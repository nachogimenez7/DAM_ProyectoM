# Cierre del Modo Local — Guía de QA y bitácora

> **Esto NO es una spec para Codex.** Es tu hoja de ruta para **testear** y anotar lo que falle.
> El "cierre" del modo local es una pasada de QA: se cierra cuando una partida completa por mapa corre sin contenido cortado, sin rutas rotas y con feedback claro. Marcá `[x]` lo que pasa; anotá lo que falla en la bitácora (Parte 3).

## Antes de empezar
- [ ] Compilar la app con los últimos cambios (incluye el fix del encuadre de avatar).
- [ ] Confirmar que el **Modo Test Rápido está apagado** (es el default) — así probás el ritmo real.

---

## Parte 1 — Bugs puntuales a confirmar (rápido, ~10 min)
- [ ] **Expulsión no crashea**: llegá a una votación que termine en expulsión y tocá **"VER EXPULSIÓN"**. No debe tirar pantalla negra / cerrarse.
- [ ] **Payador `SEÑALAR`** (mapa Pampa, 15 jug.): como Payador humano, en el debate iniciá **Contrapunto** y **señalá** a un jugador. Debe registrar el señalamiento (no "no hace nada").
- [ ] **Ventana de acción/víctima**: como Asesino/Médico/Detective, hacé tu acción de noche. El texto debe quedar **dentro** del marco, legible, sin pisar el borde.
- [ ] **Avatar retrato**: entrá al perfil. La ilustración se ve como **retrato** (cara/hombros), no el cuerpo entero chiquito.
- [ ] **Títulos de overlays legibles**: en muerte / recuento / silencio, los títulos se leen sobre el fondo oscuro (no montados sobre el borde del marco).
- [ ] **Sin beeps sintéticos**: al aparecer tu carta no suena un "pim" agudo.

---

## Parte 2 — Una partida COMPLETA por mapa (el QA de verdad)

### Checklist general (revisar en las 3 partidas)
- [ ] Reparto de roles: se ve bien, tu carta se lee sin apuro.
- [ ] Noche: podés hacer tu acción; el feedback es claro ("Investigaste a X. Resultado: parece…").
- [ ] Amanecer: el reveal de muerte se lee sin apuro; tap-para-continuar funciona.
- [ ] Debate: el chat / cronista funciona; los bots hablan **sin repetirse ni auto-acusarse**.
- [ ] Votación → recuento (grilla) → expulsión: entra todo, sin corte de texto, sin crash.
- [ ] Victoria/derrota: la pantalla final es correcta según el bando ganador.
- [ ] Volver al menú/lobby sin rutas rotas ni estados raros.

### Medieval — probá **Asesino** y **Bufón**
- [ ] Asesino: elegís víctima con aliados; se gana por número.
- [ ] Bufón: si te **expulsan** ganás (victoria especial); si morís de noche, no.

### Grecia — probá **Detective** y **Oráculo**
- [ ] Detective: investigás y recibís pista; un **Espía** te aparece **inocente**.
- [ ] Oráculo: invocás a un muerto; habla en el próximo debate, no vota.

### Pampa — probá **Payador** y **Médico**
- [ ] Payador: Contrapunto + SEÑALAR funciona de punta a punta.
- [ ] Médico: protegés; si la víctima estaba protegida, no muere.

### Roles clave (probar en cualquier mapa con 10+ jugadores)
- [ ] **Espía**: de noche elegís la víctima junto a los asesinos **y** el Detective te ve inocente.
- [ ] **Mercenario**: silenciás; esa persona no habla ni vota al día siguiente.
- [ ] **Alcalde**: te revelás; tu voto vale doble y decidís empates.
- [ ] **Desertor**: elegís bando; podés reconsiderar una sola vez.

---

## Parte 3 — Bitácora de bugs encontrados
> Anotá acá lo que falle. Después me lo pasás y armamos los arreglos (yo lo mío, spec puntual para Codex lo suyo).

| # | Mapa / rol | Qué pasó | ¿Captura? |
|---|---|---|---|
|   |   |   |   |
|   |   |   |   |
|   |   |   |   |

---

## Fuera de alcance (NO bloquea el cierre de local)
- Avatar por foto de galería (backlog N7) — diferido post-cierre.
- i18n / selector de idioma (F4), acción nocturna del Bufón (F2), refactor del monolito (D1), online (N1–N4), tablets (N6).
- Amplificar 2 SFX en Audacity (tarea manual tuya) — no bloquea.

## Criterio de "modo local cerrado"
1. Los 6 chequeos de **Parte 1** pasan.
2. Una **partida completa por mapa** (Parte 2) corre sin contenido cortado, sin rutas rotas y con feedback claro.
3. La bitácora (Parte 3) quedó vacía o con bugs ya arreglados y re-testeados.
