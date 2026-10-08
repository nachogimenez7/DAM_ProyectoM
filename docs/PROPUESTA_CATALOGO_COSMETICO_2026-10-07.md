# Propuesta de catálogo cosmético para la beta

Fecha: 7 de octubre de 2026 (actualizado el 8) · Estado: **borrador; precio del pack y reparto decididos por el usuario el 8/10, el resto sin aprobar**.
No se tocó código, backend, reglas ni archivos iOS. No hay commit ni productos publicados.
Layout visual de cada producto: [`propuesta_cosmeticos/catalogo_visual.html`](propuesta_cosmeticos/catalogo_visual.html).

**Plataforma.** El encargo dice «todo para iOS, luego replicamos en iOS»; el plan y el brief
son de Android. Esta propuesta es neutra: el catálogo, las reglas y la presentación valen para las
dos. Las diferencias de tienda (Play Billing / StoreKit) están en la sección 5. Confirmar con el usuario
cuál va primero.

**Regla de oro.** Todo es cosmético. Ninguna compra cambia reglas, poderes, roles ni información de
la partida. Ver el cosmético de otro jugador siempre es gratis; solo equiparlo requiere tenerlo.

---

## 1. Inventario de recursos

Revisado en `assets/pack_bienvenida/`, `app/src/main/res/` y los catálogos Kotlin.

### Pack de apoyo (`assets/pack_bienvenida/`)

| Recurso | Estado | Notas |
|---|---|---|
| Marco de perfil (`marco_perfil.png`, 1254², RGBA) | **Terminado** (arte) / retocar tamaños | Aro bronce con cinta carmesí y medallón «T». Falta comprobar legibilidad a tamaño de tarjeta online/lobby (≈40–56 dp); puede necesitar una versión simplificada. |
| Insignia (`insignia_apoyo.png`, 1254², RGBA) | **Terminado** (arte) / retocar tamaños | Sello de cera carmesí con «T» dorada. Falta versión pequeña (16–24 dp) para chat y mesa. |
| Banner Asesino medieval sonriente | **Terminado** | Es el que usa la app. `asesino_medieval.png` queda como variante descartada; archivar. |
| Banner Médica pampeana | **Terminado**, corregir nombre | El archivo y el doc dicen «médico pampeano»; el arte y la app dicen «Médica». Unificar. |
| Banner Oráculo griego | **Terminado** | — |
| Estilo «Pack de apoyo» (`support_preview`) | **A completar** | Hoy es solo paleta roja y dorada en `CosmeticPilot`. No tiene fondo propio (los otros estilos sí). Es una prueba local, no un tema publicable. |

Nota de contenido: el banner del Asesino muestra un cuchillo al cuello (ilustración cartoon). Revisar contra
la clasificación de contenido de Play/App Store antes de usarlo en promoción.

### Ya en el juego, sin restricciones (lo que los jugadores tienen hoy)

No encontré ninguna compuerta de acceso en el código: `isPremium` en `EmoteCatalog` solo es una
etiqueta y `selectTheme`, los banners y los avatares no comprueban nada. **Hoy todo está libre.**

| Familia | Cantidad | Detalle | Estado de arte |
|---|---|---|---|
| Emotes de rol | 12 | Griego, Asesino medieval y Detective gaucho × 4 emociones | Terminado |
| Emotes «premium» (etiqueta) | 8 | Hermosa mañana, Ruidito de mate, Me duermo zzz · **nuevos:** Genio, Médico tímido, Cualquiera, Mmmm… ñe, 6 7 (animado, 2 cuadros) | Terminado; ver retoques |
| Sonidos de emote | 20 | `sfx_emote_*` | Terminado (niveles ya trabajados) |
| Estilos de perfil | 4 | Clásico, Espacial, Abismo Real, Forja Infernal (con fondo) | Terminado |
| Banners | 6 | Pampa, Grecia, Medieval + las 3 escenas del pack | Terminado |
| Avatares animales | 15 | Compartidos Android/iOS | Terminado |

Los bots usan `premium_mate` y `premium_dormida` en sus emotes (`PlayerProfile.kt`). No afecta: ver es gratis.

### A retocar

1. **Emote «Genio»**: tiene el texto «GENIO!» dentro de la imagen (no se puede traducir) y un estilo cartoon
   distinto al resto, que es pintado. Rehacer sin texto, en el estilo común.
2. **Contorno blanco** de los emotes nuevos frente a los de rol (sin contorno): decidir uno solo.
3. **Marco e insignia**: versiones pequeñas y prueba sobre las fotos de perfil y los 15 avatares.
4. Nombre «Médica/Médico pampeano»: unificar en arte, archivo, doc y app.

### Faltante

