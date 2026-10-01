// Read-only monitoring: never deploys, enables billing or reads player content.
"use strict";
const fs = require("node:fs");
const auth = require("firebase-tools/lib/auth");
const api = require("firebase-tools/lib/api");

async function main() {
  const account = auth.getProjectDefaultAccount(process.cwd());
  if (!account) throw new Error("Firebase CLI login required");
  api.setScopes(["https://www.googleapis.com/auth/cloud-platform"]);
  const credentials = await auth.getAccessToken(account.tokens.refresh_token, api.getScopes());
  const headers = { Authorization: `Bearer ${credentials.access_token}` };
  async function get(origin, path, query = {}) {
    const url = new URL(path, origin);
    for (const [key, value] of Object.entries(query)) url.searchParams.set(key, value);
    const response = await fetch(url, { headers, signal: AbortSignal.timeout(30000) });
    const body = await response.json();
    if (!response.ok) return { unavailable: true, status: response.status, reason: body.error?.status || "API_ERROR" };
    return body;
  }
  const project = "traidores";
  const result = { project, capturedAt: new Date().toISOString(), readOnly: true };
  const jobs = [
    ["billing", "https://cloudbilling.googleapis.com", `/v1/projects/${project}/billingInfo`],
    ["projectStatus", "https://firebase.googleapis.com", `/v1beta1/projects/${project}`],
    ["databases", "https://firestore.googleapis.com", `/v1/projects/${project}/databases`],
    ["functions", "https://cloudfunctions.googleapis.com", `/v2/projects/${project}/locations/-/functions`],
    ["rtdbMetrics", "https://monitoring.googleapis.com", `/v3/projects/${project}/metricDescriptors`, { filter: 'metric.type = starts_with("firebasedatabase.googleapis.com/")', pageSize: "1000" }],
    ["firestoreMetrics", "https://monitoring.googleapis.com", `/v3/projects/${project}/metricDescriptors`, { filter: 'metric.type = starts_with("firestore.googleapis.com/")', pageSize: "1000" }],
  ];
  await Promise.all(jobs.map(async ([name, origin, path, query]) => {
    const data = await get(origin, path, query);
    if (data.unavailable) result[name] = data;
    else if (name === "billing") result[name] = { billingEnabled: data.billingEnabled === true };
    else if (name === "projectStatus") result[name] = { state: data.state };
    else if (name === "databases") result[name] = (data.databases || []).map(d => ({ location: d.locationId, type: d.type, edition: d.databaseEdition }));
    else if (name === "functions") result[name] = { count: (data.functions || []).length, unreachable: data.unreachable || [] };
    else result[name] = (data.metricDescriptors || []).map(d => ({ type: d.type, kind: d.metricKind, unit: d.unit }));
  }));
  const end = new Date();
  const selected = [
    ["rtdbConnections", "firebasedatabase.googleapis.com/network/active_connections", "GAUGE"],
    ["rtdbMonthlyBytes", "firebasedatabase.googleapis.com/network/monthly_sent", "GAUGE"],
    ["rtdbMonthlyLimitBytes", "firebasedatabase.googleapis.com/network/monthly_sent_limit", "GAUGE"],
    ["rtdbStorageBytes", "firebasedatabase.googleapis.com/storage/total_bytes", "GAUGE"],
    ["rtdbDisabledForOverages", "firebasedatabase.googleapis.com/network/disabled_for_overages", "BOOL"],
    ["rtdbSentBytes", "firebasedatabase.googleapis.com/network/sent_bytes_count", "DELTA"],
    ["firestoreReads", "firestore.googleapis.com/document/read_ops_count", "DELTA"],
    ["firestoreWrites", "firestore.googleapis.com/document/write_ops_count", "DELTA"],
    ["firestoreLegacyReads", "firestore.googleapis.com/document/read_count", "DELTA"],
    ["firestoreLegacyWrites", "firestore.googleapis.com/document/write_count", "DELTA"],
  ];
  result.metrics = {};
  await Promise.all(selected.map(async ([name, type, kind]) => {
    const windows = kind !== "DELTA" ? [["last7Days", new Date(end - 7 * 86400000)]] : [
      ["last24Hours", new Date(end - 86400000)],
      ["monthToDateUTC", new Date(Date.UTC(end.getUTCFullYear(), end.getUTCMonth(), 1))],
    ];
    result.metrics[name] = {};
    await Promise.all(windows.map(async ([window, start]) => {
      const query = { filter: `metric.type = "${type}"`, "interval.startTime": start.toISOString(), "interval.endTime": end.toISOString(),
        "aggregation.alignmentPeriod": "60s", "aggregation.perSeriesAligner": kind === "BOOL" ? "ALIGN_NEXT_OLDER" : kind === "GAUGE" ? "ALIGN_MAX" : "ALIGN_SUM",
        "aggregation.crossSeriesReducer": kind === "BOOL" ? "REDUCE_COUNT_TRUE" : "REDUCE_SUM", pageSize: "100000" };
      let pageToken;
      const points = [];
      do {
        const data = await get("https://monitoring.googleapis.com", `/v3/projects/${project}/timeSeries`, { ...query, ...(pageToken ? { pageToken } : {}) });
        if (data.unavailable) { result.metrics[name][window] = data; return; }
        for (const series of data.timeSeries || []) for (const p of series.points || []) {
          const value = Number(p.value.int64Value ?? p.value.doubleValue);
          if (Number.isFinite(value)) points.push({ time: p.interval.endTime, value });
        }
        pageToken = data.nextPageToken;
      } while (pageToken);
      points.sort((a, b) => a.time.localeCompare(b.time));
      result.metrics[name][window] = { start: start.toISOString(), end: end.toISOString(), samples: points.length,
        ...(points.length ? { ...(kind !== "DELTA" ? { latest: points.at(-1), peak: Math.max(...points.map(p => p.value)) } : { sum: points.reduce((n, p) => n + p.value, 0) }), latestSampleAt: points.at(-1).time } : { unavailable: true, reason: "NO_SAMPLES" }) };
    }));
  }));
  fs.mkdirSync("output/firebase-capacity", { recursive: true });
  const snapshot = JSON.stringify(result, null, 2);
  fs.writeFileSync("output/firebase-capacity/snapshot.json", snapshot);
  fs.writeFileSync(`output/firebase-capacity/snapshot-${result.capturedAt.replace(/[:.]/g, "-")}.json`, snapshot);
  console.log(JSON.stringify({ project, capturedAt: result.capturedAt, billing: result.billing, databases: result.databases, functions: result.functions, metrics: result.metrics }, null, 2));
}
main().catch(() => { console.error("Snapshot failed: authentication or network unavailable; no changes made."); process.exitCode = 1; });
