"use strict";

// Local integration measurement. No production credentials or paid services are used.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const {createRequire} = require("node:module");
const {AsyncLocalStorage} = require("node:async_hooks");
const sdk = createRequire(require.resolve("../functions/package.json"));
const {initializeApp, deleteApp} = sdk("firebase-admin/app");
const {getFirestore} = sdk("firebase-admin/firestore");
const {getDatabase} = sdk("firebase-admin/database");
const service = require("../functions/src/onlineGameService");
const {deadlineToken} = require("../functions/src/onlineGameCore");
const {enforceRequestLimit, requestLimitId} = require("../functions/src/onlineRequestLimiter");
const {archiveFinishedRoom} = require("../functions/src/accountHistoryService");
const {enqueueRoomCleanup} = require("../functions/src/onlineRoomCleanupService");

function meterFirestore(raw) {
  const unwrap = new WeakMap();
  const cache = new WeakMap();
  const rows = {};
  const scope = new AsyncLocalStorage();
  const current = () => { const row = scope.getStore(); assert.ok(row, "Unmeasured SDK operation"); return row; };
  const read = (snapshot) => { current().reads += Array.isArray(snapshot.docs) ? Math.max(1, snapshot.docs.length) : 1; };
  const commit = (writes) => {
    for (const [kind, ref] of writes) {
      current()[kind === "delete" ? "deletes" : "writes"]++;
      if (/^partidas\/[^/]+$/.test(ref.path)) current().roomWrites++;
    }
  };
  function wrap(ref) {
    if (cache.has(ref)) return cache.get(ref);
    const proxy = new Proxy(ref, {get(target, key) {
      if (key === "get") return async (...args) => { const value = await target.get(...args); read(value); return value; };
      if (["set", "update", "create", "delete"].includes(key)) return async (...args) => {
        const value = await target[key](...args); commit([[key, target]]); return value;
      };
      if (["doc", "collection", "where", "orderBy", "limit", "startAfter"].includes(key)) {
        return (...args) => wrap(target[key](...args));
      }
      const value = Reflect.get(target, key, target);
      return typeof value === "function" ? value.bind(target) : value;
    }});
    unwrap.set(proxy, ref); cache.set(ref, proxy); return proxy;
  }
  const firestore = {
    doc: (...args) => wrap(raw.doc(...args)), collection: (...args) => wrap(raw.collection(...args)),
    collectionGroup: (...args) => wrap(raw.collectionGroup(...args)),
    async runTransaction(work) {
      let committedWrites;
      const result = await raw.runTransaction(async (tx) => {
        current().transactionAttempts++;
        const writes = [];
        const measured = {async get(ref) { const value = await tx.get(unwrap.get(ref) || ref); read(value); return value; }};
        for (const kind of ["set", "update", "create", "delete"]) measured[kind] = (ref, ...args) => {
          const original = unwrap.get(ref) || ref;
          tx[kind](original, ...args); writes.push([kind, original]); return measured;
        };
        const value = await work(measured); committedWrites = writes; return value;
      });
      commit(committedWrites); return result;
    },
  };
  async function measure(label, work) {
    assert.equal(scope.getStore(), undefined, "Nested measurement would count operations twice");
    const row = rows[label] ||= {calls: 0, reads: 0, writes: 0, deletes: 0, roomWrites: 0,
      transactionAttempts: 0, errors: 0, durationsMs: []};
    row.calls++;
    const startedAt = performance.now();
    return scope.run(row, async () => {
      try { return await work(); } catch (error) { row.errors++; throw error; }
      finally { row.durationsMs.push(Math.round((performance.now() - startedAt) * 100) / 100); }
    });
  }
  return {firestore, rows, measure};
}

