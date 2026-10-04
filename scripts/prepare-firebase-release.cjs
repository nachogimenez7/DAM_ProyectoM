// Default: read-only preflight. --apply deploys this backend, then its rules;
// it never creates a billing account, enables Blaze or creates a Storage bucket.
"use strict";
const fs = require("node:fs");
const path = require("node:path");
const {spawnSync} = require("node:child_process");
const auth = require("firebase-tools/lib/auth");
const api = require("firebase-tools/lib/api");

const root = path.resolve(__dirname, "..");
const project = "traidores";
const region = "southamerica-west1";
const names = ["iniciarPartidaV2", "limpiarSalasAbandonadasV1", "registrarSalaHuerfanaV1",
  "programarLimpiezaSalaV2", "guardarHistorialOnlineV1", "contarPartidaLocalV1", "borrarHistorialCuentaV1"];
const args = process.argv.slice(2);
if (args.some(arg => !["--apply", "--storage"].includes(arg))) {
  console.error("Uso: node scripts/prepare-firebase-release.cjs [--apply] [--storage]");
  process.exit(1);
}
process.chdir(root);

async function main() {
  const config = JSON.parse(fs.readFileSync("app/google-services.json", "utf8")).project_info;
  const projects = JSON.parse(fs.readFileSync(".firebaserc", "utf8")).projects;
  if (config.project_id !== project || projects.default !== project) throw new Error("Proyecto local inesperado.");
  const account = auth.getProjectDefaultAccount(root);
  if (!account) throw new Error("Falta iniciar sesión en Firebase CLI.");
  api.setScopes(["https://www.googleapis.com/auth/cloud-platform"]);
  const credentials = await auth.getAccessToken(account.tokens.refresh_token, api.getScopes());
  async function read(url) {
    const response = await fetch(url, {headers: {Authorization: `Bearer ${credentials.access_token}`},
      signal: AbortSignal.timeout(30000)});
    if (!response.ok) throw new Error(`Consulta remota rechazada (${response.status}). No se modificó el proyecto.`);
    return response.json();
  }
  const [billing, databases] = await Promise.all([
    read(`https://cloudbilling.googleapis.com/v1/projects/${project}/billingInfo`),
    read(`https://firestore.googleapis.com/v1/projects/${project}/databases`),
  ]);
  const database = databases.databases?.find(value => value.name.endsWith("/(default)"));
  if (database?.locationId !== region) throw new Error("La región de Firestore no coincide con el backend.");
  if (args.includes("--storage")) {
    const bucket = await read(`https://storage.googleapis.com/storage/v1/b/${config.storage_bucket}`);
    if (bucket.projectNumber !== String(config.project_number)) throw new Error("El bucket no pertenece a este proyecto.");
    console.log(`Storage existente: ${bucket.name}, región ${bucket.location}.`);
  }
  const cli = require.resolve("firebase-tools/lib/bin/firebase.js");
  const stages = [
    ["deploy", "--project", project, "--only", names.map(name => `functions:${name}`).join(",")],
    ["deploy", "--project", project, "--only", "firestore:rules,firestore:indexes"],
  ];
  if (args.includes("--storage")) stages.push(["deploy", "--project", project, "--only", "storage"]);
  console.log(`Proyecto ${project}; Firestore ${database.locationId}; facturación ${billing.billingEnabled ? "activa" : "inactiva (Spark)"}.`);
  console.log("Orden: backend confirmado → reglas → Storage (solo con --storage y bucket existente).");
  console.log("La app de producción debe incluir los payloads nuevos que reservan las estadísticas al servidor.");
  for (const stage of stages) console.log(`firebase ${stage.join(" ")}`);
  if (!args.includes("--apply")) {
    console.log("Consulta terminada: no se desplegó nada. iOS aún requiere historial y fotos; su gameplay online sigue pendiente.");
    return;
  }
  if (!billing.billingEnabled) throw new Error("Blaze debe activarse antes del despliegue. No se modificó el proyecto.");
  for (const stage of stages) {
    const result = spawnSync(process.execPath, [cli, ...stage, "--non-interactive"], {cwd: root, stdio: "inherit"});
    if (result.error || result.status !== 0) throw new Error("Despliegue incompleto. Se detuvo antes de la siguiente etapa; revisar la salida del CLI.");
  }
  console.log("Despliegue terminado. Falta la aceptación con dos cuentas en apps sin emuladores.");
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
