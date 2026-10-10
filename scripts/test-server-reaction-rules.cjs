'use strict';
const fs = require('node:fs');
const {initializeTestEnvironment, assertSucceeds, assertFails} = require('@firebase/rules-unit-testing');
const {prepareOnlineMatch} = require('../functions/src/onlineStartCore');
const {createServerGame, projectServerGame} = require('../functions/src/onlineGameCore');
(async () => {
  const env = await initializeTestEnvironment({projectId:'traidores-local', database:{rules:fs.readFileSync('database.rules.json','utf8')}});
  let checks=0; const pass=async p=>{await assertSucceeds(p);checks++;}, deny=async p=>{await assertFails(p);checks++;};
  const room='reaction-security', path=`onlineV3/${room}`;
  const roomConfig={hostId:'p0',estado:'esperando',jugadoresEsperados:5,codigoSala:'EMOTE',mapa:'pampa'};
  const prepared=prepareOnlineMatch({requesterId:'p0',room:roomConfig,players:Array.from({length:5},(_,i)=>({id:`p${i}`,order:i,name:`P${i}`,activeInMatch:true,ready:true})),matchId:'reaction-match',nowMs:Date.now(),randomInt:()=>0});
  const state=createServerGame({roomId:room,prepared,nowMs:Date.now()});
  const db=env.authenticatedContext('p0').database(), peer=env.authenticatedContext('p1').database();
  async function admin(body) {return env.withSecurityRulesDisabled(c=>body(c.database()));}
  async function reset(phase='DIA_DEBATE', extra={}) {
    Object.assign(state,{phase,phaseIndex:8,round:2,winner:null,deadlineMs:Date.now()+90000,counterpointPlayers:['p0','p1']});
    Object.assign(state.players[0],{alive:true,muted:false,left:false},extra);
    await admin(d=>d.ref(path).set({snapshot:projectServerGame(state)}));
  }
  function payload(uid='p0', slot=0, uses=1, fields={}, rateFields={}) {
    const id=`${uid}_${slot}`, ts={'.sv':'timestamp'};
    return {[`reactions/${id}`]:{actorUid:uid,matchId:state.matchId,phaseIndex:state.phaseIndex,slot,emoteId:'griego_contento',ts,...fields},
      [`reactionRate/${uid}`]:{reactionId:id,matchId:state.matchId,slot,round:state.round,uses,ts,...rateFields}};
  }
  try {
    for(const phase of ['DIA_DEBATE','CONTRAPUNTO','VOTACION','RECUENTO_VOTOS','DESEMPATE_VOTACION','ALCALDE_DESEMPATE']) {
      await reset(phase); await pass(db.ref(path).update(payload()));
    }
    for(const phase of ['REPARTO','NOCHE','AMANECER','RESULTADO','DESERTOR_RECONSIDERACION']) {
      await reset(phase);await deny(db.ref(path).update(payload()));
    }
    for(const player of [{muted:true},{alive:false},{left:true}]) {await reset('DIA_DEBATE',player);await deny(db.ref(path).update(payload()));}
    for(const fields of [{emoteId:'premium_six_seven'},{emoteId:'invalid'},{actorUid:'p1'},{matchId:'old-match'},{phaseIndex:7},{slot:8},{ts:1},{extra:'oops'}]) {
      await reset();await deny(db.ref(path).update(payload('p0',0,1,fields)));
    }
    await reset();await deny(peer.ref(path).update(payload()));
    await deny(env.unauthenticatedContext().database().ref(`${path}/reactions`).once('value'));
    await deny(env.authenticatedContext('outsider').database().ref(`${path}/reactions`).once('value'));
    await pass(peer.ref(`${path}/reactions`).orderByChild('ts').limitToLast(40).once('value'));
    await pass(db.ref(path).update(payload()));
    await deny(db.ref(path).update(payload('p0',1,2))); // ten seconds on server, cannot evade with fresh slot
    await admin(d=>d.ref(`${path}/reactionRate/p0/ts`).set(Date.now()-11000));
    await pass(db.ref(path).update(payload('p0',1,2)));
    await admin(d=>d.ref(`${path}/reactionRate/p0/ts`).set(Date.now()-11000));
    await deny(db.ref(path).update(payload('p0',2,3))); // per-round limit also enforced by rules
    state.round=3;await admin(d=>d.ref(`${path}/snapshot/public/ronda`).set(3));
    await pass(db.ref(path).update(payload('p0',2,1)));
    await reset();await deny(db.ref(`${path}/reactionRate/p0`).set(payload()['reactionRate/p0']));
    await deny(db.ref(`${path}/reactions/p0_0`).set(payload()['reactions/p0_0']));
    await reset(); await admin(d=>d.ref(`${path}/snapshot/public/limiteFaseEpochMs`).set(Date.now()-1));
    await deny(db.ref(path).update(payload()));
    await reset();await admin(d=>d.ref(`${path}/snapshot/cleanupState`).set('deleting'));
    await deny(db.ref(path).update(payload()));
    console.log(`Reaction rules: ${checks} checks OK (phase, mute, death, membership, atomic rate, old match, base catalog).`);
  } finally {await admin(d=>d.ref(path).remove());await env.cleanup();}
})().catch(e=>{console.error(e);process.exitCode=1;});
