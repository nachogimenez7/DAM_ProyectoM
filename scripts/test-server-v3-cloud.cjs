"use strict";
// Explicit, bounded Cloud QA. Admin prepares/removes fixtures; gameplay uses authenticated clients.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const {randomUUID, randomInt} = require("node:crypto");
const {initializeApp, deleteApp} = require("firebase/app");
const {initializeAppCheck, CustomProvider} = require("firebase/app-check");
const {getAuth, createUserWithEmailAndPassword, signOut} = require("firebase/auth");
const {getDatabase, ref, get, goOffline} = require("firebase/database");
const {getFirestore, doc, getDoc: clientDoc, updateDoc, terminate} = require("firebase/firestore");
const {cloudAccess} = require("./v3-cloud-access.cjs");
const {historyId} = require("../functions/src/accountHistoryService");
const {requestLimitId} = require("../functions/src/onlineRequestLimiter");
const {taskId} = require("../functions/src/onlineGameService");
const sleep = ms => new Promise(resolve=>setTimeout(resolve,ms));
async function waitFor(work, predicate, timeoutMs=60000) {
  const end=Date.now()+timeoutMs;
  while (Date.now()<end) { const result=await work(); if(predicate(result)) return result; await sleep(1500); }
  throw Error("Cloud QA condition timed out");
}
async function main() {
  assert.equal(process.env.TRAIDORES_REAL_FIREBASE_CONFIRM,"traidores","Explicit production QA opt-in required");
  const fullMatch=process.argv.includes("--full-match");
  const countArgument=process.argv.indexOf("--players");
  const playerCount=countArgument < 0 ? 5 : Number(process.argv[countArgument+1]);
  assert.ok([5,10,15].includes(playerCount),"Cloud QA supports only 5, 10 or 15 players");
  assert.ok(fullMatch || playerCount === 5,"Larger rooms require the bounded full-match measurement");
  const cloud=await cloudAccess(), {api,config,setDoc,getDoc,documentBase}=cloud;
  const originalGate=await getDoc("onlineMaintenance/serverAuthority");
  assert.equal(originalGate?.data.enabled,false,"Run only while general V3 rollout is closed");
  const room=`qa-v3-cloud-${Date.now()}`, users=[], apps=[], tasks=new Set();
  const appResource=`projects/${config.projectNumber}/apps/${config.appId}`;
  let debugResource, matchId, gateOpened=false;
  const evidence={room,players:playerCount,fullMatch,startedAt:new Date().toISOString(),checks:[]};
  const abort=new AbortController();
  const stop=()=>abort.abort(new Error("Cloud QA interrupted; restoring its gate and fixtures"));
  process.once("SIGINT",stop); process.once("SIGTERM",stop);
  fs.mkdirSync("output/v3-cloud",{recursive:true});
  const check=(message)=>{evidence.checks.push(message);console.log(message);};
  async function deleteTree(documentPath) {
    assert.ok(documentPath===`partidas/${room}` || documentPath.startsWith(`partidas/${room}/`) ||
      users.some(u=>documentPath===`cuentas/${u.uid}` || documentPath.startsWith(`cuentas/${u.uid}/`)),"Cleanup restricted to owned fixtures");
    const collections=await api(documentBase+documentPath+":listCollectionIds",{method:"POST",body:"{}"});
    for(const id of collections.collectionIds || []) {
      let pageToken;
      do {
        const page=await api(documentBase+documentPath+"/"+id+"?pageSize=100"+(pageToken?"&pageToken="+encodeURIComponent(pageToken):""));
        for(const d of page.documents || []) await deleteTree(d.name.split("/documents/")[1]);
        pageToken=page.nextPageToken;
      } while(pageToken);
    }
    await api(documentBase+documentPath,{method:"DELETE"});
  }
  try {
    const debugToken=randomUUID();
    debugResource=(await api(`https://firebaseappcheck.googleapis.com/v1/${appResource}/debugTokens`,{
      method:"POST",body:JSON.stringify({displayName:room,token:debugToken})})).name;
    // Token exchange is a client request identified by API key, not CLI OAuth.
    const exchange=await fetch(`https://firebaseappcheck.googleapis.com/v1/${appResource}:exchangeDebugToken?key=${config.apiKey}`,{
      method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({debugToken}),signal:AbortSignal.timeout(60000)});
    const appCheck=await exchange.json();
    if(!exchange.ok) throw Error(`App Check exchange ${exchange.status}: ${appCheck.error?.message || "failed"}`);
    const password=randomUUID()+"A1!", numberBase=997000000000+randomInt(10000000)*10;
    for(let i=0;i<playerCount;i++) {
      const app=initializeApp(config,`${room}-${i}`);apps.push(app);
      initializeAppCheck(app,{provider:new CustomProvider({getToken:async()=>({token:appCheck.token,expireTimeMillis:Date.now()+300000})}),isTokenAutoRefreshEnabled:false});
      const user=(await createUserWithEmailAndPassword(getAuth(app),`${room}-${i}@example.test`,password)).user;
      users.push({uid:user.uid,user,app,name:`QA ${i+1}`});
      await setDoc(`perfiles_publicos/${user.uid}`,{nombrePerfil:`QA ${i+1}`,publicId:String(numberBase+i)});
    }
    // Non-secret IDs enable manual recovery if this process is interrupted.
    fs.writeFileSync("output/v3-cloud/qa-manifest.json",JSON.stringify({room,uids:users.map(u=>u.uid),debugResource},null,2));
    const host=users[0];
    await setDoc(`partidas/${room}`,{estado:"esperando",nombre:"QA V3 Cloud",visibilidad:"privada",hostNombre:host.name,
      hostId:host.uid,hostActivoId:host.uid,hostVersion:0,jugadoresEsperados:playerCount,jugadoresActuales:playerCount,maxJugadores:playerCount,
      modoPrueba:false,partidaInicialCreada:false,limpiezaPendiente:false,mapa:"pampa",mapaNombre:"Pampa",codigoSala:"QA2345",
      origen:"codex-qa-v3-cloud",protocolVersion:3,configLobby:{presetRoles:"RECOMMENDED",transicionSeg:fullMatch ? 1 : 3,nocheSeg:fullMatch ? 20 : 30,discusionSeg:30,votacionSeg:25}});
    for(const [i,u] of users.entries()) await setDoc(`partidas/${room}/jugadores/${u.uid}`,{
      nombre:u.name,publicId:String(numberBase+i),listo:true,activoEnPartida:true,esHost:i===0,orden:i,protocolVersion:3,
      puedeArbitrar:false,estado:"conectado",uidTemporal:u.uid});
    async function callable(u,name,data) {
      const token=await u.user.getIdToken();
      const response=await fetch(`https://southamerica-west1-traidores.cloudfunctions.net/${name}`,{
        method:"POST",headers:{"Content-Type":"application/json",Authorization:`Bearer ${token}`,"X-Firebase-AppCheck":appCheck.token},
        body:JSON.stringify({data}),signal:AbortSignal.timeout(60000)});
      const result=await response.json();
      if(!response.ok || result.error) { const e=Error(`Callable ${name}: ${result.error?.details?.reason || result.error?.status || response.status}`);e.reason=result.error?.details?.reason;throw e; }
      return result.result;
    }
    await assert.rejects(callable(host,"iniciarPartidaV3",{roomId:room}),e=>e.reason==="server-mode-unavailable");
    check("Closed rollout rejects real Cloud start");
    abort.signal.throwIfAborted();
    gateOpened=true; // Restore even if the PATCH commits but its response is lost.
    await setDoc("onlineMaintenance/serverAuthority",{enabled:true,allowedHostUids:[host.uid],allowedRoomIds:[room]});
    const start=await callable(host,"iniciarPartidaV3",{roomId:room});assert.equal(start.status,"started");matchId=start.matchId;
    check("Authenticated Cloud callable starts allowlisted room");
    await waitFor(async()=> (await api(config.databaseURL.replace(/\/$/,"")+`/onlineV3/${room}/snapshot/permissions/${host.uid}/member.json`)),x=>x===true);
    const publicState=async u=>(await get(ref(getDatabase(u.app),`onlineV3/${room}/snapshot/public`))).val();
    const initial=await publicState(host);assert.equal(initial.matchId,matchId);assert.equal(initial.fase,"REPARTO");
    const rememberTask=p=>tasks.add(taskId(room,1,{matchId:p.matchId,phaseIndex:p.phaseIndex,deadlineMs:p.limiteFaseEpochMs}));
    rememberTask(initial);
    if(fullMatch) {
      const {measureCloudMatch}=require("./server-v3-cloud-measurement.cjs");
      evidence.measurement=await measureCloudMatch({users,room,matchId,callable,rememberTask,signal:abort.signal});
      assert.ok(["Pueblo","Traidores"].includes(evidence.measurement.winner));
      const recordId=historyId(`online:${matchId}`);
      await waitFor(()=>getDoc(`cuentas/${host.uid}/historial/${recordId}`),x=>Boolean(x?.data.contabilizada));
      await Promise.all(users.map(async u=>assert.ok((await clientDoc(doc(getFirestore(u.app),`cuentas/${u.uid}/historial/${recordId}`))).exists())));
      check("Full Cloud match finished with all clients authenticated; account histories readable");
      evidence.success=true; return;
    }
    const own=(await get(ref(getDatabase(host.app),`onlineV3/${room}/snapshot/private/${host.uid}`))).val();assert.equal(own.matchId,matchId);
    await assert.rejects(get(ref(getDatabase(host.app),`onlineV3/${room}/snapshot/private/${users[1].uid}`)));
    check("RTDB own projection allowed; another player's secret denied");
    const ack={roomId:room,matchId,phaseIndex:initial.phaseIndex,requestId:randomUUID(),action:"role_ack"};
    const receipt=await callable(host,"accionPartidaV3",ack);
    assert.deepEqual(await callable(host,"accionPartidaV3",ack),receipt);
    assert.equal((await publicState(host)).revision,initial.revision);
    check("Real action callable accepts authenticated intent and deduplicates its retry");
    // No recovery calls and no gameplay actions: the next phase must come from Cloud Tasks.
    goOffline(getDatabase(host.app));
    const night=await waitFor(()=>publicState(users[1]),x=>x?.matchId===matchId && x.phaseIndex>initial.phaseIndex,75000);
    assert.equal(night.fase,"NOCHE");
    rememberTask(night);
    const deadlineLog=await waitFor(async()=>api("https://logging.googleapis.com/v2/entries:list",{
      method:"POST",body:JSON.stringify({resourceNames:["projects/traidores"],
        filter:`resource.type="cloud_run_revision" AND resource.labels.service_name="resolverfasev3" AND jsonPayload.roomId="${room}" AND jsonPayload.operation="deadline"`,
        orderBy:"timestamp desc",pageSize:5})}),x=>(x.entries || []).some(e=>e.jsonPayload?.changed===true),30000);
    evidence.deadline=deadlineLog.entries.find(e=>e.jsonPayload?.changed===true).jsonPayload;
    check("Cloud deadline advances with host offline and no recovery requests");
    for(const u of users.slice(0,4)) await callable(u,"abandonarPartidaV3",{roomId:room,matchId});
    const finished=await waitFor(()=>publicState(users[4]),x=>Boolean(x?.ganador));
    assert.equal(finished.fase,"FINALIZADA");check("Real leave callables finish match without host authority");
    assert.equal(finished.jugadores.find(p=>p.uidTemporal===host.uid).causaEliminacion,"ABANDONO");
    const recordId=historyId(`online:${matchId}`);
    await waitFor(()=>getDoc(`cuentas/${host.uid}/historial/${recordId}`),x=>Boolean(x?.data.contabilizada));
    assert.equal((await getDoc(`cuentas/${host.uid}/historial/${recordId}`)).data.won,false);
    const history=await clientDoc(doc(getFirestore(users[4].app),`cuentas/${users[4].uid}/historial/${recordId}`));assert.ok(history.exists());
    await assert.rejects(clientDoc(doc(getFirestore(users[4].app),`cuentas/${host.uid}/historial/${recordId}`)));
    check("Cloud history persisted; abandonment counts as loss; account privacy enforced");
    const rematch=await callable(host,"prepararRevanchaV3",{roomId:room,matchId});assert.equal(rematch.status,"prepared");assert.notEqual(rematch.matchId,matchId);
    await waitFor(()=>publicState(users[4]),x=>x?.fase==="LOBBY" && x.matchId===rematch.matchId);
    await updateDoc(doc(getFirestore(users[4].app),`partidas/${room}/jugadores/${users[4].uid}`),{listo:true});
    check("Cloud rematch has new matchId, clears old projections and permits lobby ready");
    evidence.success=true;
  } finally {
    // Close starts first, then remove ONLY this script's room/accounts/debug token.
    const errors=[];
    const clean=async work=>{try{await work();}catch(e){errors.push(e.message);}};
    if(gateOpened) await clean(()=>setDoc("onlineMaintenance/serverAuthority",originalGate.data));
    for(const id of tasks) await clean(async()=>{
      try{await api(`https://cloudtasks.googleapis.com/v2/projects/traidores/locations/southamerica-east1/queues/resolverFaseV3/tasks/${id}`,{method:"DELETE"});}
      catch(e){if(e.status!==404)throw e;}
    });
    let roomExisted=null;
    await clean(async()=>{roomExisted=await getDoc(`partidas/${room}`);});
    await clean(()=>deleteTree(`partidas/${room}`));
    if(roomExisted) await clean(async()=>{
      // The deletion trigger creates maintenance markers outside the room tree.
      // Await this owned marker before removing it; never scan unrelated rooms.
      await waitFor(()=>getDoc(`onlineRoomCleanupOrphans/${room}`),Boolean,30000);
      await sleep(1500);
      for(const collection of ["onlineRoomCleanupQueue","onlineRoomCleanupOrphans"])
        await api(documentBase+`${collection}/${room}`,{method:"DELETE"}).catch(e=>{if(e.status!==404)throw e;});
    });
    await clean(()=>api(config.databaseURL.replace(/\/$/,"")+`/onlineV3/${room}.json`,{method:"DELETE"}));
    for(const u of users) {
      await clean(()=>deleteTree(`cuentas/${u.uid}`));
      await clean(()=>api(documentBase+`perfiles_publicos/${u.uid}`,{method:"DELETE"}));
      await clean(()=>api(documentBase+`onlineRequestLimits/${requestLimitId(u.uid)}`,{method:"DELETE"}));
      // QA fixture ownership is established above. Admin deletion remains valid
      // after a long match, when client deletion requires a fresh sign-in.
      await clean(()=>api("https://identitytoolkit.googleapis.com/v1/projects/traidores/accounts:delete",{
        method:"POST",body:JSON.stringify({localId:u.uid})}));
      await clean(()=>signOut(getAuth(u.app)));
    }
    if(debugResource) await clean(()=>api(`https://firebaseappcheck.googleapis.com/v1/${debugResource}`,{method:"DELETE"}));
    for(const app of apps){goOffline(getDatabase(app));await clean(()=>terminate(getFirestore(app)));await clean(()=>deleteApp(app));}
    evidence.cleanupErrors=errors;evidence.finishedAt=new Date().toISOString();
    const evidenceName=fullMatch ? `qa-evidence-${playerCount}-full.json` : "qa-evidence.json";
    fs.writeFileSync(`output/v3-cloud/${evidenceName}`,JSON.stringify(evidence,null,2));
    process.removeListener("SIGINT",stop); process.removeListener("SIGTERM",stop);
    if(errors.length) console.error("Cleanup requires review: "+errors.join("; "));else console.log("Cloud QA fixtures removed; rollout remains CLOSED");
    if(errors.length) throw Error("Cloud QA cleanup incomplete; review its evidence before another run");
  }
}
// SDK token/backoff timers can outlive deleteApp. Exit only after all explicit
// cleanup and the synchronous evidence write have finished, then flush CLI logs.
const finish=code=>process.stdout.write("",()=>process.stderr.write("",()=>process.exit(code)));
if(require.main===module) main().then(()=>finish(0),e=>{console.error(e.message);finish(1)});
