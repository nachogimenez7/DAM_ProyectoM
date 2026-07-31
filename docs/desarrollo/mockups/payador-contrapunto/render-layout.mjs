import sharp from "file:///C:/Users/Nacho/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp/lib/index.js";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const project = path.resolve(here, "../../../..");
const width = 432;
const height = 900;

const backgroundPath = path.join(
  project,
  "app/src/main/res/drawable/mapa_pampa_vertical_noche.webp",
);
const artPath = path.join(here, "payador-contrapunto-concept.png");
const outputPath = path.join(here, "layout-preview.png");

const background = await sharp(backgroundPath)
  .resize(width, height, { fit: "cover", position: "centre" })
  .modulate({ brightness: 0.42, saturation: 0.72 })
  .blur(1.1)
  .png()
  .toBuffer();

const art = await sharp(artPath)
  .resize(372, 236, { fit: "cover", position: "centre" })
  .png()
  .toBuffer();

const overlay = Buffer.from(`
<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}">
  <defs>
    <linearGradient id="panel" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#3b2314"/>
      <stop offset=".46" stop-color="#100a07"/>
      <stop offset="1" stop-color="#2b180e"/>
    </linearGradient>
    <linearGradient id="gold" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#f6d36a"/>
      <stop offset="1" stop-color="#b77616"/>
    </linearGradient>
    <filter id="shadow" x="-30%" y="-30%" width="160%" height="180%">
      <feDropShadow dx="0" dy="14" stdDeviation="16" flood-color="#000" flood-opacity=".9"/>
    </filter>
    <filter id="glow">
      <feDropShadow dx="0" dy="1" stdDeviation="4" flood-color="#d28d27" flood-opacity=".45"/>
    </filter>
  </defs>

  <rect width="432" height="900" fill="#000" opacity=".38"/>

  <g opacity=".25" font-family="Arial" font-weight="700" fill="#d5ae59">
    <text x="18" y="42" font-size="17">DÍA · DEBATE</text>
    <text x="358" y="42" font-size="17">01:24</text>
    <rect x="18" y="674" width="396" height="184" rx="14" fill="#100b08" stroke="#8b672c"/>
  </g>

  <g filter="url(#shadow)">
    <rect x="18" y="135" width="396" height="630" rx="21" fill="url(#panel)" stroke="#473019" stroke-width="7"/>
    <rect x="18" y="135" width="396" height="630" rx="21" fill="none" stroke="#b9822a" stroke-width="2"/>
    <path d="M56 159H181L190 151L199 159H376" fill="none" stroke="#c18b33" stroke-width="1.5"/>
    <circle cx="216" cy="156" r="4" fill="#d7a13e"/>

    <text x="216" y="199" text-anchor="middle" fill="#f1c45d"
      font-family="Georgia,serif" font-size="20" font-weight="700"
      filter="url(#glow)">¡COMIENZA EL CONTRAPUNTO!</text>
    <text x="216" y="224" text-anchor="middle" fill="#cdbb95"
      font-family="Arial,sans-serif" font-size="13" font-weight="600">
      El Payador ha elegido a dos voces
    </text>

    <rect x="29" y="247" width="374" height="240" rx="14" fill="#0c0806" stroke="#9d702d" stroke-width="2"/>
  </g>

  <g>
    <rect x="39" y="444" width="112" height="49" rx="9" fill="#1a0f09" fill-opacity=".94" stroke="#d59b36"/>
    <text x="95" y="464" text-anchor="middle" fill="#f4cf7e"
      font-family="Arial,sans-serif" font-size="14" font-weight="800">LAUTARO</text>
    <text x="95" y="480" text-anchor="middle" fill="#aa9875"
      font-family="Arial,sans-serif" font-size="9" font-weight="700" letter-spacing="1">PRIMERA VOZ</text>

    <rect x="281" y="444" width="112" height="49" rx="9" fill="#1a0f09" fill-opacity=".94" stroke="#d59b36"/>
    <text x="337" y="464" text-anchor="middle" fill="#f4cf7e"
      font-family="Arial,sans-serif" font-size="14" font-weight="800">JULI</text>
    <text x="337" y="480" text-anchor="middle" fill="#aa9875"
      font-family="Arial,sans-serif" font-size="9" font-weight="700" letter-spacing="1">SEGUNDA VOZ</text>
  </g>

  <text x="216" y="535" text-anchor="middle" fill="#f0e3ca"
    font-family="Arial,sans-serif" font-size="15" font-weight="700">
    <tspan x="216" dy="0">Solo ellos podrán hablar</tspan>
    <tspan x="216" dy="20">durante el contrapunto.</tspan>
  </text>
  <text x="216" y="591" text-anchor="middle" fill="#ad9a7c"
    font-family="Arial,sans-serif" font-size="12">
    Al terminar, uno de los dos quedará señalado.
  </text>

  <rect x="32" y="630" width="368" height="55" rx="12" fill="url(#gold)" stroke="#f1c558"/>
  <path d="M42 638H390" stroke="#fff2a6" stroke-opacity=".55"/>
  <text x="216" y="664" text-anchor="middle" fill="#241405"
    font-family="Arial,sans-serif" font-size="15" font-weight="900" letter-spacing=".8">COMENZAR</text>

  <path d="M61 727H186L195 735L204 727H371" fill="none" stroke="#8a622b"/>
</svg>
`);

const foreground = Buffer.from(`
<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}">
  <rect x="29" y="247" width="374" height="240" rx="14" fill="none" stroke="#9d702d" stroke-width="2"/>
  <rect x="39" y="444" width="112" height="49" rx="9" fill="#1a0f09" fill-opacity=".94" stroke="#d59b36"/>
  <text x="95" y="464" text-anchor="middle" fill="#f4cf7e"
    font-family="Arial,sans-serif" font-size="14" font-weight="800">LAUTARO</text>
  <text x="95" y="480" text-anchor="middle" fill="#aa9875"
    font-family="Arial,sans-serif" font-size="9" font-weight="700" letter-spacing="1">PRIMERA VOZ</text>
  <rect x="281" y="444" width="112" height="49" rx="9" fill="#1a0f09" fill-opacity=".94" stroke="#d59b36"/>
  <text x="337" y="464" text-anchor="middle" fill="#f4cf7e"
    font-family="Arial,sans-serif" font-size="14" font-weight="800">JULI</text>
  <text x="337" y="480" text-anchor="middle" fill="#aa9875"
    font-family="Arial,sans-serif" font-size="9" font-weight="700" letter-spacing="1">SEGUNDA VOZ</text>
</svg>
`);

await sharp(background)
  .composite([
    { input: overlay, left: 0, top: 0 },
    { input: art, left: 30, top: 249 },
    { input: foreground, left: 0, top: 0 },
  ])
  .png()
  .toFile(outputPath);

console.log(outputPath);
