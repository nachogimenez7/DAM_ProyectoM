import sharp from "file:///C:/Users/Nacho/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp/lib/index.js";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const project = path.resolve(here, "../../../..");
const width = 432;
const height = 900;

const backgroundPath = path.join(
  project,
  "app/src/main/res/drawable/mapa_medieval_vertical_dia.webp",
);
const artPath = path.join(here, "bufon-victoria-concept-v2.png");
const outputPath = path.join(here, "layout-preview.png");

const background = await sharp(backgroundPath)
  .resize(width, height, { fit: "cover", position: "centre" })
  .modulate({ brightness: 0.36, saturation: 0.58 })
  .blur(1.2)
  .png()
  .toBuffer();

const art = await sharp(artPath)
  .resize(374, 242, { fit: "cover", position: "centre" })
  .png()
  .toBuffer();

const base = Buffer.from(`
<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}">
  <defs>
    <linearGradient id="parchment" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#d2b177"/>
      <stop offset=".44" stop-color="#b98d53"/>
      <stop offset="1" stop-color="#8d6338"/>
    </linearGradient>
    <linearGradient id="goldButton" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#e7bd4f"/>
      <stop offset="1" stop-color="#a96a13"/>
    </linearGradient>
    <linearGradient id="darkButton" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#32271d"/>
      <stop offset="1" stop-color="#17110d"/>
    </linearGradient>
    <filter id="shadow" x="-30%" y="-30%" width="160%" height="180%">
      <feDropShadow dx="0" dy="15" stdDeviation="17" flood-color="#000" flood-opacity=".92"/>
    </filter>
  </defs>

  <rect width="432" height="900" fill="#000" opacity=".52"/>
  <g opacity=".2" font-family="Arial" font-weight="700" fill="#d4ae5e">
    <text x="18" y="42" font-size="17">RESULTADO DE LA VOTACIÓN</text>
    <rect x="18" y="682" width="396" height="176" rx="14" fill="#100b08" stroke="#73551f"/>
  </g>

  <g filter="url(#shadow)">
    <rect x="16" y="100" width="400" height="700" rx="22" fill="url(#parchment)" stroke="#4c2e1c" stroke-width="8"/>
    <rect x="18" y="102" width="396" height="696" rx="20" fill="none" stroke="#d1a24a" stroke-width="2"/>
    <path d="M48 128H164L179 116H253L268 128H384" fill="none" stroke="#6e3d25" stroke-width="1.5"/>

    <rect x="137" y="132" width="158" height="27" rx="13" fill="#6d2420"/>
    <text x="216" y="151" text-anchor="middle" fill="#f4dfa8"
      font-family="Arial,sans-serif" font-size="12" font-weight="800" letter-spacing=".8">VICTORIA ESPECIAL</text>

    <text x="216" y="194" text-anchor="middle" fill="#56231d"
      font-family="Georgia,serif" font-size="24" font-weight="700">¡EL BUFÓN LOS ENGAÑÓ!</text>
    <path d="M77 211H355" stroke="#79482a" stroke-width="2"/>

    <rect x="29" y="229" width="374" height="244" rx="13" fill="#17100b" stroke="#6b4225" stroke-width="2"/>
  </g>

  <text x="216" y="535" text-anchor="middle" fill="#40261a"
    font-family="Arial,sans-serif" font-size="19" font-weight="900">LAUTARO ERA EL BUFÓN</text>
  <text x="216" y="558" text-anchor="middle" fill="#7b2924"
    font-family="Arial,sans-serif" font-size="11" font-weight="800" letter-spacing="1">CONDICIÓN CUMPLIDA</text>

  <text x="216" y="595" text-anchor="middle" fill="#4b3020"
    font-family="Arial,sans-serif" font-size="14" font-weight="700">
    <tspan x="216" dy="0">Consiguió que el pueblo lo expulsara</tspan>
    <tspan x="216" dy="19">durante la votación.</tspan>
  </text>

  <text x="216" y="645" text-anchor="middle" fill="#6a4931"
    font-family="Georgia,serif" font-size="12" font-style="italic">
    La partida continúa, pero el Bufón ya consiguió su propia victoria.
  </text>

  <rect x="31" y="686" width="177" height="54" rx="11" fill="url(#goldButton)" stroke="#f0cb68"/>
  <text x="119.5" y="718" text-anchor="middle" fill="#211407"
    font-family="Arial,sans-serif" font-size="12" font-weight="900">SEGUIR MIRANDO</text>

  <rect x="224" y="686" width="177" height="54" rx="11" fill="url(#darkButton)" stroke="#8b6740"/>
  <text x="312.5" y="718" text-anchor="middle" fill="#eadfc8"
    font-family="Arial,sans-serif" font-size="12" font-weight="900">VOLVER A LA SALA</text>

  <path d="M54 770H176L188 780H244L256 770H378" fill="none" stroke="#6d4429"/>
</svg>
`);

const foreground = Buffer.from(`
<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}">
  <rect x="29" y="229" width="374" height="244" rx="13" fill="none" stroke="#6b4225" stroke-width="2"/>
</svg>
`);

await sharp(background)
  .composite([
    { input: base, left: 0, top: 0 },
    { input: art, left: 29, top: 231 },
    { input: foreground, left: 0, top: 0 },
  ])
  .png()
  .toFile(outputPath);

console.log(outputPath);
