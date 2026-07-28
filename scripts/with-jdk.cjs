/**
 * Corre el comando que se le pase con un JDK 17 o superior.
 *
 * El emulador de Firestore no arranca con Java 8, y en Windows es habitual tener justo esa
 * version en el PATH. En vez de pedir que cada uno exporte `JAVA_HOME` a mano, se busca el JDK
 * que ya viene con Android Studio, que es requisito del proyecto igual.
 *
 * `JAVA_HOME` se respeta solamente si apunta a una versión compatible. Esto evita que una
 * instalación vieja de Java 8 gane por estar primera en el entorno.
 */
const { spawn, spawnSync } = require("child_process");
const fs = require("fs");
const path = require("path");

const CANDIDATES = [
  process.env.JAVA_HOME,
  "C:\\Program Files\\Android\\Android Studio\\jbr",
  "C:\\Program Files\\Android\\Android Studio1\\jbr",
  path.join(process.env.LOCALAPPDATA || "", "Programs", "Android Studio", "jbr"),
  "/Applications/Android Studio.app/Contents/jbr/Contents/Home",
  "/usr/lib/jvm/default-java",
];

const MINIMUM_JAVA_MAJOR = 17;

function javaMajor(binary) {
  const result = spawnSync(binary, ["-version"], { encoding: "utf8" });
  const output = `${result.stdout || ""}\n${result.stderr || ""}`;
  const match = output.match(/version\s+"(?:1\.)?(\d+)/i);
  return match ? Number.parseInt(match[1], 10) : 0;
}

function findJdk() {
  for (const candidate of CANDIDATES) {
    if (!candidate) continue;
    const binary = path.join(candidate, "bin", process.platform === "win32" ? "java.exe" : "java");
    if (fs.existsSync(binary) && javaMajor(binary) >= MINIMUM_JAVA_MAJOR) return candidate;
  }
  return null;
}

const args = process.argv.slice(2);
if (args.length === 0) {
  console.error("Uso: node scripts/with-jdk.cjs <comando> [args...]");
  process.exit(1);
}

const jdk = findJdk();
if (!jdk) {
  console.error(
    "No se encontro un JDK 17+. Instala Android Studio o defini JAVA_HOME apuntando a uno."
  );
  process.exit(1);
}

const env = { ...process.env, JAVA_HOME: jdk };
env.PATH = [
  path.dirname(process.execPath),
  path.join(jdk, "bin"),
  env.PATH,
].filter(Boolean).join(path.delimiter);

/**
 * Sin `shell: true` a proposito. Con shell, Windows vuelve a partir los argumentos y el
 * comando entrecomillado que recibe `emulators:exec` pierde las comillas: se rompe en dos
 * argumentos y el emulador no ejecuta nada. Por eso `firebase` se resuelve a su entrada JS y
 * se lanza con el mismo node, que ademas evita depender del `.cmd` del PATH.
 */
const command = args[0] === "firebase"
  ? [process.execPath, require.resolve("firebase-tools/lib/bin/firebase.js"), ...args.slice(1)]
  : args;

const child = spawn(command[0], command.slice(1), {
  env,
  stdio: "inherit",
});
child.on("exit", (code) => process.exit(code == null ? 1 : code));
child.on("error", (error) => {
  console.error(`No se pudo ejecutar "${command[0]}": ${error.message}`);
  process.exit(1);
});
