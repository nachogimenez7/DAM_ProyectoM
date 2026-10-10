'use strict';
const fs = require('node:fs');
const {prepareOnlineMatch} = require('../functions/src/onlineStartCore');
const {createServerGame, expirePhase, deadlineToken, projectServerGame, acceptAction} = require('../functions/src/onlineGameCore');
const frames = []; const NOW = 1800000000000;
function make(count, map) {
  const prepared = prepareOnlineMatch({requesterId:'p0', room:{hostId:'p0',estado:'esperando',jugadoresEsperados:count,
    codigoSala:'ABCDE',mapa:map,configLobby:{presetRoles:'RECOMMENDED'}},
    players:Array.from({length:count},(_,i)=>({id:`p${i}`,order:i,name:`J${i}`,activeInMatch:true,ready:true,publicId:`${i+1}`})),
    matchId:`fixture-${map}-${count}`,nowMs:NOW,randomInt:()=>0});
  return createServerGame({roomId:'client-fixture',prepared,nowMs:NOW});
}
function capture(name,state) {
  const projection=projectServerGame(state);
  Object.values(projection.private).forEach(p=>p.revision=1); // Assigned by the actual publisher, not the core.
  frames.push({name,projection});
}
for (const [count,map] of [[5,'pampa'],[10,'pampa'],[15,'pampa'],[15,'medieval'],[15,'grecia']]) {
  let state=make(count,map); capture(`${map}-${count}-deal`,state);
  state=expirePhase(state,deadlineToken(state),state.deadlineMs,{chooseRandomInt:()=>0}).state;
  capture(`${map}-${count}-night`,state);
  const merc=state.players.find(p=>p.role.key==='mercenario'), town=state.players.find(p=>p.role.key==='aldeano');
  if (merc && town) {
    state=acceptAction(state,merc.uid,{matchId:state.matchId,phaseIndex:state.phaseIndex,requestId:'native-cooldown',action:'silenciar',targetUid:town.uid},state.phaseStartedAtMs+1).state;
    capture(`${map}-${count}-secret`,state);
  }
  for (let step=0;step<7 && !state.winner;step++) {
    state=expirePhase(state,deadlineToken(state),state.deadlineMs).state; capture(`${map}-${count}-natural-${step}`,state);
  }
}
let state=make(15,'pampa');
state.phase='DIA_DEBATE';state.phaseIndex=7;state.round=4;state.deserterTeam='Pueblo';
state.players.find(p=>p.role.key==='desertor').muted=true;
state.players.find(p=>p.role.key==='alcalde').muted=true;
const blocked=state.players.find(p=>p.role.key==='aldeano');blocked.lastSilencedRound=3;
capture('muted-deserter-and-mayor',state);
state.phase='NOCHE';state.phaseIndex=8;capture('mercenary-previous-night',state);
state=make(15,'grecia');state.phase='NOCHE';state.phaseIndex=9;state.round=2;
const dead=state.players.find(p=>p.role.key==='aldeano');dead.alive=false;dead.deathCause='NIGHT';
const left=state.players.filter(p=>p.role.key==='aldeano')[1];left.alive=false;left.left=true;left.deathCause='ABANDONO';
capture('oracle-dead-and-departed-target',state);
state.phase='FINALIZADA';state.phaseIndex=10;state.winner='Pueblo';state.deadlineMs=null;capture('final-roles-and-abandonment',state);
state=make(15,'pampa'); state.phase='VOTACION'; state.phaseIndex=12; state.round=2;
const mayor=state.players.find(p=>p.role.key==='alcalde'), voter=state.players.find(p=>p.role.key==='aldeano');
const voted=state.players.filter(p=>p.role.key==='aldeano')[1]; state.mayorUid=mayor.uid;
for (const actor of [mayor,voter]) state=acceptAction(state,actor.uid,{matchId:state.matchId,phaseIndex:state.phaseIndex,
  requestId:`fixture_vote_${actor.order}`,action:'votar',targetUid:voted.uid},state.phaseStartedAtMs+1).state;
capture('votes-still-secret',state);
state=expirePhase(state,deadlineToken(state),state.deadlineMs).state;capture('aggregate-recount',state);
state.afkEnabled=false;
state=expirePhase(state,deadlineToken(state),state.deadlineMs).state;capture('expulsion-public-result',state);
for (const reveal of [false,true]) {
  let result=make(15,'pampa'); result.phase='RECUENTO_VOTOS'; result.phaseIndex=14; result.afkEnabled=false;
  result.config.revelarRolesAlMorir=reveal;
  result.eliminationUid=result.players.find(p=>p.role.key==='policia').uid;
  result=expirePhase(result,deadlineToken(result),result.deadlineMs).state;
  capture(`expulsion-role-${reveal}`,result);
}
state=make(8,'medieval');state.phase='RECUENTO_VOTOS';state.phaseIndex=16;state.afkEnabled=false;
state.config.revelarRolesAlMorir=false;state.eliminationUid=state.players.find(p=>p.role.key==='bufon').uid;
state=expirePhase(state,deadlineToken(state),state.deadlineMs).state;capture('jester-public-result',state);
fs.writeFileSync('app/src/test/resources/server_game_v3.json',JSON.stringify(frames));
console.log(`Generated ${frames.length} projections from the server engine.`);
