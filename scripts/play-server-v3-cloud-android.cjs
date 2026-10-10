'use strict';
// Bounded private practice: one isolated Android + four authenticated SDK bots.
// Admin only sets up/removes owned QA data. Gameplay remains server-authoritative.
const assert=require('node:assert/strict'),fs=require('node:fs');
const {randomUUID,randomInt}=require('node:crypto');
const {execFileSync,spawn}=require('node:child_process');
const {initializeApp,deleteApp}=require('firebase/app');
const {initializeAppCheck,CustomProvider}=require('firebase/app-check');
const {getAuth,createUserWithEmailAndPassword}=require('firebase/auth');
const {getDatabase,goOffline}=require('firebase/database');
const {getFirestore,terminate}=require('firebase/firestore');
const {cloudAccess}=require('./v3-cloud-access.cjs');
const {measureCloudMatch}=require('./server-v3-cloud-measurement.cjs');
const {taskId}=require('../functions/src/onlineGameService');
const {historyId}=require('../functions/src/accountHistoryService');
const {requestLimitId}=require('../functions/src/onlineRequestLimiter');
const pause=ms=>new Promise(r=>setTimeout(r,ms));
async function main() {
  assert.equal(process.env.TRAIDORES_REAL_FIREBASE_CONFIRM,'traidores');
  const adb=process.env.TRAIDORES_QA_ADB,device=process.env.TRAIDORES_QA_DEVICE;
  assert.ok(adb && device,'Specify adb and device');
  const pkg='com.traidores.juego.v3qa';
  const deviceCall=(args)=>{
    try{return execFileSync(adb,['-s',device,...args],{encoding:'utf8',timeout:60000});}
    catch{throw Error('ADB operation failed; check the unlocked device connection');}
  };
  assert.match(deviceCall(['shell','pm','path',pkg]),/^package:/);
  const abort=new AbortController(), stop=()=>abort.abort();
  process.once('SIGINT',stop);process.once('SIGTERM',stop);
  const room=`qa-v3-interactive-${Date.now()}`,dir=`output/v3-cloud/${room}`;
  fs.mkdirSync(dir,{recursive:true});
  const cloud=await cloudAccess(),{api,config,getDoc,setDoc,documentBase}=cloud;
  const original=await getDoc('onlineMaintenance/serverAuthority');
  assert.equal(original?.data.enabled,false,'General V3 must be closed');
  assert.equal((await getDoc('config/onlineV3'))?.data.enabled,false,'Public client rollout must be closed');
  const appResource=`projects/${config.projectNumber}/apps/${config.appId}`;
  const users=[],apps=[],tasks=new Set(),debugResources=[],cleanupErrors=[];
  const evidence={room,totalPlayers:5,bots:4,createdAt:new Date().toISOString(),
    limitations:['Bot SDK counters cover four simulated clients; Android traffic is captured separately',
      'Cloud Monitoring is project-wide, delayed and includes background work and QA setup/cleanup',
      'Debug App Check is not a validation of Release Play Integrity','Bots do not replace a human multiplayer test']};
  let gateOwned=false,roomCreated=false,cachedToken,deviceLog;
  const captures=[],captureTimers=new Set(),captureJobs=new Set(),captureCounts=new Map();
  function capture(kind,delayMs=250,limit=2) {
    const count=captureCounts.get(kind)||0;
    if(count>=limit)return;
    captureCounts.set(kind,count+1);
    const timer=setTimeout(()=>{
      captureTimers.delete(timer);
      const file=`captures/${kind}-${count+1}.png`,requestedAt=new Date().toISOString();
      fs.mkdirSync(dir+'/captures',{recursive:true});
      const job=new Promise(resolve=>{
        const process=spawn(adb,['-s',device,'exec-out','screencap','-p'],{stdio:['ignore','pipe','ignore']});
        const chunks=[];let done=false;
        const finish=ok=>{
          if(done)return;done=true;clearTimeout(timeout);
          const bytes=Buffer.concat(chunks);
          if(ok && bytes.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10]))) {
            fs.writeFileSync(dir+'/'+file,bytes);captures.push({kind,file,requestedAt,capturedAt:new Date().toISOString()});
          } else evidence.captureFailures=(evidence.captureFailures||0)+1;
          resolve();
        };
        const timeout=setTimeout(()=>{process.kill();finish(false);},10000);
        process.stdout.on('data',chunk=>chunks.push(chunk));
        process.once('error',()=>finish(false));process.once('close',code=>finish(code===0));
      });
      captureJobs.add(job);job.finally(()=>captureJobs.delete(job));
    },delayMs);
    captureTimers.add(timer);
  }
  const ownedGate={enabled:true,allowedHostUids:[],allowedRoomIds:[room]};
  async function waitFor(work,predicate,ms=60000) {
    const until=Date.now()+ms;
    while(Date.now()<until){abort.signal.throwIfAborted();const value=await work();if(predicate(value))return value;await pause(1500);}
    throw Error('Private practice timed out');
  }
  async function clean(work){try{await work();}catch(e){cleanupErrors.push(e.message);}}
  async function closeGate() {
    if(!gateOwned)return;
    const current=await getDoc('onlineMaintenance/serverAuthority');
    if(current?.data.enabled===true && current.data.allowedHostUids?.join()===ownedGate.allowedHostUids.join() &&
        current.data.allowedRoomIds?.join()===room) await setDoc('onlineMaintenance/serverAuthority',original.data);
    else assert.equal(current?.data.enabled,false,'Gate changed externally; do not overwrite it');
    gateOwned=false;
  }
  async function deleteTree(documentPath) {
    assert.ok(documentPath===`partidas/${room}` || documentPath.startsWith(`partidas/${room}/`) ||
      users.some(u=>documentPath===`cuentas/${u.uid}` || documentPath.startsWith(`cuentas/${u.uid}/`)));
    const collections=await api(documentBase+documentPath+':listCollectionIds',{method:'POST',body:'{}'});
    for(const id of collections.collectionIds||[]) {
      let pageToken;
      do {
        const page=await api(documentBase+documentPath+'/'+id+'?pageSize=100'+(pageToken?'&pageToken='+encodeURIComponent(pageToken):''));
        for(const d of page.documents||[])await deleteTree(d.name.split('/documents/')[1]);
        pageToken=page.nextPageToken;
      }while(pageToken);
    }
    await api(documentBase+documentPath,{method:'DELETE'}).catch(e=>{if(e.status!==404)throw e;});
  }
  async function appCheckToken() {
    if(cachedToken && cachedToken.expireTimeMillis>Date.now()+60000)return cachedToken;
    const exchange=await fetch(`https://firebaseappcheck.googleapis.com/v1/${appResource}:exchangeDebugToken?key=${config.apiKey}`,{
      method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({debugToken}),signal:AbortSignal.timeout(60000)});
    const value=await exchange.json();assert.ok(exchange.ok,'Debug App Check exchange failed');
    cachedToken={token:value.token,expireTimeMillis:Date.now()+parseInt(value.ttl,10)*1000};return cachedToken;
  }
  let debugToken;
  try {
    deviceCall(['shell','am','start','-n',pkg+'/com.traidores.juego.CloudQaLaunchActivity']);
    debugToken=await waitFor(async()=>{
      const names=deviceCall(['shell','run-as',pkg,'ls','shared_prefs']).split(/\s+/);
      const name=names.find(n=>n.startsWith('com.google.firebase.appcheck.debug.store.'));
      if(!name)return null;
      const text=deviceCall(['shell','run-as',pkg,'cat','shared_prefs/'+name]);
      return text.match(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i)?.[0];
    },Boolean);
    const existing=await api(`https://firebaseappcheck.googleapis.com/v1/${appResource}/debugTokens`);
    if(!(existing.debugTokens||[]).some(t=>t.token===debugToken)) {
      debugResources.push((await api(`https://firebaseappcheck.googleapis.com/v1/${appResource}/debugTokens`,{
        method:'POST',body:JSON.stringify({displayName:room,token:debugToken})})).name);
    }
    await appCheckToken();
    const password=randomUUID()+'A1!',numberBase=998000000000+randomInt(10000000)*10;
    for(let i=0;i<5;i++) {
      const app=initializeApp(config,`${room}-${i}`);apps.push(app);
      initializeAppCheck(app,{provider:new CustomProvider({getToken:appCheckToken}),isTokenAutoRefreshEnabled:false});
      const email=`${room}-${i}@example.test`,user=(await createUserWithEmailAndPassword(getAuth(app),email,password)).user;
      const name=['Vos','Mateo','Luna','Ramón','Clara'][i];users.push({uid:user.uid,user,app,email,name});
      await setDoc(`perfiles_publicos/${user.uid}`,{nombrePerfil:name,publicId:String(numberBase+i),avatarPerfil:'pampa_aldeano'});
    }
    const host=users[0];
    fs.writeFileSync(dir+'/manifest.json',JSON.stringify({room,uids:users.map(u=>u.uid),debugResources,originalGate:original.data},null,2));
    roomCreated=true;
    await setDoc(`partidas/${room}`,{estado:'esperando',nombre:'Práctica privada V3',visibilidad:'privada',hostNombre:host.name,
      hostId:host.uid,hostActivoId:host.uid,hostVersion:0,jugadoresEsperados:5,jugadoresActuales:5,maxJugadores:5,
      modoPrueba:false,partidaInicialCreada:false,limpiezaPendiente:false,mapa:'pampa',mapaNombre:'Pampa',codigoSala:'QA2345',
      origen:'codex-qa-v3-interactive',protocolVersion:3,
      configLobby:{presetRoles:'RECOMMENDED',transicionSeg:4,nocheSeg:60,discusionSeg:90,votacionSeg:45}});
    for(const [i,u] of users.entries())await setDoc(`partidas/${room}/jugadores/${u.uid}`,{
      nombre:u.name,publicId:String(numberBase+i),listo:true,activoEnPartida:true,esHost:i===0,orden:i,
      protocolVersion:3,puedeArbitrar:false,estado:'conectado',uidTemporal:u.uid,avatarPerfil:'pampa_aldeano'});
    ownedGate.allowedHostUids=[host.uid];gateOwned=true;
    await setDoc('onlineMaintenance/serverAuthority',ownedGate);
    // Credentials are temporary fixtures, passed privately to adb, never printed or saved.
    deviceCall(['shell','am','force-stop',pkg]);
    deviceLog=spawn(adb,['-s',device,'logcat','-v','epoch','-T','1','-s','TraidoresOnline:I'],{stdio:['ignore','pipe','ignore']});
    let logBuffer='';
    deviceLog.stdout.on('data',chunk=>{
      logBuffer+=chunk.toString();const lines=logBuffer.split('\n');logBuffer=lines.pop();
      for(const line of lines)if(/\bv3_|essential_presentation_(start|finish)/.test(line) && !/token|password|credential/i.test(line)) {
        fs.appendFileSync(dir+'/android-v3-events.log',line+'\n');
        if(/essential_presentation_start type=role_dealing/.test(line)){capture('reparto',300,1);capture('reparto-cartas',1300,1);}
        if(/v3_render phase=REPARTO\b/.test(line)){capture('mesa-inicial',350,1);capture('rol-presentado',2200,1);}
        if(/v3_render phase=AMANECER\b/.test(line)){capture('amanecer',300,2);capture('amanecer-transicion',1600,2);}
        if(/v3_action_submit/.test(line))capture('confirmacion-inmediata',200,4);
        if(/v3_action_confirmed/.test(line))capture('accion-aceptada',400,4);
        if(/v3_action_confirmed action=votar\b/.test(line)){capture('votaste-y-contorno',1400,4);capture('voto-persistente',5000,4);}
        if(/v3_render phase=DIA_DEBATE\b/.test(line)){capture('votar-antes-contador',2500,2);capture('votar-antes-disponible',12000,2);}
        if(/v3_action_confirmed action=(listo_votar|cancelar_listo)\b/.test(line))capture('votar-antes-confirmado',600,4);
        if(/v3_render phase=RECUENTO_VOTOS\b/.test(line)){capture('recuento-sellos',1500,4);capture('recuento-sellos-final',2800,4);}
        if(/v3_reveal\b/.test(line))capture('anuncio-decorado',1600,4);
        if(/v3_render phase=FINALIZADA\b/.test(line))capture('resultado',500,1);
      }
    });
    deviceLog.on('error',()=>{evidence.androidEventLogUnavailable=true;});
    deviceCall(['shell','am','start','-n',pkg+'/com.traidores.juego.CloudQaLaunchActivity',
      '--es','room',room,'--es','email',host.email,'--es','password',password]);
    console.log('READY: A56 lobby + four ready bots. Press EMPEZAR PARTIDA. Room: '+room);
    const initial=await waitFor(()=>api(config.databaseURL.replace(/\/$/,'')+`/onlineV3/${room}/snapshot/permissions/${host.uid}.json`),v=>!!v?.matchId,20*60*1000);
    evidence.matchId=initial.matchId;evidence.startedAt=new Date().toISOString();
    // This practice is one match: close future starts as soon as the user starts it.
    await closeGate();
    async function callable(u,name,data) {
      const token=await u.user.getIdToken(),check=await appCheckToken();
      const response=await fetch(`https://southamerica-west1-traidores.cloudfunctions.net/${name}`,{
        method:'POST',headers:{'Content-Type':'application/json',Authorization:`Bearer ${token}`,'X-Firebase-AppCheck':check.token},
        body:JSON.stringify({data}),signal:AbortSignal.timeout(60000)});
      const value=await response.json();
      if(!response.ok || value.error){const e=Error('Callable rejected');e.reason=value.error?.details?.reason||value.error?.status;throw e;}
      return value.result;
    }
    evidence.botsMeasurement=await measureCloudMatch({users:users.slice(1),room,matchId:initial.matchId,callable,
      readyVoteDelayMs:process.env.TRAIDORES_QA_READY_VOTE_DELAY_MS===undefined ? null : Number(process.env.TRAIDORES_QA_READY_VOTE_DELAY_MS),
      rememberTask:p=>tasks.add(taskId(room,1,{matchId:p.matchId,phaseIndex:p.phaseIndex,deadlineMs:p.limiteFaseEpochMs})),
      signal:abort.signal,maxDurationMs:30*60*1000,
      onProgress:event=>fs.appendFileSync(dir+'/progress.jsonl',JSON.stringify(event)+'\n')});
    evidence.gameFinishedAt=new Date().toISOString();
    const record=historyId(`online:${initial.matchId}`);
    const history=await waitFor(()=>getDoc(`cuentas/${host.uid}/historial/${record}`),v=>!!v?.data.contabilizada);
    evidence.hostHistory={contabilizada:history.data.contabilizada,won:history.data.won};
    await pause(2000);
    await Promise.all([...captureJobs]);evidence.captures=captures;
    try{fs.writeFileSync(dir+'/android-metrics.txt',deviceCall(['shell','am','broadcast','-n',pkg+'/com.traidores.juego.CloudQaMetricsReceiver']));}
    catch{evidence.androidMetricsUnavailable=true;}
    fs.writeFileSync(dir+'/evidence.json',JSON.stringify(evidence,null,2));
    console.log('FINISHED: '+evidence.botsMeasurement.winner+'. Evidence: '+dir+'. New starts already closed. Result remains on screen for ten minutes or until Ctrl+C.');
    const until=Date.now()+10*60*1000;
    while(!abort.signal.aborted && Date.now()<until)await pause(1000);
  }finally {
    deviceLog?.kill();
    for(const timer of captureTimers)clearTimeout(timer);
    await Promise.all([...captureJobs]);evidence.captures=captures;
    await clean(closeGate);
    try{deviceCall(['shell','am','force-stop',pkg]);}catch{}
    for(const app of apps){goOffline(getDatabase(app));await clean(()=>terminate(getFirestore(app)));await clean(()=>deleteApp(app));}
    for(const id of tasks)await clean(()=>api(`https://cloudtasks.googleapis.com/v2/projects/traidores/locations/southamerica-east1/queues/resolverFaseV3/tasks/${id}`,{method:'DELETE'}).catch(e=>{if(e.status!==404)throw e;}));
    if(roomCreated){
      await clean(()=>deleteTree(`partidas/${room}`));
      await clean(()=>api(config.databaseURL.replace(/\/$/,'')+`/onlineV3/${room}.json`,{method:'DELETE'}));
      await clean(async()=>{
        // The room-deletion trigger writes these owned markers asynchronously.
        const until=Date.now()+45000;let marker;
        while(Date.now()<until && !(marker=await getDoc(`onlineRoomCleanupOrphans/${room}`)))await pause(1500);
        assert.ok(marker,'Owned cleanup marker not observed; review before another run');
        await pause(1500);
        for(const collection of ['onlineRoomCleanupQueue','onlineRoomCleanupOrphans'])
          await api(documentBase+`${collection}/${room}`,{method:'DELETE'}).catch(e=>{if(e.status!==404)throw e;});
      });
    }
    for(const u of users) {
      await clean(()=>deleteTree(`cuentas/${u.uid}`));
      for(const path of [`perfiles_publicos/${u.uid}`,`onlineRequestLimits/${requestLimitId(u.uid)}`])
        await clean(()=>api(documentBase+path,{method:'DELETE'}).catch(e=>{if(e.status!==404)throw e;}));
      await clean(()=>api('https://identitytoolkit.googleapis.com/v1/projects/traidores/accounts:delete',{method:'POST',body:JSON.stringify({localId:u.uid})}));
    }
    for(const resource of debugResources)await clean(()=>api('https://firebaseappcheck.googleapis.com/v1/'+resource,{method:'DELETE'}));
    evidence.cleanedAt=new Date().toISOString();evidence.cleanupErrors=cleanupErrors;
    fs.writeFileSync(dir+'/evidence.json',JSON.stringify(evidence,null,2));
    console.log(cleanupErrors.length?'Review cleanup errors in '+dir+'/evidence.json':'Owned practice accounts/room/token removed; public access remains closed.');
    process.removeListener('SIGINT',stop);process.removeListener('SIGTERM',stop);
    if(cleanupErrors.length)throw Error('Private practice cleanup requires review');
  }
}
if(require.main===module)main().then(()=>process.exit(0),e=>{console.error(e.name==='AbortError'?'Practice stopped; cleanup completed.':e.message);process.exit(e.name==='AbortError'?0:1);});
