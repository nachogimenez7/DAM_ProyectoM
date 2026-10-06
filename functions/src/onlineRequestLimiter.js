"use strict";
const {createHash} = require("node:crypto");
const {Timestamp} = require("firebase-admin/firestore");
const {HttpsError} = require("firebase-functions/v2/https");

const CAPACITY = 8;
const REFILL_MS = 2000; // Eight-request burst, then at most 30/minute per authenticated UID.
function requestLimitId(uid) { return createHash("sha256").update(uid).digest("hex"); }
async function enforceRequestLimit({firestore, uid, nowMs = Date.now()}) {
  const ref = firestore.doc(`onlineRequestLimits/${requestLimitId(uid)}`);
  const result = await firestore.runTransaction(async (tx) => {
    const old = (await tx.get(ref)).data();
    const tokens = old ? Math.min(CAPACITY, old.tokens + Math.max(0, nowMs - old.atMs) / REFILL_MS) : CAPACITY;
    if (tokens < 1) return {allowed: false, retryAfterMs: Math.ceil((1 - tokens) * REFILL_MS)};
    tx.set(ref, {tokens: tokens - 1, atMs: nowMs, expiresAt: Timestamp.fromMillis(nowMs + 30 * 86400000)});
    return {allowed: true};
  });
  if (!result.allowed) throw new HttpsError("resource-exhausted", "Esperá un momento antes de volver a intentar.",
    {reason: "request-rate-limit", retryAfterMs: result.retryAfterMs});
}
module.exports = {enforceRequestLimit, requestLimitId, CAPACITY, REFILL_MS};
