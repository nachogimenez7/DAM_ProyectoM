// Purchase entitlements: only the owner reads them, nobody writes them from a client.
const fs = require("fs");
const {initializeTestEnvironment, assertSucceeds, assertFails} = require("@firebase/rules-unit-testing");
const {doc, getDoc, setDoc} = require("firebase/firestore");

(async () => {
  const env = await initializeTestEnvironment({
    projectId: "demo-traidores-purchases",
    firestore: {rules: fs.readFileSync("firestore.rules", "utf8"),
      host: "127.0.0.1", port: Number((process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8080").split(":")[1])},
  });
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "derechos/ana"), {items: ["sin_anuncios"]});
    await setDoc(doc(ctx.firestore(), "compras/t1"), {uid: "ana", productId: "sin_anuncios"});
  });
  const google = {firebase: {sign_in_provider: "google.com"}, email: "ana@example.test"};
  const ana = env.authenticatedContext("ana", google).firestore();
  const beto = env.authenticatedContext("beto", {...google, email: "beto@example.test"}).firestore();
  const guest = env.authenticatedContext("ana", {firebase: {sign_in_provider: "anonymous"}}).firestore();
  const checks = [
    ["owner reads own entitlements", assertSucceeds(getDoc(doc(ana, "derechos/ana")))],
    ["owner cannot grant herself items", assertFails(setDoc(doc(ana, "derechos/ana"), {items: ["pack"]}))],
    ["another account cannot read them", assertFails(getDoc(doc(beto, "derechos/ana")))],
    ["a guest session cannot read them", assertFails(getDoc(doc(guest, "derechos/ana")))],
    ["purchase records are private", assertFails(getDoc(doc(ana, "compras/t1")))],
    ["clients cannot write purchase records", assertFails(setDoc(doc(ana, "compras/t2"), {uid: "ana"}))],
  ];
  for (const [name, check] of checks) { await check; console.log(`ok - ${name}`); }
  await env.cleanup();
})().catch((error) => { console.error(error); process.exit(1); });
