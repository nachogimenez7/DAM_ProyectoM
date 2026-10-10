"use strict";

// An authoritative, small read: SDK get() can fall back to a local cache that
// includes an uncommitted concurrent transaction. Such a value must never be
// used to acknowledge the durable outbox without actually publishing it.
async function readServerDelivery({database, realtime, fetchImpl = fetch}) {
  const url = new URL(realtime.child("delivery").toString());
  url.pathname += ".json";
  const emulator = process.env.FIREBASE_DATABASE_EMULATOR_HOST;
  let token;
  if (emulator) {
    if (url.host !== emulator || url.protocol !== "http:") throw new Error("delivery-emulator-mismatch");
    const configured = new URL(database.app.options.databaseURL);
    url.searchParams.set("ns", configured.searchParams.get("ns") || configured.hostname.split(".")[0]);
    token = "owner";
  } else {
    if (url.protocol !== "https:" || !/\.(firebaseio\.com|firebasedatabase\.app)$/.test(url.hostname)) {
      throw new Error("invalid-delivery-origin");
    }
    token = (await database.app.options.credential.getAccessToken()).access_token;
  }
  if (!token) throw new Error("missing-delivery-credential");
  const response = await fetchImpl(url, {headers: {Authorization: `Bearer ${token}`},
    redirect: "error", signal: AbortSignal.timeout(10000)});
  if (!response.ok) {
    const error = new Error("delivery-check-unavailable");
    error.code = "delivery-check-unavailable";
    throw error; // Do not log the response body, URL or credentials.
  }
  const value = await response.json();
  if (value !== null && (typeof value !== "object" || Array.isArray(value) ||
      typeof value.matchId !== "string" || !value.matchId.length || value.matchId.length > 128 ||
      !Number.isSafeInteger(value.generation) || value.generation < 1 ||
      !Number.isSafeInteger(value.revision) || value.revision < 1 ||
      value.queuedTaskId != null && typeof value.queuedTaskId !== "string")) {
    throw new Error("invalid-delivery-checkpoint");
  }
  return value;
}

module.exports = {readServerDelivery};
