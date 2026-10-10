"use strict";

// Permanent cosmetic purchases (Google Play, non-consumable). The phone never grants anything:
// it sends the purchase token, the server verifies it with Google, records it and derives the
// account's entitlements from every active purchase. Refunds and chargebacks are revoked by
// rebuilding from the purchases that remain active.
const crypto = require("node:crypto");

const PACKAGE_NAME = "com.traidores.juego";

// Product id (Play Console) -> entitlement keys. Kept in sync with PurchaseCatalog.kt.
const PACK_ITEMS = ["sin_anuncios", "estilo_sello", "marco_sello", "insignia_sello", "banners_pack",
  "emotes_pack", "emote_brindis", "placa_sello", "burbujas_sello", "festejo_sello"];
const CATALOG = Object.freeze({
  sin_anuncios: ["sin_anuncios"],
  pack_sello_pueblo: PACK_ITEMS,
  estilo_espacial: ["estilo_espacial"],
  estilo_abismo_real: ["estilo_abismo_real"],
  estilo_forja_infernal: ["estilo_forja_infernal"],
  estilos_tres: ["estilo_espacial", "estilo_abismo_real", "estilo_forja_infernal"],
  emotes_memes: ["emotes_memes"],
});

class PurchaseError extends Error {
  constructor(code, message) { super(message); this.code = code; }
}

/** Binds a Play purchase to one Firebase account without sending the uid to Google. */
function accountHash(uid) {
  return crypto.createHash("sha256").update(`traidores:${uid}`).digest("hex");
}

function tokenId(purchaseToken) {
  return crypto.createHash("sha256").update(purchaseToken).digest("hex");
}

function entitlementsFor(productIds) {
  return [...new Set(productIds.flatMap((id) => CATALOG[id] || []))].sort();
}

/** Pure check of Google's answer (purchases.products.get). */
function evaluatePurchase(purchase, uid) {
  if (!purchase) throw new PurchaseError("not-found", "Google no encontró la compra.");
  if (purchase.purchaseState === 2) throw new PurchaseError("pending", "El pago todavía está pendiente.");
  if (purchase.purchaseState !== 0) throw new PurchaseError("not-purchased", "La compra fue cancelada.");
  if (purchase.obfuscatedExternalAccountId && purchase.obfuscatedExternalAccountId !== accountHash(uid)) {
    throw new PurchaseError("other-account", "Esta compra pertenece a otra cuenta de Traidores.");
  }
  return {orderId: purchase.orderId || "", needsAck: purchase.acknowledgementState === 0};
}

async function rebuildEntitlements(firestore, tx, uid, extraProductId) {
  const active = await tx.get(firestore.collection("compras").where("uid", "==", uid).where("estado", "==", "activa"));
  const products = active.docs.map((doc) => doc.get("productId"));
  if (extraProductId) products.push(extraProductId);
  return entitlementsFor(products);
}

/**
 * Verifies one purchase and grants it once. Idempotent: restoring the same token again only
 * returns the current entitlements. A token already bound to another account is refused.
 */
