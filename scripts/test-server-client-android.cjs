'use strict';
// Only runs against firebase.authority-qa.json and an explicitly selected Android emulator.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {execFileSync} = require('node:child_process');
const adminRequire = require('node:module').createRequire(require.resolve('../functions/package.json'));
const {initializeApp,deleteApp}=adminRequire('firebase-admin/app');
const {getFirestore}=adminRequire('firebase-admin/firestore');
const {getDatabase}=adminRequire('firebase-admin/database');
const {getAuth}=adminRequire('firebase-admin/auth');
const {AUTHORITY_GATE}=require('../functions/src/onlineGameService');
const {requestLimitId}=require('../functions/src/onlineRequestLimiter');
for(const key of ['FIRESTORE_EMULATOR_HOST','FIREBASE_DATABASE_EMULATOR_HOST','FIREBASE_AUTH_EMULATOR_HOST']) assert.match(process.env[key]||'',/^127\.0\.0\.1:/);
assert.equal(process.env.GCLOUD_PROJECT,'traidores-local');
const adb=process.env.TRAIDORES_QA_ADB; assert.ok(adb && fs.existsSync(adb));
const device=process.env.TRAIDORES_QA_DEVICE; assert.ok(device);
const app=initializeApp({projectId:'traidores-local',databaseURL:'https://traidores-local-default-rtdb.firebaseio.com'},'native-v3-qa');
const db=getFirestore(app),rtdb=getDatabase(app),auth=getAuth(app);
const room=`native-v3-${Date.now()}`,password='OnlyEmulatorQA123!',members=[];
const prefix=process.env.TRAIDORES_QA_PACKAGE || 'com.traidores.juego';
assert.ok(['com.traidores.juego', 'com.traidores.juego.v3qa'].includes(prefix));
assert.ok(/^emulator-\d+$/.test(device) || prefix === 'com.traidores.juego.v3qa', 'Physical QA must preserve the installed game');
let originalGate;
function command(...args){return execFileSync(adb,['-s',device,...args],{encoding:'utf8',maxBuffer:8000000,timeout:30000});}
const pause=(ms)=>new Promise(r=>setTimeout(r,ms));
async function eventually(read,accepts,label,ms=20000){const end=Date.now()+ms;while(Date.now()<end){const v=await read();if(accepts(v))return v;await pause(350);}throw Error(`Timed out: ${label}`);}
const windowDump = process.env.TRAIDORES_QA_WINDOW_DUMP;
let screenSize;
async function xml(){
  for (let attempt=0; ; attempt++) {
    command('shell','rm','-f','/sdcard/v3-qa.xml');
    try {
      if (windowDump) command('shell', `CLASSPATH=/system/framework/uiautomator.jar:/data/local/tmp/traidores-window-dump.jar app_process /system/bin WindowDump /sdcard/v3-qa.xml ${screenSize}`);
      else command('shell','uiautomator','dump','/sdcard/v3-qa.xml');
      return command('shell','cat','/sdcard/v3-qa.xml');
    } catch(error) {
      if (attempt>=2 || !(error.status===137 || /No active accessibility root/.test(String(error)))) throw error;
      await pause(250);
    }
  }
}
function tapNode(xml,predicate){const nodes=xml.match(/<node\b[^>]*>/g)||[]; const node=nodes.find(predicate);assert.ok(node,'Missing actionable native view');const m=node.match(/bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/);assert.ok(m);command('shell','input','tap',String(Math.round((+m[1]+ +m[3])/2)),String(Math.round((+m[2]+ +m[4])/2)));}
function shot(name){const bytes=execFileSync(adb,['-s',device,'exec-out','screencap','-p'],{maxBuffer:8000000,timeout:30000});fs.writeFileSync(`output/server-v3-native-${name}.png`,bytes);}
async function deleteEmulatorDocument(reference){
  // A cancelled watch can make the emulator cancel a committed delete; deletion is idempotent.
  for(let attempt=0; ; attempt++){
    try { await reference.delete(); return; }
    catch(error){ if(error.code!==1 || attempt>=2) throw error; await pause(250*(attempt+1)); }
  }
}
async function deleteOwnedDocument(reference){
  // Sequential cleanup avoids BulkWriter cancellation races in the Firestore emulator.
  for(const collection of await reference.listCollections())
    for(const child of await collection.listDocuments()) await deleteOwnedDocument(child);
  await deleteEmulatorDocument(reference);
}
async function enter(){command('shell','am','force-stop',prefix);command('shell','am','start','-n',`${prefix}/com.traidores.juego.ServerGameSmokeActivity`,'--es','room',room,'--es','email',members[0].email,'--es','password',password,'--ez','via_lobby','true');}
async function callable(name,data,token){const jwt=[Buffer.from('{"alg":"none","typ":"JWT"}').toString('base64url'),Buffer.from('{"app_id":"native-qa"}').toString('base64url'),'local'].join('.');const response=await fetch(`http://127.0.0.1:15001/traidores-local/southamerica-west1/${name}`,{method:'POST',headers:{'Content-Type':'application/json',Authorization:`Bearer ${token}`,'X-Firebase-AppCheck':jwt},body:JSON.stringify({data})});const result=await response.json();assert.equal(response.status,200,JSON.stringify(result));return result.result;}
async function main(){
  try {
    if (windowDump) {
      assert.ok(fs.existsSync(windowDump)); command('push', windowDump, '/data/local/tmp/traidores-window-dump.jar');
      const sizes = [...command('shell','wm','size').matchAll(/(\d+)x(\d+)/g)]; assert.ok(sizes.length);
      screenSize = sizes.at(-1).slice(1).join(' ');
    }
    originalGate = await db.doc(AUTHORITY_GATE).get();
    for(let i=0;i<5;i++) {
      const email=`native-v3-${Date.now()}-${i}@example.test`;
      const user=await auth.createUser({email,password,emailVerified:true});
      const res=await fetch(`http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=local-only`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email,password,returnSecureToken:true})});
      const signed=await res.json();assert.equal(res.status,200);members.push({uid:user.uid,email,token:signed.idToken});
    }
    const host=members[0]; const batch=db.batch();
    batch.set(db.doc(AUTHORITY_GATE),{enabled:true});
    batch.set(db.doc(`partidas/${room}`),{estado:'esperando',nombre:'QA Android',hostNombre:'QA0',hostId:host.uid,hostActivoId:host.uid,hostVersion:0,
      jugadoresEsperados:5,jugadoresActuales:5,maxJugadores:5,modoPrueba:false,partidaInicialCreada:false,limpiezaPendiente:false,mapa:'pampa',mapaNombre:'Pampa',codigoSala:'QA2345',origen:'qa-emulator',
      protocolVersion:3,configLobby:{presetRoles:'RECOMMENDED',transicionSeg:1,nocheSeg:10,discusionSeg:60,votacionSeg:15}});
    members.forEach((u,i)=>batch.set(db.doc(`partidas/${room}/jugadores/${u.uid}`),{nombre:`QA${i}`,publicId:`${i+1}`,listo:true,activoEnPartida:true,orden:i,protocolVersion:3,puedeArbitrar:false,estado:'conectado',uidTemporal:u.uid}));
    batch.set(db.doc(`perfiles_publicos/${host.uid}`),{nombrePerfil:'QA0',publicId:'1'});await batch.commit();
    await enter();
    await eventually(xml,x=>x.includes('TU ROL') && /text="YA LEÍ MI ROL"[^>]*enabled="true"/.test(x),'native private role and permitted acknowledgement',30000);
    await pause(1500); // Read fresh bounds after the shared role entrance animation.
    const first = await xml(); shot('role');
    assert.ok(!first.includes('V3 QA FAIL'));
    tapNode(first,n=>n.includes('text="YA LEÍ MI ROL"')||n.includes('text="ELEGIR PUEBLO"'));
    const stateRef=db.doc(`partidas/${room}/servidor/current`);
    const acknowledged=await eventually(async()=> (await stateRef.get()).data(),s=>Object.values(s?.actions||{}).some(a=>a.actorOrder===0),'native intention acknowledged');
    assert.equal(Object.values(acknowledged.actions).filter(a=>a.actorOrder===0).length,1);
    console.log('NATIVE V3: Auth, callable start, three projections and private action passed.');
    command('shell','am','force-stop',prefix);
    const night=await eventually(async()=> (await stateRef.get()).data(),s=>s?.phase==='NOCHE','Tasks advances with every game client closed',50000);
    assert.equal(night.phaseIndex,1);console.log('NATIVE V3: Tasks advanced while the Android app was closed.');
    await enter();
    await eventually(xml,x=>x.includes('NOCHE')&&x.includes('/roleName'),'native reconnect',20000);shot('reconnect');
    const day=await eventually(async()=> (await stateRef.get()).data(),s=>s?.phase==='DIA_DEBATE','automatic dawn/debate',30000);
    let ui=await eventually(xml,x=>x.includes('DEBATE')&&!x.includes('Sincronizando partida') &&
      !['/noDeathRevealOverlay','/deathRevealOverlay','/silenceRevealOverlay','/dayNightTransitionOverlay'].some(id=>x.includes(id)),
      'native debate after automatic dawn ceremony');
    ui=await eventually(async()=>{
      const visible=await xml();
      if (!visible.includes('/chatInput') && !['/noDeathRevealOverlay','/deathRevealOverlay','/silenceRevealOverlay','/dayNightTransitionOverlay'].some(id=>visible.includes(id)) &&
        visible.includes("/chatAmbientTitle"))
        tapNode(visible,n=>n.includes('/chatAmbientTitle'));
      return visible;
    },x=>x.includes('/chatInput'),'native chat opens');
    tapNode(ui,n=>n.includes('class="android.widget.EditText"'));command('shell','input','text','mensaje%sdesde%sAndroid');
    // Hide the IME and refresh bounds before tapping; keyboard animation invalidates the first dump.
    command('shell','input','keyevent','4');
    await pause(600); // Wait for the IME/panel layout to settle before reading tap bounds.
    const composing=await xml();assert.ok(/text="mensaje desde Android"/i.test(composing));
    tapNode(composing,n=>n.includes('text="ENVIAR"')&&n.includes('enabled="true"'));
    await eventually(async()=> (await rtdb.ref(`onlineV3/${room}/chat/publico/${host.uid}_0`).get()).val(),m=>m?.actorUid===host.uid && m?.text.toLowerCase()==='mensaje desde android','atomic native chat message');
    assert.ok((await rtdb.ref(`onlineV3/${room}/chatRate/${host.uid}`).get()).exists());
    shot('chat');console.log('NATIVE V3: reconnect and bounded real RTDB chat passed.');
    for(const member of members.slice(1)) await callable('abandonarPartidaV3',{roomId:room,matchId:day.matchId},member.token);
    await eventually(async()=> (await stateRef.get()).data(),s=>s?.winner,'server final result');
    await eventually(async()=> (await db.doc(`partidas/${room}/serverOutbox/current`).get()).data(),o=>o?.deliveredRevision===o?.revision,'final outbox acknowledgement');
    await eventually(xml,x=>x.includes('PREPARAR REVANCHA')&&(x.includes('VICTORIA')||x.includes('DERROTA')),'native winners UI');
    await pause(1000); const final=await xml(); shot('winner');
    tapNode(final,n=>n.includes('text="PREPARAR REVANCHA"'));
    await eventually(async()=> (await db.doc(`partidas/${room}`).get()).data(),s=>s?.authorityMode==='lobby','native rematch callable');
    assert.equal((await rtdb.ref(`onlineV3/${room}/chat`).get()).exists(),false);
    assert.ok((await db.collection(`cuentas/${host.uid}/historial`).get()).size>0,'Finished game archived for the account');
    console.log('NATIVE V3: final result, account archive and clean rematch passed.');
  } catch (error) {
    fs.writeFileSync('output/server-v3-native-failure.xml', await xml());
    fs.writeFileSync('output/server-v3-native-failure.log', command('logcat','-d','-s','TraidoresOnline:E','TRAIDORES_ONLINE:E','AndroidRuntime:E','TRAIDORES_V3_QA:E'));
    throw error;
  } finally {
    try {
    command('shell','am','force-stop',prefix);
    await pause(2000); // Let native Firestore listeners close before teardown.
    await deleteOwnedDocument(db.doc(`partidas/${room}`));await rtdb.ref(`onlineV3/${room}`).remove();
    if (originalGate?.exists) await db.doc(AUTHORITY_GATE).set(originalGate.data());
    else if (originalGate) await deleteEmulatorDocument(db.doc(AUTHORITY_GATE));
    for(const user of members){await auth.deleteUser(user.uid);await deleteEmulatorDocument(db.doc(`onlineRequestLimits/${requestLimitId(user.uid)}`));await deleteEmulatorDocument(db.doc(`perfiles_publicos/${user.uid}`));await deleteOwnedDocument(db.doc(`cuentas/${user.uid}`));}
    } finally { await deleteApp(app); }
  }
}
main().catch(e=>{console.error(e);process.exitCode=1;});
