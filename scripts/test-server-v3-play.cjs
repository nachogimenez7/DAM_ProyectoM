"use strict";
const {test} = require("node:test");
const assert = require("node:assert/strict");
const {chooseAction} = require("./play-server-client-android.cjs");
function fixture(role,phase="NOCHE") {
  return [{matchId:"match",phaseIndex:1,fase:phase,ronda:2,
    jugadores:[{uidTemporal:"self",orden:0,vivo:true},{uidTemporal:"ally",orden:1,vivo:true},{uidTemporal:"target",orden:2,vivo:true}]},
  {matchId:"match",phaseIndex:1,rolesVisibles:[{orden:0,rolKey:role}]}];
}
test("QA bots wait for coherent private state and never act dead or after the result", () => {
  const [pub,priv]=fixture("policia");
  assert.equal(chooseAction(pub,{...priv,phaseIndex:0},"self"),null);
  assert.equal(chooseAction({...pub,ganador:"Pueblo"},priv,"self"),null);
  pub.jugadores[0].vivo=false; assert.equal(chooseAction(pub,priv,"self"),null);
});
test("QA killers use visible allies and never target themselves or another traitor", () => {
  const [pub,priv]=fixture("asesino"); priv.rolesVisibles.push({orden:1,rolKey:"mercenario"});
  assert.deepEqual(chooseAction(pub,priv,"self"),{action:"matar",targetUid:"target"});
});
test("QA mercenary honors the private cooldown projection", () => {
  const [pub,priv]=fixture("mercenario"); priv.objetivosBloqueados=["ally"];
  assert.deepEqual(chooseAction(pub,priv,"self"),{action:"silenciar",targetUid:"target"});
  priv.objetivosBloqueados.push("target"); assert.equal(chooseAction(pub,priv,"self"),null);
});
test("QA voting respects silence and second-vote candidates", () => {
  const [pub,priv]=fixture("aldeano","DESEMPATE_VOTACION"); pub.empateVoto=["target"];
  assert.deepEqual(chooseAction(pub,priv,"self"),{action:"votar",targetUid:"target"});
  pub.jugadores[0].muteado=true; assert.equal(chooseAction(pub,priv,"self"),null);
});
test("QA mayor cannot decide while silenced or without revealing", () => {
  const [pub,priv]=fixture("alcalde","ALCALDE_DESEMPATE"); pub.empateVoto=["target"];
  assert.equal(chooseAction(pub,priv,"self"),null);
  pub.alcaldeRevelado="self";
  assert.deepEqual(chooseAction(pub,priv,"self"),{action:"decidir_empate",targetUid:"target"});
  pub.jugadores[0].muteado=true; assert.equal(chooseAction(pub,priv,"self"),null);
});
