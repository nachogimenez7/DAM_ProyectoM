"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {accountHash, entitlementsFor, evaluatePurchase, verifyAndGrant, revokeVoided, tokenId,
  PurchaseError} = require("../src/purchaseService");

// Minimal Firestore stand-in: documents, equality queries and read-before-write transactions.
function fakeFirestore() {
  const docs = new Map();
  const snap = (path) => ({id: path.split("/").pop(), exists: docs.has(path),
    get: (field) => docs.get(path)?.[field], data: () => docs.get(path)});
  const doc = (path) => ({path, id: path.split("/").pop(), kind: "doc"});
  const collection = (name) => ({
    doc: (id) => doc(`${name}/${id}`),
    where(field, _op, value, filters = []) {
      const q = {kind: "query", name, filters: [...filters, [field, value]],
        where: (f, o, v) => collection(name).where(f, o, v, q.filters)};
      return q;
    },
  });
  async function read(ref) {
    if (ref.kind === "doc") return snap(ref.path);
    const matches = [...docs.keys()].filter((p) => p.startsWith(`${ref.name}/`) &&
      ref.filters.every(([f, v]) => docs.get(p)[f] === v));
    return {docs: matches.map(snap)};
  }
  return {
    docs,
    collection,
    async runTransaction(fn) {
      const writes = [];
      const tx = {
        get: async (ref) => { assert.equal(writes.length, 0, "read after write in transaction"); return read(ref); },
        set: (ref, data) => writes.push(() => docs.set(ref.path, {...data})),
        update: (ref, data) => writes.push(() => docs.set(ref.path, {...docs.get(ref.path), ...data})),
      };
      const result = await fn(tx);
      writes.forEach((w) => w());
      return result;
    },
  };
}

const FieldValue = {serverTimestamp: () => "ts"};
const TOKEN = "token-de-prueba-123456";

function play(purchase, voided = []) {
  const calls = {ack: 0};
  return {calls, getProduct: async () => purchase, acknowledge: async () => { calls.ack += 1; },
    listVoided: async () => voided};
}

test("the pack grants no ads and every pack cosmetic", () => {
  const items = entitlementsFor(["pack_sello_pueblo"]);
  assert.ok(items.includes("sin_anuncios"));
  assert.ok(items.includes("festejo_sello") && items.includes("emote_brindis"));
  assert.deepEqual(entitlementsFor(["estilos_tres", "estilo_espacial"]),
    ["estilo_abismo_real", "estilo_espacial", "estilo_forja_infernal"]);
});

test("pending, cancelled and other-account purchases are refused", () => {
  assert.throws(() => evaluatePurchase({purchaseState: 2}, "u1"), {code: "pending"});
  assert.throws(() => evaluatePurchase({purchaseState: 1}, "u1"), {code: "not-purchased"});
  assert.throws(() => evaluatePurchase({purchaseState: 0, obfuscatedExternalAccountId: accountHash("u2")}, "u1"),
    {code: "other-account"});
  assert.deepEqual(evaluatePurchase({purchaseState: 0, orderId: "GPA.1", acknowledgementState: 0,
    obfuscatedExternalAccountId: accountHash("u1")}, "u1"), {orderId: "GPA.1", needsAck: true});
});

test("a purchase is granted once, acknowledged, and restoring it is idempotent", async () => {
  const firestore = fakeFirestore();
  const api = play({purchaseState: 0, orderId: "GPA.1", acknowledgementState: 0,
    obfuscatedExternalAccountId: accountHash("u1")});
  const args = {firestore, playApi: api, uid: "u1", productId: "sin_anuncios", purchaseToken: TOKEN,
    nowMs: 1, FieldValue};
  assert.deepEqual((await verifyAndGrant(args)).items, ["sin_anuncios"]);
  assert.deepEqual((await verifyAndGrant(args)).items, ["sin_anuncios"]);
  assert.equal(firestore.docs.get(`compras/${tokenId(TOKEN)}`).uid, "u1");
  assert.deepEqual(firestore.docs.get("derechos/u1").items, ["sin_anuncios"]);
  assert.equal(api.calls.ack, 2); // Google still reports it unacknowledged in this fake.
});

test("a token already bound to another account cannot be reused", async () => {
  const firestore = fakeFirestore();
  firestore.docs.set(`compras/${tokenId(TOKEN)}`, {uid: "u2", productId: "sin_anuncios", estado: "activa"});
  await assert.rejects(verifyAndGrant({firestore, playApi: play({purchaseState: 0}), uid: "u1",
    productId: "sin_anuncios", purchaseToken: TOKEN, nowMs: 1, FieldValue}), {code: "other-account"});
});

test("unknown products and missing accounts are rejected before calling Google", async () => {
  const api = {getProduct: async () => assert.fail("must not call Google")};
  await assert.rejects(verifyAndGrant({firestore: fakeFirestore(), playApi: api, uid: "u1",
    productId: "monedas", purchaseToken: TOKEN, FieldValue}), PurchaseError);
  await assert.rejects(verifyAndGrant({firestore: fakeFirestore(), playApi: api, uid: "",
    productId: "sin_anuncios", purchaseToken: TOKEN, FieldValue}), {code: "unauthenticated"});
});

test("a refund removes only what that purchase granted", async () => {
  const firestore = fakeFirestore();
  firestore.docs.set(`compras/${tokenId("pack-token-1234")}`, {uid: "u1", productId: "pack_sello_pueblo", estado: "activa"});
  firestore.docs.set(`compras/${tokenId("style-token-1234")}`, {uid: "u1", productId: "estilo_espacial", estado: "activa"});
  const result = await revokeVoided({firestore, playApi: play(null, [{purchaseToken: "pack-token-1234"}]),
    sinceMs: 0, FieldValue});
  assert.deepEqual(result, {checked: 1, revoked: 1});
  assert.equal(firestore.docs.get(`compras/${tokenId("pack-token-1234")}`).estado, "anulada");
  assert.deepEqual(firestore.docs.get("derechos/u1").items, ["estilo_espacial"]);
});