async function verifyAndGrant({firestore, playApi, uid, productId, purchaseToken, nowMs, FieldValue}) {
  if (!uid) throw new PurchaseError("unauthenticated", "Iniciá sesión con tu cuenta para comprar.");
  if (!Object.prototype.hasOwnProperty.call(CATALOG, productId)) {
    throw new PurchaseError("unknown-product", "Ese producto no existe.");
  }
  if (typeof purchaseToken !== "string" || purchaseToken.length < 10 || purchaseToken.length > 4096) {
    throw new PurchaseError("invalid-token", "Compra inválida.");
  }
  const purchase = await playApi.getProduct(productId, purchaseToken);
  const {orderId, needsAck} = evaluatePurchase(purchase, uid);
  const purchaseRef = firestore.collection("compras").doc(tokenId(purchaseToken));
  const rightsRef = firestore.collection("derechos").doc(uid);
  const items = await firestore.runTransaction(async (tx) => {
    const existing = await tx.get(purchaseRef);
    if (existing.exists && existing.get("uid") !== uid) {
      throw new PurchaseError("other-account", "Esta compra pertenece a otra cuenta de Traidores.");
    }
    if (existing.exists && existing.get("estado") !== "activa") {
      throw new PurchaseError("revoked", "Esta compra fue reembolsada o anulada.");
    }
    const granted = await rebuildEntitlements(firestore, tx, uid, existing.exists ? null : productId);
    if (!existing.exists) {
      tx.set(purchaseRef, {uid, productId, orderId, estado: "activa", otorgadaEnMs: nowMs,
        otorgadaEn: FieldValue.serverTimestamp()});
    }
    tx.set(rightsRef, {schemaVersion: 1, items: granted, actualizadoEn: FieldValue.serverTimestamp()});
    return granted;
  });
  // Acknowledge after recording: an unacknowledged purchase is refunded by Google in 3 days.
  if (needsAck) await playApi.acknowledge(productId, purchaseToken);
  return {items};
}

/** Marks refunded/charged-back purchases and rebuilds the affected accounts' entitlements. */
async function revokeVoided({firestore, playApi, sinceMs, FieldValue}) {
  const voided = await playApi.listVoided(sinceMs);
  let revoked = 0;
  for (const entry of voided) {
    if (!entry.purchaseToken) continue;
    const ref = firestore.collection("compras").doc(tokenId(entry.purchaseToken));
    await firestore.runTransaction(async (tx) => {
      const doc = await tx.get(ref);
      if (!doc.exists || doc.get("estado") !== "activa") return;
      const uid = doc.get("uid");
      // Firestore transactions read everything before the first write.
      const active = await tx.get(firestore.collection("compras").where("uid", "==", uid).where("estado", "==", "activa"));
      const products = active.docs.filter((d) => d.id !== ref.id).map((d) => d.get("productId"));
      tx.update(ref, {estado: "anulada", anuladaEn: FieldValue.serverTimestamp()});
      tx.set(firestore.collection("derechos").doc(uid),
        {schemaVersion: 1, items: entitlementsFor(products), actualizadoEn: FieldValue.serverTimestamp()});
      revoked += 1;
    });
  }
  return {checked: voided.length, revoked};
}

/** Google Play Developer API over REST, with the function's own service account. */
function createPlayApi({auth, packageName = PACKAGE_NAME, fetchImpl = fetch}) {
  const base = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${packageName}`;
  async function call(path, init = {}) {
    const client = await auth.getClient();
    const {token} = await client.getAccessToken();
    const response = await fetchImpl(`${base}${path}`, {...init, headers: {Authorization: `Bearer ${token}`}});
    if (response.status === 404 || response.status === 410) return null;
    if (!response.ok) throw new PurchaseError("play-unavailable", `Google Play respondió ${response.status}.`);
    const text = await response.text();
    return text ? JSON.parse(text) : {};
  }
  const enc = encodeURIComponent;
  return {
    getProduct: (productId, token) => call(`/purchases/products/${enc(productId)}/tokens/${enc(token)}`),
    acknowledge: (productId, token) =>
      call(`/purchases/products/${enc(productId)}/tokens/${enc(token)}:acknowledge`, {method: "POST"}),
    listVoided: async (sinceMs) => {
      const out = [];
      let pageToken = "";
      do {
        const query = `?startTime=${sinceMs}&maxResults=1000${pageToken ? `&token=${enc(pageToken)}` : ""}`;
        const page = await call(`/purchases/voidedpurchases${query}`) || {};
        out.push(...(page.voidedPurchases || []));
        pageToken = page.tokenPagination && page.tokenPagination.nextPageToken || "";
      } while (pageToken);
      return out;
    },
  };
}

module.exports = {CATALOG, PACKAGE_NAME, PurchaseError, accountHash, tokenId, entitlementsFor,
  evaluatePurchase, verifyAndGrant, revokeVoided, createPlayApi};
