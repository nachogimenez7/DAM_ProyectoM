# Checklist de QA — Cierre del modo local

> Marcá cada `[ ]` con **V** (anda bien) o **X** (anda mal). Si pusiste X, anotá qué pasó en "Notas libres" de esa fila — con eso arma después el spec para Codex.

## Antes de empezar

- Dispositivo: Samsung A56 — Android: _____ — Fecha: _____
- Las herramientas de debug (botón **"Opciones de testeo"** en el lobby → switches y "Forzar tu rol") **solo aparecen en builds de debug**. Si instalaste un APK de release y no ves los switches, jugá los casos de la Parte 2 dejando que salgan solos (puede tardar varias partidas).

---

## Parte 1 — Partidas por cantidad de jugadores

8 partidas completas cubren todos los quiebres de reparto y los 3 mapas al menos dos veces cada uno. Jugalas en el orden que quieras.

### 1. 5 jugadores (mínimo) — Pampa
- [ ] El reparto se ve bien, sin nombres/roles cortados
- [ ] La noche corre fluida, sin trabarse ni congelarse
- [ ] La votación y expulsión son fluidas
- [ ] Sin cierres inesperados de la app (ANR / crash)
- Notas libres:

### 2. 7 jugadores (aparece Mercenario) — Grecia
- [ ] Reparto ok / noche fluida / votación fluida / sin crashes (los 4 de arriba)
- [ ] El Mercenario silencia bien y el silenciado no puede hablar ni votar al día siguiente
- Notas libres:

### 3. 8 jugadores — Pampa (Payador exclusivo del mapa)
- [ ] Reparto ok / noche fluida / votación fluida / sin crashes
- [ ] Aparece el Alcalde y puede revelarse (voto doble)
- [ ] El Payador dispara el Contrapunto entre 2 jugadores y se resuelve bien
- [ ] **Opcional recomendado**: en esta partida forzá tu rol a **Payador** ("Opciones de testeo" → "Forzar tu rol") para probarlo vos mismo
- Notas libres:

### 4. 8 jugadores — Grecia (Oráculo exclusivo del mapa)
- [ ] Reparto ok / noche fluida / votación fluida / sin crashes
- [ ] **Forzá tu rol a Oráculo** ("Opciones de testeo" → "Forzar tu rol") — invocá a un muerto y confirmá que el juego **no se traba** (esto es lo que arreglamos hoy: médico/policía/oráculo se congelaban)
- [ ] El invocado vuelve a hablar en el debate siguiente pero no puede votar
- Notas libres:

### 5. 9 jugadores — Medieval (Bufón exclusivo del mapa + aparece Desertor)
- [ ] Reparto ok / noche fluida / votación fluida / sin crashes
- [ ] Dejá tu rol en AZAR: si el Bufón le toca a un bot, ¿busca activamente que lo voten, o juega pasivo como un aldeano más?
- [ ] El Desertor recibe su elección de bando inicial
- Notas libres:

### 6. 10 jugadores — Medieval (aparece Espía)
- [ ] Reparto ok / noche fluida / votación fluida / sin crashes
- [ ] El Espía actúa junto a los Asesinos de noche y el Policía lo ve como inocente al investigarlo
- [ ] **Forzá tu rol a Médico o Policía** en esta partida (el que no hayas probado antes) y confirmá que tu acción de noche resuelve sin trabarse
- Notas libres:

### 7. 13 jugadores (aparece 2do Asesino) — Pampa
- [ ] Reparto ok / noche fluida / votación fluida / sin crashes
- [ ] Los 2 Asesinos coordinan de noche sin romper nada (un solo objetivo elegido, mensajes del chat de traidores ok)
- Notas libres:

### 8. 15 jugadores (máximo) — Grecia
- [ ] Reparto ok / noche fluida / votación fluida / sin crashes
- [ ] La mesa activa scroll y se puede recorrer sin que las cartas se superpongan
- [ ] Con esta cantidad probá forzar un **empate de 5+ candidatos** (switch "Forzar empates") y confirmá que la ventana de desempate entra bien en pantalla (el fix de hoy)
- Notas libres:

---

## Parte 2 — Casos borde (forzados con "Opciones de testeo")

### A. Empate repetido → Alcalde decide (bot)
Forzá tu rol a algo que **no** sea Alcalde (ej. Aldeano) con 8+ jugadores, activá "Forzar empates", jugá hasta que se repita un empate.
- [ ] El Alcalde (bot) se revela y decide correctamente la expulsión
- Notas libres:

### B. Empate repetido → Alcalde decide (vos)
Forzá tu rol a **Alcalde**, activá "Forzar empates".
- [ ] Podés revelarte y elegir a quién expulsar entre los empatados sin trabas
- Notas libres:

### C. Morís temprano y seguís observando
En cualquier partida donde mueras pronto (no hace falta forzarlo), **no reinicies** — quedate observando el resto.
- [ ] Podés seguir viendo cartas/chat estando muerto, sin bloquear el avance de fases
- Notas libres:

### D. Desertor reconsiderando bando
Forzá tu rol a **Desertor** con 9+ jugadores, jugá hasta el punto donde el juego ofrece reconsiderar bando.
- [ ] La opción de reconsiderar aparece y el cambio de bando se aplica bien
- Notas libres:

### E. Sensación general de juego (sin flags de debug)
2-3 partidas jugadas "en serio", sin ningún switch de testeo activado.
- [ ] El ritmo general se siente bien (ni muy lento ni muy apurado)
- [ ] Ningún rol se siente roto o aburrido de jugar — anotá cuál si es el caso
- [ ] La IA de los bots se siente creíble en el chat (no repite frases raras ni contradice sin sentido)
- Notas libres:

---

## Hallazgos sueltos (no encajan arriba)

- _(anotar aquí)_
- _(anotar aquí)_
- _(anotar aquí)_
