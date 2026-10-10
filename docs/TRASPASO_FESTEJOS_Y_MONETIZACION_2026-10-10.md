# Traspaso: festejos de fin de partida y monetización (10/10/2026)

Para retomar en una sesión nueva. Trabajo para **Android**. Probador visual aprobado por el
usuario: https://claude.ai/artifact/HpzRPQpHaKCbjeRYNCzH8m

## Estado de la monetización (orden acordado: no avanzar sin cerrar el paso anterior)

1. ✅ AdMob: app «Traidores» y bloque intersticial «Entre partidas». Los IDs están en
   `gradle.properties` (Release los usa; Debug usa siempre los anuncios de prueba de
   Google).
2. ✅ Anuncios probados online por Codex. Se corrigieron dos cosas:
   - las reglas de traspaso de anfitrión excedían el límite de 1000 expresiones (ya
     desplegadas);
   - la marca «salió de la sala» se movió al setter de `leavingOnlineLobby`.
3. ⏳ Compras: falta lo de Play Console (perfil de pagos, 7 productos, testers de licencias,
   invitar a la cuenta de servicio) y el despliegue de Codex. Ver
   `docs/BASE_COMPRAS_2026-10-10.md`.
4. ⏳ Pantalla de tienda (sin empezar).
5. Decisión pendiente: si los derechos pasan a otra cuenta cuando alguien borra la suya.
6. Aparte: el usuario reportó un error al iniciar con Google, todavía sin reproducir.
   Sospecha: falta en Firebase la huella SHA de la firma de apps de Play.

Reglas de anuncios: un intersticial cada 2 partidas terminadas, con 4 minutos de
separación y ninguno en las 2 primeras partidas. Sin banners, sin recompensados y sin
anuncios al abrir el juego. Online: invitados al volver al lobby (antes de LISTO);
anfitrión solo al salir de la sala. El juego es para mayores de 13 y por ahora solo para
Argentina.

## Catálogo y precios aprobados

| Producto | US$ | Sugerido ARS |
|---|---|---|
| `pack_sello_pueblo` | 5,99 | $7.999 |
| `sin_anuncios` | 2,99 | $3.999 |
| `estilo_espacial`, `estilo_abismo_real`, `estilo_forja_infernal` | 1,99 c/u | $2.999 |
| `estilos_tres` | 3,99 | $5.999 |
| `emotes_memes` | 1,99 | a definir |

El pack incluye: sin anuncios, estilo Sello Carmesí (con fondo), marco, insignia, 3 banners,
emotes exclusivos (incluido «Brindis» de la Alcaldesa), placa de nombre, burbujas de chat y
festejo del Sello. Clásico es gratis y no tiene festejo.

## Festejos (lo próximo a programar en Android)

Pantalla de ganadores (`GameplayWinnerRevealLayout` / pantalla final común). Cada carta muestra
el festejo del **estilo que tiene puesto** ese jugador (`temaCosmeticoPerfil`, que ya viaja
en el perfil de la sala): el de ganar si su bando ganó, el de perder si perdió. Todos ven
el festejo de todos. Arrancan 0,5 s después del cartel de ganador, escalonados 0,3 s por
carta, y duran unos 2 s. Al terminar quedan en su pose final (el sello roto **queda**
visible). Con «Reducir movimiento» se muestra solo la pose final. Clásico no festeja.

Arte final (PNG transparente de 1024 px, salvo el fondo) en
`/Users/ignaciogimenez/.codex/.chatgpt-projects/g-p-6aade7b2232481919f33598df6616dcb/output/festejos/`:

| Estilo | Ganar | Movimiento | Perder | Movimiento |
|---|---|---|---|---|
| Mar (`sea`) | `mar_gana.png` sol con anteojos | sube por detrás de la carta con rebote | `mar_pierde.png` llanto | aparece y solloza; carta azulada, lágrimas que caen y forman un charquito |
| Fuego (`fire`) | `fuego_gana.png` | aparece y salta flexionando; carta salta y explota en llamas hacia arriba | `fuego_pierde.png` | aparece y tiembla de rabia; carta roja temblando con llamas |
| Espacial (`space`) | `espacio_gana.png` | aparece, brilla y se balancea; estrellas forman una constelación | `espacio_pierde.png` | se aleja flotando y girando; la carta queda a la deriva |
| Sello (pack) | `sello_gana.png` | cae girando, se estampa y brilla en dorado; chispas | `sello_pierde.png` | cae partido y tiembla; **queda en la carta** |

Fondo del estilo Sello: `sello_fondo.png` (1080×2400, opaco). Las partículas (lágrimas,
llamas, estrellas y chispas) se hacen con código. Tamaño del sticker: unos 78 dp sobre la
carta (el usuario pidió achicarlos un poco).

Plan: primero implementar con todos los estilos habilitados (Debug), después conectar
«solo si lo compraste» (`AccountEntitlements`). Convertir el arte a WebP en
`drawable-nodpi`.

Otros recursos del pack listos: `output/emotes/alcaldesa-brindis-v3.png` y
`output/emotes/sfx_emote_premium_brindis.ogg` (−20 LUFS).

## Cómo trabajar con el usuario

Responder en castellano rioplatense, con explicaciones simples (no es programador). Ir
paso a paso y mostrar las cosas visualmente en el probador. No hacer commit, push ni
despliegues sin pedido; los despliegues a producción los hace Codex.
