const fs = require("fs");
const path = require("path");
const { spawnSync } = require("child_process");
const { initializeApp, deleteApp } = require("firebase/app");
const { getAuth, signInAnonymously } = require("firebase/auth");
const {
  collection,
  getDocs,
  getFirestore,
  orderBy,
  query,
  where,
} = require("firebase/firestore");

const PROJECT_ID = "traidores";
const DATABASE_INSTANCE = "traidores-default-rtdb";
const STALE_AFTER_MS = 24 * 60 * 60 * 1000;
const apply = process.argv.includes("--apply");

function firebaseConfig() {
  const services = JSON.parse(fs.readFileSync("app/google-services.json", "utf8"));
  const client = services.client.find(
    (item) => item.client_info.android_client_info.package_name === "com.traidores.juego"
  );
  if (!client) throw new Error("No se encontró la configuración Android de Traidores.");
  return {
    apiKey: client.api_key[0].current_key,
    appId: client.client_info.mobilesdk_app_id,
    projectId: services.project_info.project_id,
  };
}

function runFirebase(args) {
  const cli = path.resolve("node_modules/firebase-tools/lib/bin/firebase.js");
  const result = spawnSync(process.execPath, [cli, ...args], {
    cwd: process.cwd(),
    encoding: "utf8",
    stdio: "inherit",
  });
  if (result.status !== 0) {
    throw new Error(`Firebase CLI terminó con código ${result.status}: ${args[0]}`);
  }
}

function safeRoom(document) {
  const data = document.data();
  const code = String(data.codigoSala || "");
  if (!/^[A-Za-z0-9]{10,80}$/.test(document.id)) return null;
  if (code && !/^[A-Z2-9]{6}$/.test(code)) return null;
  return {
    id: document.id,
    code,
    name: String(data.nombre || "Sala sin nombre").slice(0, 40),
    updatedAt: data.actualizadaEn?.toDate?.() || null,
  };
}

async function main() {
  if (apply && process.env.TRAIDORES_REAL_FIREBASE_CONFIRM !== PROJECT_ID) {
    throw new Error(
      "Protección activa: define TRAIDORES_REAL_FIREBASE_CONFIRM=traidores para borrar."
    );
  }
  const app = initializeApp(firebaseConfig(), `stale-room-cleanup-${Date.now()}`);
  await signInAnonymously(getAuth(app));
  const cutoff = new Date(Date.now() - STALE_AFTER_MS);
  const snapshot = await getDocs(query(
    collection(getFirestore(app), "partidas"),
    where("estado", "==", "esperando"),
    where("visibilidad", "==", "publica"),
    where("actualizadaEn", "<", cutoff),
    orderBy("actualizadaEn", "desc")
  ));
  const rooms = snapshot.docs.map(safeRoom).filter(Boolean);
  process.stdout.write(
    `${rooms.length} sala(s) pública(s) vencida(s) antes de ${cutoff.toISOString()}.\n`
  );
  rooms.forEach((room) => {
    process.stdout.write(`- ${room.name} · ${room.updatedAt?.toISOString?.() || "sin fecha"}\n`);
  });
  if (!apply || rooms.length === 0) {
    if (!apply && rooms.length > 0) {
      process.stdout.write("Vista previa: no se borró nada. Usa --apply con la confirmación.\n");
    }
    await deleteApp(app);
    return;
  }
  await deleteApp(app);
  for (const room of rooms) {
    if (room.code) {
      runFirebase([
        "firestore:delete",
        `codigosSala/${room.code}`,
        "--shallow",
        "--force",
        "--project",
        PROJECT_ID,
      ]);
    }
    runFirebase([
      "database:remove",
      `/salas/${room.id}`,
      "--force",
      "--project",
      PROJECT_ID,
      "--instance",
      DATABASE_INSTANCE,
    ]);
    runFirebase([
      "firestore:delete",
      `partidas/${room.id}`,
      "--recursive",
      "--force",
      "--project",
      PROJECT_ID,
    ]);
  }
  process.stdout.write(`Limpieza completa: ${rooms.length} sala(s).\n`);
}

main().catch((error) => {
  process.stderr.write(`${error.message}\n`);
  process.exitCode = 1;
});
