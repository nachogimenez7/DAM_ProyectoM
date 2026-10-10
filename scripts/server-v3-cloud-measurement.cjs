"use strict";
// QA only; never imported by the APK or deployed Functions. Clients know only
// their authorized projections. No Admin game state or artificial clock changes.
const assert=require("node:assert/strict");
const {randomUUID}=require("node:crypto");
const {getDatabase,ref,onValue,update,serverTimestamp}=require("firebase/database");
const {chooseAction}=require("./play-server-client-android.cjs");
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
function percentile(values,fraction) {
  if(!values.length) return null;
  return [...values].sort((a,b)=>a-b)[Math.max(0,Math.ceil(values.length*fraction)-1)];
}
async function measureCloudMatch({users,room,matchId,callable,rememberTask,signal,maxDurationMs=12*60*1000,onProgress=()=>{},readyVoteDelayMs=null}) {
  assert.ok(Number.isFinite(maxDurationMs) && maxDurationMs>0 && maxDurationMs<=30*60*1000);
  assert.ok(readyVoteDelayMs===null || Number.isFinite(readyVoteDelayMs) && readyVoteDelayMs>=10000);
  const stats={public:{callbacks:0,jsonBytes:0},private:{callbacks:0,jsonBytes:0},permissions:{callbacks:0,jsonBytes:0},chat:{callbacks:0,jsonBytes:0}};
  const contexts=users.map(u=>({u,publicState:null,own:null,permission:null,busy:false,keys:new Set(),chatSlots:0,chatChannel:null,chatStop:null}));
  const records=[],pending=new Set(),stops=[],errors=[],phases=new Map();
  const began=Date.now(); let finished=null;
  const countValue=(kind,value)=>{stats[kind].callbacks++; stats[kind].jsonBytes+=Buffer.byteLength(JSON.stringify(value));};
  const addWork=work=>{pending.add(work);work.finally(()=>pending.delete(work));};
  const channels=c=>c.permission.publicChat ? "publico" : c.permission.traitorChat ? "traidores" : c.permission.deadChat ? "muertos" : null;
  function watchChat(c) {
    const channel=channels(c);
    if(c.chatChannel===channel) return;
    c.chatStop?.(); c.chatStop=null; c.chatChannel=channel;
    if(channel) c.chatStop=onValue(ref(getDatabase(c.u.app),`onlineV3/${room}/chat/${channel}`),s=>countValue("chat",s.val()),e=>errors.push({kind:"chat-listener",code:e.code}));
  }
  function observe(c) {
    const p=c.publicState;
    if(!p || !c.own || !c.permission || p.matchId!==matchId || c.own.matchId!==matchId || c.permission.matchId!==matchId ||
        p.phaseIndex!==c.own.phaseIndex || p.phaseIndex!==c.permission.phaseIndex) return;
    watchChat(c);
    for(const record of records) if(record.client===c.u.uid && record.phaseIndex===p.phaseIndex && !record.projectionAt &&
      (c.own.accionesConfirmadas||[]).some(a=>a.action===record.intent.action && (a.targetUid||null)===(record.intent.targetUid||null) && (a.team||null)===(record.intent.team||null))) record.projectionAt=Date.now();
    if(!phases.has(p.phaseIndex)) {
      phases.set(p.phaseIndex,{phaseIndex:p.phaseIndex,phase:p.fase,round:p.ronda,observedAt:Date.now(),deadlineMs:p.limiteFaseEpochMs,
        votingReadyFromMs:p.listosVotar?.desdeEpochMs||null,individualBallots:p.votosIndividuales||null});
      onProgress({kind:"phase",...phases.get(p.phaseIndex)});
      if(p.limiteFaseEpochMs) rememberTask(p);
      console.log(`CLOUD ${users.length}: phase ${p.fase}, round ${p.ronda}`);
    }
    if(p.ganador) {
      if(contexts.every(x=>x.publicState?.ganador===p.ganador && x.own?.phaseIndex===p.phaseIndex && x.permission?.phaseIndex===p.phaseIndex)) finished=p;
      return;
    }
    if(c.busy || signal.aborted || Date.now() >= p.limiteFaseEpochMs) return;
    const self=p.jugadores?.find(player=>player.uidTemporal===c.u.uid);
    const readyToVote=readyVoteDelayMs!==null && p.fase==="DIA_DEBATE" && self?.vivo &&
      Number.isFinite(p.listosVotar?.desdeEpochMs) && Date.now()>=p.listosVotar.desdeEpochMs+readyVoteDelayMs-10000;
    const action=chooseAction(p,c.own,c.u.uid) || (readyToVote ? {action:"listo_votar"} : null);
    const key=action && `${p.phaseIndex}:${action.action}`;
    if(!action || c.keys.has(key)) return;
    c.keys.add(key); c.busy=true;
    const record={client:c.u.uid,phaseIndex:p.phaseIndex,intent:action,startedAt:Date.now()}; records.push(record);
    const work=(async()=>{
      try {await callable(c.u,"accionPartidaV3",{roomId:room,matchId,phaseIndex:p.phaseIndex,requestId:randomUUID(),...action});record.accepted=true;}
      catch(e){record.accepted=false;record.error=e.reason||"request-failed";}
      finally {
        record.receiptAt=Date.now();c.busy=false;observe(c);
        onProgress({kind:"action-receipt",clientIndex:contexts.indexOf(c),phaseIndex:record.phaseIndex,
          accepted:record.accepted,error:record.error,durationMs:record.receiptAt-record.startedAt,
          observedAt:record.receiptAt});
      }
    })();addWork(work);
  }
  async function chat(c) {
    const p=c.publicState, channel=channels(c);
    if(p?.fase!=="DIA_DEBATE" || !channel || !c.own || p.phaseIndex!==c.own.phaseIndex || p.phaseIndex!==c.permission.phaseIndex || Date.now() >= p.limiteFaseEpochMs-3000) return;
    const key=`${p.phaseIndex}:chat`;
    if(c.keys.has(key)) return;c.keys.add(key);
    const slot=c.chatSlots++%16,id=`${c.u.uid}_${slot}`,root=ref(getDatabase(c.u.app),`onlineV3/${room}`),ts=serverTimestamp();
    try {
      await update(root,{[`chat/${channel}/${id}`]:{actorUid:c.u.uid,matchId,phaseIndex:p.phaseIndex,slot,text:"Mensaje de prueba de consumo V3.",ts},
        [`chatRate/${c.u.uid}`]:{messageId:id,channel,slot,ts}});
    } catch(e){errors.push({kind:"chat-send",code:e.code});}
  }
  try {
    for(const c of contexts) for(const [kind,path] of [["public","public"],["private",`private/${c.u.uid}`],["permissions",`permissions/${c.u.uid}`]]) {
      stops.push(onValue(ref(getDatabase(c.u.app),`onlineV3/${room}/snapshot/${path}`),s=>{
        const value=s.val();countValue(kind,value);
        if(kind==="public")c.publicState=value;else if(kind==="private")c.own=value;else c.permission=value;
        observe(c);
      },e=>errors.push({kind:`${kind}-listener`,code:e.code})));
    }
    const until=Date.now()+maxDurationMs;
    while(!finished) {
      signal.throwIfAborted();assert.ok(Date.now()<until,"Cloud QA exceeded its bounded match duration");
      assert.equal(errors.filter(e=>e.kind.endsWith("listener")).length,0,"A client lost projection access");
      for(const c of contexts) if(c.permission && !c.busy) {
        if(readyVoteDelayMs!==null)observe(c);
        if(!c.busy)addWork(chat(c));
      }
      await pause(300);
    }
    assert.equal(finished.jugadores.filter(p=>p.causaEliminacion==="AFK").length,0,"Autonomous QA clients unexpectedly eliminated by AFK");
  } finally {
    stops.forEach(stop=>stop());contexts.forEach(c=>c.chatStop?.());
    await Promise.allSettled([...pending]);
  }
  const receipts=records.filter(r=>r.accepted).map(r=>r.receiptAt-r.startedAt);
  const projections=records.filter(r=>r.accepted&&r.projectionAt).map(r=>r.projectionAt-r.startedAt);
  return {players:users.length,environment:"real-cloud-web-sdk",winner:finished.ganador,rounds:finished.ronda,durationMs:Date.now()-began,
    actions:records.length,accepted:receipts.length,rejected:records.filter(r=>!r.accepted).map(r=>({phaseIndex:r.phaseIndex,reason:r.error})),
    actionReceiptMs:{p50:percentile(receipts,.5),p95:percentile(receipts,.95),max:Math.max(0,...receipts)},
    actionProjectionMs:{observed:projections.length,p50:percentile(projections,.5),p95:percentile(projections,.95),max:Math.max(0,...projections)},
    phases:[...phases.values()],sdkValues:stats,chatSent:contexts.reduce((n,c)=>n+c.chatSlots,0)-errors.filter(e=>e.kind==="chat-send").length,chatErrors:errors,
    limitations:["SDK JSON callbacks are not billed network bytes","Cloud Monitoring counters are shared with the rest of the project","Setup, inspection, history verification and cleanup use separate QA operations"]};
}
module.exports={measureCloudMatch};