async function scenario(raw, database, count, duplicateActions, options = {}) {
  const mapKey = options.mapKey || "pampa", long = options.long === true, concurrent = options.concurrent === true;
  const mode = long ? "long" : concurrent ? "concurrent" : duplicateActions ? "retry" : "base";
  const roomId = `cost-v3-${count}-${mapKey}-${mode}`;
  const uids = Array.from({length: count}, (_, i) => `${roomId}-p${i}`);
  const roomRef = raw.doc(`partidas/${roomId}`);
  const measured = meterFirestore(raw), {firestore, measure, rows} = measured;
  let clock = 1000000, requests = 0, tasks = 0, publications = 0, stateJsonPeakBytes = 0;
  const phaseCounts = {};
  let previous = null, logicalDeliveries = 0, logicalBytes = 0;
  let observers;
  const taskIds = new Set();
  const enqueueDeadline = async ({id}) => { if (!taskIds.has(id)) { taskIds.add(id); tasks++; } };
  const state = async () => {
    const value = (await roomRef.collection("servidor").doc("current").get()).data();
    stateJsonPeakBytes = Math.max(stateJsonPeakBytes, Buffer.byteLength(JSON.stringify(value)));
    return value;
  };
  const publish = async () => {
    const publicDeliveriesBefore = observers?.stats().public.deliveries;
    const result = await measure("publish", () => service.publishServerOutbox({firestore, database, roomId, enqueueDeadline}));
    if (!result.published) return;
    publications++;
    // Logical complete value payloads, not RTDB wire bytes or billable downloads.
    const snapshot = (await database.ref(`onlineV3/${roomId}/snapshot`).get()).val();
    for (const uid of uids) for (const [value, old] of [
      [snapshot.public, previous?.public],
      [snapshot.private?.[uid], previous?.private?.[uid]],
      [snapshot.permissions?.[uid], previous?.permissions?.[uid]],
    ]) {
      if (JSON.stringify(value) !== JSON.stringify(old)) {
        logicalDeliveries++; logicalBytes += Buffer.byteLength(JSON.stringify(value ?? null));
      }
    }
    previous = snapshot;
    if (observers) {
      await observers.sync(snapshot);
      if (!result.changedPublic) assert.equal(observers.stats().public.deliveries, publicDeliveriesBefore,
        "A secret-only publication must not deliver a public value event");
    }
  };
  const cleanupTrigger = async (before) => {
    const roomWrites = Object.values(rows).reduce((sum, r) => sum + r.roomWrites, 0);
    for (let i = before; i < roomWrites; i++) await measure("cleanupEnqueue", () => enqueueRoomCleanup({firestore, roomId, nowMs: clock}));
  };
  const totalRoomWrites = () => Object.values(rows).reduce((sum, r) => sum + r.roomWrites, 0);
  const action = async (current, uid, name, extra = {}, deferPublish = false) => {
    clock = current.phaseStartedAtMs + 1;
    const input = {matchId: current.matchId, phaseIndex: current.phaseIndex,
      requestId: `cost_request_${++requests}`, action: name, ...extra};
    const before = totalRoomWrites();
    const result = await measure("action", async () => {
      await enforceRequestLimit({firestore, uid, nowMs: clock});
      return service.submitServerAction({firestore, roomId, requesterId: uid, action: input, nowMs: clock});
    });
    assert.equal(result.changed, true);
    if (!deferPublish) { await publish(); await cleanupTrigger(before); }
    if (duplicateActions) {
      const duplicate = await measure("duplicateAction", async () => {
        await enforceRequestLimit({firestore, uid, nowMs: clock});
        return service.submitServerAction({firestore, roomId, requesterId: uid, action: input, nowMs: clock});
      });
      assert.equal(duplicate.changed, false);
    }
  };
  const dispatch = async (current, plans) => {
    if (!concurrent) {
      for (const [uid, name, extra] of plans) await action(current, uid, name, extra);
    } else {
      const before = totalRoomWrites();
      // All intents commit through the real Firestore transaction; delivery is
      // coalesced after this burst, then duplicated events read delivered state.
      await Promise.all(plans.map(([uid, name, extra]) => action(current, uid, name, extra, true)));
      await publish(); await cleanupTrigger(before);
      for (let i = 1; i < plans.length; i++) await measure("coalescedOutboxEvent", () =>
        service.publishServerOutbox({firestore, database, roomId, enqueueDeadline}));
    }
  };
  try {
    await raw.recursiveDelete(roomRef);
    await database.ref(`onlineV3/${roomId}`).remove();
    const batch = raw.batch();
    batch.set(raw.doc(service.AUTHORITY_GATE), {enabled: true});
    batch.set(roomRef, {estado: "esperando", hostId: uids[0], hostActivoId: uids[0], jugadoresEsperados: count,
      mapa: mapKey, codigoSala: "ABC234", configLobby: {presetRoles: "RECOMMENDED"}});
    for (const [i, uid] of uids.entries()) {
      batch.set(roomRef.collection("jugadores").doc(uid), {nombre: `J${i}`, listo: true,
        activoEnPartida: true, orden: i, publicId: i + 1, protocolVersion: 3});
      batch.set(raw.doc(`perfiles_publicos/${uid}`), {nombre: `J${i}`, publicId: i + 1});
      batch.delete(raw.doc(`onlineRequestLimits/${requestLimitId(uid)}`));
    }
    await batch.commit();
    await measure("start", async () => {
      await enforceRequestLimit({firestore, uid: uids[0], nowMs: clock});
      return service.startServerMatch({firestore, roomId, requesterId: uids[0], nowMs: clock,
        matchId: `match-${roomId}`, chooseRandomInt: () => 0});
    });
    await publish(); await cleanupTrigger(0);
    if (options.createObservers) {
      observers = options.createObservers(uids, roomId);
      await observers.sync(previous);
    }
    for (let step = 0; step < 100; step++) {
      let current = await state();
      if (current.winner) break;
      const phase = current.phase;
      phaseCounts[phase] = (phaseCounts[phase] || 0) + 1;
      const alive = current.players.filter((p) => p.alive);
      if (phase === "REPARTO") {
        const deserter = current.players.find((p) => p.role.key === "desertor");
        if (deserter) await action(current, deserter.uid, "desertor_initial", {team: "Pueblo"});
        await dispatch(current, current.players.map((p) => [p.uid, "role_ack", {}]));
      } else if (phase === "NOCHE") {
        const plans = [];
        for (const p of alive) {
          let name, target;
          if (["asesino", "espia"].includes(p.role.key)) {
            name = "matar"; target = long && current.round <= 3 ? alive.find((t) => t.role.key === "medico") : alive.find((t) => t.uid !== p.uid &&
              !["asesino", "mercenario", "espia"].includes(t.role.key) &&
              !(t.role.key === "desertor" && current.deserterTeam === "Traidores"));
          } else if (p.role.key === "mercenario") {
            name = "silenciar"; target = alive.find((t) => t.uid !== p.uid && (!long || !["medico", "alcalde"].includes(t.role.key)) &&
              (t.lastSilencedRound === null || current.round - t.lastSilencedRound >= 2));
          } else if (p.role.key === "policia") { name = "investigar"; target = alive.find((t) => t.uid !== p.uid); }
          else if (p.role.key === "medico") { name = "salvar"; target = p; }
          else if (p.role.key === "oraculo" && current.round > 1 && !current.oracleUsed) {
            name = "invitar_muerto"; target = current.players.find((t) => !t.alive && !t.left);
          }
          if (name && target) plans.push([p.uid, name, {targetUid: target.uid}]);
        }
        await dispatch(current, plans);
      } else if (["VOTACION", "DESEMPATE_VOTACION"].includes(phase)) {
        const voters = alive.filter((p) => !p.muted), plans = [];
        // With two tie candidates and an odd electorate, one player abstains in
        // the second ballot. Everyone votes in the first, so this never reaches
        // the two-consecutive-misses AFK threshold.
        if (long && current.round <= 3 && phase === "DESEMPATE_VOTACION" && voters.length % 2) voters.pop();
        const tiedPlan = long && current.round <= 3 ? balancedTieVotes(current, voters) : null;
        for (const p of voters) {
          const candidates = alive.filter((t) => t.uid !== p.uid &&
            (phase !== "DESEMPATE_VOTACION" || current.tieCandidates.includes(t.uid)));
          const target = tiedPlan ? alive.find((t) => t.uid === tiedPlan[p.uid]) :
            candidates.find((t) => !["asesino", "mercenario", "espia"].includes(t.role.key)) || candidates[0];
          if (target) plans.push([p.uid, "votar", {targetUid: target.uid}]);
        }
        await dispatch(current, plans);
      } else if (phase === "DIA_DEBATE") {
        const mayor = alive.find((p) => p.role.key === "alcalde" && !p.muted);
        if (mayor && !current.mayorUid && (!long || current.round >= 4)) await action(current, mayor.uid, "revelar_alcalde");
        const deserter = alive.find((p) => p.role.key === "desertor");
        if (deserter && current.round >= 4 && !current.deserterUsed && !long) await action(current, deserter.uid, "desertor_rethink", {team: "mantener"});
        const payador = alive.find((p) => p.role.key === "payador" && !p.muted);
        if (long && current.round >= 4 && payador && !current.payadorUsed) {
          const targets = alive.filter((p) => p.uid !== payador.uid).slice(0, 2);
          for (const target of targets) await action(current, payador.uid, "contrapunto", {targetUid: target.uid});
        }
      } else if (phase === "CONTRAPUNTO") {
        await action(current, alive.find((p) => p.role.key === "payador").uid, "senalar_contrapunto", {targetUid: current.counterpointPlayers[0]});
      } else if (phase === "DESERTOR_RECONSIDERACION") {
        await action(current, alive.find((p) => p.role.key === "desertor").uid, "desertor_rethink", {team: "mantener"});
      } else if (phase === "ALCALDE_DESEMPATE") {
        const mayor = alive.find((p) => p.uid === current.mayorUid && !p.muted);
        if (mayor && (!long || current.round >= 4)) await action(current, mayor.uid, "decidir_empate", {targetUid: current.tieCandidates[0]});
      }
      current = await state();
      if (current.winner) break;
      clock = current.deadlineMs + service.DEADLINE_MARGIN_MS;
      const before = totalRoomWrites();
      await measure("deadline", () => service.expireServerPhase({firestore, roomId, token: deadlineToken(current), nowMs: clock}));
      await publish();
      // Worker publishes inline; subsequently delivered outbox event reads already-delivered data.
      await measure("deliveredOutboxEvent", () => service.publishServerOutbox({firestore, database, roomId, enqueueDeadline}));
      await cleanupTrigger(before);
    }
    const final = await state();
    assert.ok(["Pueblo", "Traidores"].includes(final.winner), "Expected a completed match, not an AFK cancellation");
    assert.equal(final.players.filter((p) => p.deathCause === "AFK").length, 0, "Baseline must not rely on AFK eliminations");
    const room = (await roomRef.get()).data();
    await measure("history", () => archiveFinishedRoom({firestore, roomId, room, finishedAtMs: clock}));
    for (const uid of uids) assert.equal((await raw.collection(`cuentas/${uid}/historial`).get()).size, 1);
    assert.equal(rows.history.reads, count * 3); assert.equal(rows.history.writes, count * 3);
    if (!concurrent) assert.equal(rows.action.reads, rows.action.calls * 3);
    assert.ok(rows.action.writes >= rows.action.calls * 3 && rows.action.writes <= rows.action.calls * 3 + 1);
    if (long) { assert.ok(final.round >= 4); assert.ok(phaseCounts.DESEMPATE_VOTACION >= 3);
      if (count >= 8) assert.ok(phaseCounts.ALCALDE_DESEMPATE >= 3);
      if (count >= 14) assert.equal(phaseCounts.DESERTOR_RECONSIDERACION, 1); }
    if (duplicateActions) { assert.equal(rows.duplicateAction.reads, rows.duplicateAction.calls * 3);
      assert.equal(rows.duplicateAction.writes, rows.duplicateAction.calls); }
    const totals = Object.values(rows).reduce((t, r) => ({reads: t.reads + r.reads, writes: t.writes + r.writes,
      deletes: t.deletes + r.deletes}), {reads: 0, writes: 0, deletes: 0});
    return {players: count, mapKey, mode, duplicateActions, concurrent, winner: final.winner,
      rounds: final.round, phaseCounts, tasks, publications, stateJsonPeakBytes,
      logicalDeliveries, logicalBytes, totals, operations: rows,
      clientValueCallbacks: observers?.stats()};
  } finally {
    observers?.close();
    await raw.recursiveDelete(roomRef);
    await database.ref(`onlineV3/${roomId}`).remove();
    await raw.doc(`onlineRoomCleanupQueue/${roomId}`).delete();
    for (const uid of uids) {
      await raw.recursiveDelete(raw.doc(`cuentas/${uid}`));
      await raw.doc(`perfiles_publicos/${uid}`).delete();
      await raw.doc(`onlineRequestLimits/${requestLimitId(uid)}`).delete();
    }
  }
}

