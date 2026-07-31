import sharp from "file:///C:/Users/Nacho/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp/lib/index.js";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const project = path.resolve(here, "../../../..");
const width = 432;
const height = 900;

const backgroundPath = path.join(
  project,
  "app/src/main/res/drawable/mapa_grecia_vertical_noche.webp",
);
const artPath = path.join(here, "oraculo-regreso-concept-v2.png");
const outputPath = path.join(here, "layout-preview.png");

const background = await sharp(backgroundPath)
  .resize(width, height, { fit: "cover", position: "centre" })
  .modulate({ brightness: 0.4, saturation: 0.72 })
  .blur(1.1)
  .png()
  .toBuffer();

const art = await sharp(artPath)
  .resize(372, 238, { fit: "cover", position: "centre" })
  .png()
  .toBuffer();

const base = Buffer.from(`
<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}">
  <defs>
    <linearGradient id="panel" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#172a3d"/>
      <stop offset=".43" stop-color="#080d16"/>
      <stop offset="1" stop-color="#171128"/>
    </linearGradient>
    <linearGradient id="progress" x1="0" y1="0" x2="1" y2="0">
      <stop offset="0" stop-color="#e8b958"/>
      <stop offset=".46" stop-color="#79d9ff"/>
      <stop offset="1" stop-color="#c8f4ff"/>
    </linearGradient>
    <filter id="shadow" x="-30%" y="-30%" width="160%" height="180%">
      <feDropShadow dx="0" dy="14" stdDeviation="16" flood-color="#000" flood-opacity=".9"/>
    </filter>
    <filter id="glow">
      <feDropShadow dx="0" dy="1" stdDeviation="5" flood-color="#67d8ff" flood-opacity=".48"/>
    </filter>
  </defs>

  <rect width="432" height="900" fill="#000" opacity=".42"/>
  <g opacity=".24" font-family="Arial" font-weight="700" fill="#d7bd76">
    <text x="18" y="42" font-size="17">NOCHE · GRECIA</text>
    <text x="358" y="42" font-size="17">00:42</text>
    <rect x="18" y="675" width="396" height="180" rx="14" fill="#080c12" stroke="#6d5e36"/>
  </g>

  <g filter="url(#shadow)">
    <rect x="18" y="158" width="396" height="575" rx="22" fill="url(#panel)" stroke="#263b54" stroke-width="7"/>
    <rect x="18" y="158" width="396" height="575" rx="22" fill="none" stroke="#bf9343" stroke-width="2"/>
    <path d="M50 183H171L185 171H247L261 183H382" fill="none" stroke="#d1a653" stroke-width="1.5"/>
    <circle cx="216" cy="176" r="5" fill="#6dd7ff" stroke="#ead28d"/>

    <text x="216" y="222" text-anchor="middle" fill="#f0cf77"
      font-family="Georgia,serif" font-size="24" font-weight="700"
      filter="url(#glow)">¡UNA VOZ REGRESA!</text>
    <text x="216" y="248" text-anchor="middle" fill="#bdd8e7"
      font-family="Arial,sans-serif" font-size="13" font-weight="600">
      El fuego recuerda su nombre
    </text>

    <rect x="29" y="271" width="374" height="242" rx="14" fill="#090d14" stroke="#ab8240" stroke-width="2"/>
  </g>

  <text x="216" y="576" text-anchor="middle" fill="#f1e8d1"
    font-family="Arial,sans-serif" font-size="16" font-weight="700">
    <tspan x="216" dy="0">Juli vuelve para hablar</tspan>
    <tspan x="216" dy="21">una vez más.</tspan>
  </text>
  <text x="216" y="632" text-anchor="middle" fill="#9fb1bd"
    font-family="Arial,sans-serif" font-size="12">
    El pueblo escuchará una voz que ya había perdido.
  </text>

  <rect x="52" y="679" width="328" height="5" rx="2.5" fill="#26323d"/>
  <rect x="52" y="679" width="244" height="5" rx="2.5" fill="url(#progress)" filter="url(#glow)"/>
  <path d="M66 704H179L190 714H242L253 704H366" fill="none" stroke="#705d37"/>
</svg>
`);

const foreground = Buffer.from(`
<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}">
  <rect x="29" y="271" width="374" height="242" rx="14" fill="none" stroke="#ab8240" stroke-width="2"/>
  <rect x="269" y="469" width="124" height="50" rx="9" fill="#0b111a" fill-opacity=".94" stroke="#74d8ff"/>
  <text x="331" y="490" text-anchor="middle" fill="#d8f5ff"
    font-family="Arial,sans-serif" font-size="15" font-weight="800">JULI</text>
  <text x="331" y="506" text-anchor="middle" fill="#adbec6"
    font-family="Arial,sans-serif" font-size="9" font-weight="700" letter-spacing="1">VOZ RECUPERADA</text>
</svg>
`);

await sharp(background)
  .composite([
    { input: base, left: 0, top: 0 },
    { input: art, left: 30, top: 273 },
    { input: foreground, left: 0, top: 0 },
  ])
  .png()
  .toFile(outputPath);

console.log(outputPath);