- Fondo del estilo del pack (y su variante con los mismos recortes que los demás estilos).
- **Presentación fuera del perfil**: marco e insignia solo están cableados en `ProfileActivity`. No aparecen en
  tarjeta online, lobby ni mesa, que el plan pide.
- **Toda la tienda** (no existe): pantallas, miniaturas de producto, estados bloqueado/comprado/pendiente.
- Imagen central del pack para la tienda (marco + insignia + banner compuestos).
- Opcional: marco rectangular para la placa de nombre.

---

## 2. Decisiones del usuario (8/10/2026)

- Todos los jugadores arrancan de cero al lanzar la beta: **no hay legado ni grandfathering** (se descartan las opciones A/B/C anteriores).
- Pack a **USD 4,99**, con las piezas listadas abajo **y quitar publicidad**.
- Solo el estilo Clásico es gratis; los demás estilos se compran.
- Mejorar el emote Genio y la animación/sonido del 6 7.

## 3. Contenido y reparto

### Pack «Sello del Pueblo» — USD 4,99 (11 piezas cosméticas + sin publicidad)

| Componente | Contenido | Estado |
|---|---|---|
| Estilo | Sello Carmesí | Falta el fondo |
| Marco | Aro de bronce y cinta carmesí | Terminado |
| Insignia | Sello de cera «T» | Terminada |
| Banners (3) | Asesino medieval · Médica pampeana · Oráculo griego | Terminados |
| Emotes (5) | Genio · Médico tímido · Cualquiera · Mmmm… ñe · 6 7 | Genio a rehacer; 6 7 a mejorar |
| Sin publicidad | Elimina los anuncios del juego | Ver nota |

**Nota sobre «sin publicidad».** Es el único beneficio no cosmético del pack. Hoy no hay SDK de anuncios en Android; el plan propone
anuncios recompensados voluntarios. Un anuncio voluntario no necesita «quitarse»: hay que decidir **qué anuncios habrá** (¿entre partidas?)
antes de prometerlo en la ficha. Puede afectar la declaración de datos de Play. No altera reglas ni da ventaja en partida.

### Productos sueltos

| ID (borrador) | Nombre | Contenido | Precio |
|---|---|---|---|
| `pack_sello_pueblo` | Sello del Pueblo | Pack completo | 4,99 |
| `emotes_memes` | Memes Pampeanos | Hermosa mañana, Ruidito de mate, Me duermo zzz | 1,99 |
| `estilo_espacial` | Estilo Espacial | Fondo y paleta | 1,49 |
| `estilo_abismo_real` | Estilo Abismo Real | Fondo y paleta | 1,49 |
| `estilo_forja_infernal` | Estilo Forja Infernal | Fondo y paleta | 1,49 |
| `estilos_tres` (opcional) | Los 3 estilos | Espacial + Abismo Real + Forja Infernal | 2,99 |

### Gratis al empezar
Estilo Clásico · 12 emotes de rol · banners Pampa, Grecia y Medieval · 15 avatares animales.

### Solo en el pack (no se venden sueltos)
Estilo Sello Carmesí, marco, insignia, 3 banners de escena, 5 emotes legendarios y sin publicidad.

### Pendientes de decidir
- Precios de los sueltos (propuesta, sin datos de mercado; estilos bajados a 1,49 el 8/10) y si entra «Los 3 estilos».
- `support_preview` (prueba local): se descarta al lanzar; `normalizeTheme` ya ignora temas desconocidos.

## 4. Mejoras de arte comprometidas

- **Genio (iOS, 8/10):** el texto «GENIO!» se conserva por decisión del usuario, pero se achicó al 62 % junto con su trazo en el PNG de iOS (Android sin tocar).
  Sigue pendiente lo que no se arregla editando píxeles: es un dibujo cartoon de 256 px, cortado en el borde derecho y distinto al
  estilo pintado del resto. Para resolverlo hay que regenerarlo (brief abajo).
- **6 7 (iOS, 8/10):** la animación ahora cambia de cuadro con un pequeño salto en cada uno de los 4 golpes del sonido (≈0,10 / 0,35 / 0,50 / 0,75 s) y termina en reposo. El sonido no se tocó. Diagnóstico original: eran 2 cuadros que alternan las manos cada 110 ms (0,44 s por ciclo) y el sonido dura 1,2 s: no coinciden.
  Propuesta: 6-8 cuadros con movimiento más amplio, ~150-200 ms por cuadro, y dos golpes de sonido sincronizados con dos gestos.
  El nivel del sonido (-21,9 LUFS) está cerca del resto (≈-20); no pude escucharlo, así que el diagnóstico es del archivo, no del oído.
  Con «Reducir movimiento» queda quieto.
- Versiones pequeñas de marco e insignia; unificar el contorno de los emotes; nombre «Médica pampeana».