// Choose actual legal votes whose top totals tie. For the long scenarios the Mayor
// stays hidden and the first three nights protect the Doctor, keeping the roster.
function balancedTieVotes(state, voters) {
  const candidates = state.players.filter((p) => p.alive && p.role.key !== "alcalde" &&
    (state.phase !== "DESEMPATE_VOTACION" || state.tieCandidates.includes(p.uid)));
  const total = voters.length;
  const targets = candidates.slice(0, total % 2 ? 3 : 2);
  const capacity = total % 2 ? [Math.floor(total / 2), Math.floor(total / 2), 1] : [total / 2, total / 2];
  assert.equal(targets.length, capacity.length, "Need enough tie targets");
  const plan = {};
  function assign(i) {
    if (i === voters.length) return true;
    for (let j = 0; j < targets.length; j++) if (capacity[j] && targets[j].uid !== voters[i].uid) {
      capacity[j]--; plan[voters[i].uid] = targets[j].uid;
      if (assign(i + 1)) return true;
      capacity[j]++;
    }
    return false;
  }
  assert.ok(assign(0), "Could not construct a legal tie"); return plan;
}

async function main() {
  assert.equal(process.env.FIRESTORE_EMULATOR_HOST, "127.0.0.1:18081", "Use isolated QA Firestore emulator");
  assert.equal(process.env.FIREBASE_DATABASE_EMULATOR_HOST, "127.0.0.1:19000", "Use isolated QA Database emulator");
  assert.equal(process.env.GCLOUD_PROJECT, "traidores-local", "Production execution is forbidden");
  const app = initializeApp({projectId: "traidores-local", databaseURL: "https://traidores-local-default-rtdb.firebaseio.com"}, "v3-cost");
  const firestore = getFirestore(app), database = getDatabase(app);
  try {
    const results = [];
    for (const retry of process.argv.includes("--concurrent-only") ? [] : [false, true]) for (const count of [5, 10, 15]) {
      const result = await scenario(firestore, database, count, retry); results.push(result);
      console.log(JSON.stringify({players: count, retries: retry, ...result.totals,
        phases: result.operations.deadline.calls, actions: result.operations.action.calls, logicalBytes: result.logicalBytes}));
    }
    for (const mapKey of process.argv.includes("--concurrent-only") ? [] : ["pampa", "grecia", "medieval"]) for (const count of [5, 10, 15]) {
      const result = await scenario(firestore, database, count, false, {mapKey, long: true}); results.push(result);
      console.log(JSON.stringify({players: count, mapKey, mode: result.mode, rounds: result.rounds, ...result.totals}));
    }
    for (const count of [5, 10, 15]) {
      const result = await scenario(firestore, database, count, false, {concurrent: true}); results.push(result);
      console.log(JSON.stringify({players: count, mode: result.mode, ...result.totals,
        actionTransactionAttempts: result.operations.action.transactionAttempts}));
    }
    const output = path.resolve(__dirname, process.argv.includes("--concurrent-only") ?
      "../output/server-v3-concurrent.json" : "../output/server-v3-consumption.json");
    fs.mkdirSync(path.dirname(output), {recursive: true});
    fs.writeFileSync(output, JSON.stringify({schemaVersion: 2, environment: "local-emulators", results}, null, 2) + "\n");
    console.log(`Report: ${output}`);
  } finally { await firestore.doc(service.AUTHORITY_GATE).delete(); await deleteApp(app); }
}
if (require.main === module) main().catch((error) => { console.error(error); process.exitCode = 1; });
module.exports = {meterFirestore, scenario};
