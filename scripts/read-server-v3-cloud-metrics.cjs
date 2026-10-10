"use strict";
// Read-only Cloud Monitoring/Logging evidence. Does not change rollout or fixtures.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const {cloudAccess} = require("./v3-cloud-access.cjs");
const metrics = [
  "firestore.googleapis.com/document/read_ops_count",
  "firestore.googleapis.com/document/write_ops_count",
  "firebasedatabase.googleapis.com/network/sent_bytes_count",
  "firebasedatabase.googleapis.com/network/sent_payload_bytes_count",
  "firebasedatabase.googleapis.com/network/active_connections",
  "firebasedatabase.googleapis.com/io/database_load",
  "firebasedatabase.googleapis.com/storage/total_bytes",
  "run.googleapis.com/request_count",
  "run.googleapis.com/container/billable_instance_time"
];
const option = name => {
  const index = process.argv.indexOf(name);
  return index < 0 ? null : process.argv[index + 1];
};
async function collect() {
  const from = option("--from"), to = option("--to"), out = option("--out");
  assert.ok(from && to && Number.isFinite(Date.parse(from)) && Date.parse(from) < Date.parse(to),
    "Required: --from ISO --to ISO [--room ID] --out output/NAME.json");
  const output = out && path.resolve(out);
  assert.ok(output?.startsWith(path.resolve("output") + path.sep), "Output must be under output/");
  const room = option("--room"), {api} = await cloudAccess();
  async function series(type) {
    const rows = []; let pageToken;
    do {
      const query = new URLSearchParams({filter:`metric.type="${type}"`,
        "interval.startTime":from,"interval.endTime":to,view:"FULL",pageSize:"1000"});
      if (pageToken) query.set("pageToken",pageToken);
      const page = await api("https://monitoring.googleapis.com/v3/projects/traidores/timeSeries?" + query);
      rows.push(...(page.timeSeries || [])); pageToken = page.nextPageToken;
    } while (pageToken);
    const kind = rows[0]?.metricKind;
    const value = p => Number(p.value.int64Value ?? p.value.doubleValue ?? 0);
    if (kind === "GAUGE") {
      const byTime = new Map();
      for (const row of rows) for (const p of row.points)
        byTime.set(p.interval.endTime,(byTime.get(p.interval.endTime) || 0) + value(p));
      const samples = [...byTime.entries()].sort(([a],[b])=>a.localeCompare(b));
      return {type,kind,series:rows,peak:samples.length ? Math.max(...samples.map(([,v])=>v)) : null,
        last:samples.at(-1)?.[1] ?? null};
    }
    return {type,kind,series:rows,total:rows.length ? rows.reduce((sum,row) => sum + row.points.reduce((n,p) => n + value(p),0),0) : null};
  }
  async function logs() {
    const rows = []; let pageToken;
    const scope = room ? `jsonPayload.roomId="${room}"` : 'jsonPayload.message="online_v3_recovery"';
    // Restrict the input to a literal identifier, not an arbitrary Logging filter.
    assert.ok(!room || /^[a-zA-Z0-9_-]+$/.test(room), "Invalid room identifier");
    do {
      const page = await api("https://logging.googleapis.com/v2/entries:list",{method:"POST",body:JSON.stringify({
        resourceNames:["projects/traidores"],
        filter:`resource.type="cloud_run_revision" AND ${scope} AND timestamp>="${from}" AND timestamp<="${to}"`,
        orderBy:"timestamp asc",pageSize:1000,...(pageToken ? {pageToken} : {})})});
      for (const e of page.entries || []) {
        const p = e.jsonPayload || {};
        rows.push({timestamp:e.timestamp,service:e.resource.labels.service_name,message:p.message,
          operation:p.operation,durationMs:p.durationMs,transactionAttempts:p.transactionAttempts,
          changed:p.changed,published:p.published,changedPublic:p.changedPublic,changedPrivate:p.changedPrivate,
          reusedDelivery:p.reusedDelivery,realtimeAttempts:p.realtimeAttempts,
          projectionBytes:p.projectionBytes,scanned:p.scanned,repaired:p.repaired,failed:p.failed,hasMore:p.hasMore});
      }
      pageToken = page.nextPageToken;
    } while (pageToken);
    return rows;
  }
  const results = await Promise.all(metrics.map(series));
  const report = {from,to,collectedAt:new Date().toISOString(),room,metrics:results,logs:await logs(),
    limitations:["Project counters include QA setup, cleanup, background work and other users",
      "Firestore successful-operation metrics do not include every billable read, including empty-query minimums",
      "Minute-sized points may overlap the interval boundaries; ingestion is delayed",
      "Realtime Database sent bytes include protocol and encryption overhead; this is not a per-room counter",
      "Cloud Run billable instance seconds are not the sum of client request durations"]};
  fs.mkdirSync(path.dirname(output),{recursive:true});
  fs.writeFileSync(output,JSON.stringify(report,null,2));
  console.log(JSON.stringify({output:out,metrics:results.map(r=>({type:r.type,total:r.total,peak:r.peak,series:r.series.length})),logs:report.logs.length}));
  return report;
}
if (require.main === module) collect().catch(e => {console.error(e.message);process.exitCode=1;});
module.exports = {collect};