### Brief para regenerar Genio (herramienta de imágenes)
Busto de hombre corpulento de unos 45 años, bigote negro grueso, barba de dos días, pelo oscuro peinado hacia atrás, cuello alto granate
con broche dorado y cadena de oro; expresión de orgullo exagerado, mirada de lado, ceño levantado, boca en una sonrisa de suficiencia
(no gritando). Con la palabra «GENIO!» en un globo pequeño (opcional, como letras aparte). Mismo estilo pintado al óleo y contorno fino que los emotes de rol. Centrado,
completo (nada cortado), fondo transparente, 1024×1024 para poder reducir a 256 y 512.

---

## 5. Cómo se presenta cada producto

No existe tienda. Propuesta mínima: **una pantalla «Tienda»** accesible desde Perfil (botón junto a «Editar perfil»)
y desde cualquier cosmético bloqueado. Sin pestañas ni monedas.

**Tienda**: tarjeta destacada del pack arriba, luego filas para los productos sueltos. Cada fila muestra miniatura,
nombre, qué incluye en una línea y el precio **que informa la tienda** (nunca fijo en el juego), o «Equipar»/«Ya lo tenés».

**Detalle de producto**
1. **Vista previa real**: reutiliza el render del perfil (como hoy `support_preview`): avatar con marco, insignia, banner,
   emotes animados con su sonido. El botón «Probar» aplica la vista sin concederla.
2. **Contenido incluido**: lista con miniaturas de cada pieza; en los sets sueltos, qué se repite con el pack.
3. **Botón de compra** con el precio de la tienda. Tras comprar: «Equipar ahora».
4. **Equipar** es independiente del derecho: se compra una vez, se equipa o desequipa cuando se quiera.

**Estados**: sin comprar · pendiente (pago diferido; no concede aún) · comprado · reembolsado (se retira y se vuelve a
Clásico/los gratis) · sin conexión · error de la tienda. Comprar está desactivado sin cuenta de Firebase.

**Restaurar**: botón «Restaurar compras» al pie de la Tienda y en Ajustes. Al reinstalar o cambiar de dispositivo, el
inventario sale de Firebase (no del teléfono). Apple exige este botón; Google no, pero conviene igual por claridad.

**Bloqueados en selectores**: los selectores de estilo, banner y emotes muestran los productos no comprados con un candado
y llevan al detalle. Un cosmético sin derecho no puede publicarse online aunque el valor local lo diga.

**Tienda concreta**: Android usa Google Play Billing; iOS, StoreKit 2. En iOS el precio y la restauración vienen del sistema,
los productos son «no consumibles» y no se menciona otro medio de pago.

**Dónde se muestra lo comprado**: perfil, tarjeta online, lobby y mesa, según el alcance (marco e insignia: perfil, tarjeta y
lobby; en mesa solo marco chico; emotes: chat y mesa; estilo: perfil y menús). Hoy solo hay presentación en perfil: el resto es trabajo nuevo.

---

## 6. Escenas del tráiler que mostrarían estos cosméticos

Solo mostrar lo que exista y se pueda usar en beta. No grabar hasta tener mesa V3 y tienda real; si la tienda no está lista, evitar
mostrar precios o el botón de compra.

| # | Escena | Cosmético visible |
|---|---|---|
| 1 | Perfil con avatar animal, marco carmesí e insignia; la pantalla se desplaza al banner | Marco, insignia, banner Asesino |
| 2 | Selector de estilo: Clásico → Sello Carmesí con vista previa | Estilo del pack |
| 3 | Debate nocturno: alguien manda «Sospechoso» y se ve el emote sobre su ficha | Emotes de rol |
| 4 | Voto empatado: reacciones rápidas «Genio» y «Cualquiera» | Emotes legendarios |
| 5 | Mesa con 2–3 jugadores con marco/insignia visibles | Marco e insignia en partida |
| 6 | Resultado de partida con el 6 7 animado en el perfil del ganador | Emote animado |
| 7 | Lobby con tarjetas de jugadores con distintos banners | Banners de escena |
| 8 | Cierre: la pantalla de la tienda con el pack y su vista previa (solo si existe y se puede usar) | Pack completo |

---

## Decisiones que quedan

1. Precios de los sueltos y si entra «Los 3 estilos».
2. Qué anuncios habrá, para poder prometer «sin publicidad».
3. Plataforma primero: Android o iOS (el pedido dice iOS dos veces).
4. Aprobar el nuevo arte (Genio, 6 7, fondo del Sello Carmesí).

## Trabajo siguiente (cuando se decida; no hecho)

Para Codex: derechos en Firebase separados de lo equipado, validación de compra en el servidor, `normalizeTheme` y listas del servidor con los
IDs de estilo nuevos, derecho «sin publicidad» y presentación en tarjeta/lobby/mesa. Para arte: fondo del estilo, miniaturas, versiones pequeñas
de marco/insignia, Genio y 6 7.
