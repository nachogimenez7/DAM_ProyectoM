'use strict';
const fs=require('node:fs'),assert=require('node:assert/strict');
const {initializeTestEnvironment}=require('@firebase/rules-unit-testing');
const {prepareOnlineMatch}=require('../functions/src/onlineStartCore');
const {createServerGame,projectServerGame}=require('../functions/src/onlineGameCore');
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
(async()=>{
 const env=await initializeTestEnvironment({projectId:'traidores-local',database:{rules:fs.readFileSync('database.rules.json','utf8')}});
 const results=[];
 try {
  for(const count of [5,10,15]) {
   const room=`emote-meter-${count}`,path=`onlineV3/${room}`,uid=i=>`player${i}`.padEnd(28,'x');
   const prepared=prepareOnlineMatch({requesterId:uid(0),room:{hostId:uid(0),estado:'esperando',mapa:'pampa',codigoSala:'METER',jugadoresEsperados:count},players:Array.from({length:count},(_,i)=>({id:uid(i),order:i,name:`Player${i}`,activeInMatch:true,ready:true})),matchId:'12345678-1234-1234-1234-123456789012',nowMs:Date.now(),randomInt:()=>0});
   const state=createServerGame({roomId:room,prepared,nowMs:Date.now()});Object.assign(state,{phase:'DIA_DEBATE',deadlineMs:Date.now()+90000,round:2});
   await env.withSecurityRulesDisabled(c=>c.database().ref(path).set({snapshot:projectServerGame(state)}));
   const began=Date.now(),stats={players:count,sent:0,callbacks:0,rawCallbacks:0,receivedJsonBytes:0,writeJsonBytes:0,rateReads:0,subscriptions:count};
   const clients=Array.from({length:count},(_,i)=>env.authenticatedContext(uid(i)).database()),listeners=[];
   for(const db of clients) {
    const query=db.ref(`${path}/reactions`).orderByChild('ts').startAt(began+1).limitToLast(40);
    const seen=new Map();
    const receive=s=>{const v=s.val(),key=`${v.actorUid}:${v.slot}`;stats.rawCallbacks++;stats.receivedJsonBytes+=Buffer.byteLength(JSON.stringify(v));if(seen.has(key)&&Math.abs(v.ts-seen.get(key))<10000)return;seen.set(key,v.ts);stats.callbacks++;};
    query.on('child_added',receive);query.on('child_changed',receive);listeners.push(()=>query.off());
    await query.once('value');
   }
   for(let slot=0;slot<2;slot++) {
    if(slot) await sleep(10100);
    for(let i=0;i<count;i++) {
     const db=clients[i],actor=uid(i),id=`${actor}_${slot}`,ts={'.sv':'timestamp'};
     await db.ref(`${path}/reactionRate/${actor}`).once('value');stats.rateReads++;
     const payload={[`reactions/${id}`]:{actorUid:actor,matchId:state.matchId,phaseIndex:state.phaseIndex,slot,emoteId:'griego_contento',ts},[`reactionRate/${actor}`]:{reactionId:id,slot,round:2,uses:slot+1,matchId:state.matchId,ts}};
     stats.writeJsonBytes+=Buffer.byteLength(JSON.stringify(payload));await db.ref(path).update(payload);stats.sent++;
    }
    const until=Date.now()+10000;while(stats.callbacks<count*count*(slot+1)&&Date.now()<until)await sleep(20);
    assert.equal(stats.callbacks,count*count*(slot+1));
   }
   listeners.forEach(stop=>stop());stats.jsonBytesPerReceiver=stats.receivedJsonBytes/count;
   stats.note='Measured RTDB emulator SDK JSON, all players send both allowed emotes in one round; excludes wire/TLS overhead, billing credits and other gameplay.';
   results.push(stats);console.log(stats);
   await env.withSecurityRulesDisabled(c=>c.database().ref(path).remove());
  }
  fs.mkdirSync('output/paridad-visual',{recursive:true});fs.writeFileSync('output/paridad-visual/emote-measurement.json',JSON.stringify(results,null,2));
 } finally {await env.cleanup();}
})().catch(e=>{console.error(e);process.exitCode=1});
