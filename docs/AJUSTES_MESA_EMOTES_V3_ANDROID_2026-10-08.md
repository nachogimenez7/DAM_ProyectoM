# Mesa habitual, chat y emotes V3 — entrega del 8/10/2026

Android reutiliza la mesa habitual y sus componentes de presentación. Firebase
conserva la autoridad: el teléfono presenta estados y envía intenciones; cerrar una
ventana no resuelve votos ni adelanta fases. iOS y sources/ no se modificaron en este bloque.

## Cambios de esta entrega

- Encabezado con objetivo de fase, recuento sin anuncio antiguo y botones de acción
  contextuales: seleccionar antes de votar, SALVARME/SALVAR para Médico, espera
  deshabilitada cuando no hay intención disponible. Roles públicos al morir toman
  la configuración de la sala, no la muerte de un Alcalde revelado.
- Insets y oscurecimiento de pantalla compartidos con la mesa común, también sobre
  la barra de estado. Resultado sin expulsión conserva los totales públicos si hubo
  empate; ya no afirma que no hubo votos en ese caso.
- Chat público durante debate y ambas votaciones. Contrapunto restringido a sus
  participantes; invitado del Oráculo habla solo en debate. No cuenta como nuevos
  los mensajes que ya se veían en el feed central al ampliar el chat.
- Emotes con la paleta, arte, sonido y burbujas comunes. Doce emotes base; premium,
  pack y «6 7» excluidos hasta poder validar compras. Silenciados no envían emotes,
  tanto en V3 como en el modo común, por decisión confirmada del usuario.
- Reglas RTDB verifican propietario, pertenencia, fase, plazo, silencio, vida,
  catálogo, timestamp del servidor y modificación atómica. Diez segundos entre
  envíos y dos por ronda; ocho slots por jugador. Reconectar no reproduce antiguos.
- Revancha limpia reacciones y ledger en la misma publicación del nuevo matchId.
- Interruptor de beta público y de solo lectura: config/onlineV3. Una lectura
  Firestore por entrada al modo online, ninguna durante la mesa. Si falta, falla o
  está apagado, se cierran nuevas salas; recuperar una partida existente sigue disponible.
- Build beta 52 / 0.1.51 selecciona V3 con traidoresServerOnlineV3=true.
  Filtra salas del protocolo anterior y rechaza unirlas; no vuelve al motor del anfitrión.

## Verificación

- Android: 733 pruebas unitarias, cero fallos. Build Debug instalada en emulador.
- Backend: 73 unitarias y 39 de integración, incluidas recuperación, abandono,
  compatibilidad de resultado anterior, historial y revancha vacía.
- Reglas: 107 comprobaciones de autoridad/protocolo/configuración y 35 de emotes.
- Dos clientes Android nativos, SDK y reglas emulados: misma burbuja una sola vez,
  reconexión sin repetición y envío de chat durante VOTACION.
- Regresión nativa de mesa aprobada: Desertor inicial y silenciado en ronda4,
  desempate, recuento sin autoridad local, Alcalde silenciado/activo, Contrapunto
  con chat real y espectador, Oráculo invitado sin voto, muerte/silencio a través de
  transición de cuatro segundos, expulsión de ocho segundos sin revelar antes del
  impacto, Bufón con roles ocultos y doce segundos, reconexión en resultado sin
  repetir ceremonia. Intenciones por Auth/App Check/Functions emulados; fixtures
  dirigidos, no una partida productiva. Log: ajustes-native-table.log.
- Nueve pares de capturas de la mesa común y V3 bajo output/paridad-visual/ajustes/:
  noche, amanecer, debate cerrado/abierto, votación, recuento, resultado sin expulsión,
  expulsión y victoria. Fixtures Debug, no partidas reales ni latencia productiva.
  El fixture común de expulsión tuvo que abrir el overlay antes de llamar al animador;
  la corrección está limitada al escenario de QA.
  Se revisaron visualmente las ventanas finales; la aprobación del usuario en A56
  sigue pendiente. El texto del feed del fixture no representa una crónica completa.
- Release minificada con V3 compilada; todavía no es una entrega firmada y
  distribuida por Play. Estado de despliegue en CIERRE_ACTIVACION_V3_BETA.md.

Evidencia: output/paridad-visual/ajustes-qa-build.log, ajustes-core.log,
ajustes-integration.log, ajustes-authority-rules.log, emote-rules.log,
emote-native.log, emote-measurement.json y ajustes-release.log.

## Consumo adicional medido de emotes

Una ronda donde **todos** usan sus dos emotes, con todos los receptores conectados:

| Jugadores | Envíos | Entregas aceptadas | JSON recibido total | JSON por receptor |
| --- | ---: | ---: | ---: | ---: |
| 5 | 10 | 50 | 8.965 bytes | 1.793 bytes |
| 10 | 20 | 200 | 34.067 bytes | 3.407 bytes |
| 15 | 30 | 450 | 75.795 bytes | 5.053 bytes |

Es JSON observado por el SDK en **emulador**, no bytes facturados: faltan protocolo,
TLS, reintentos, suscripciones y otros servicios. Tampoco es una partida completa.
Las correcciones de timestamp optimista generan callbacks adicionales; la UI los
deduplica. Cada envío lee el ledger propio y actualiza dos rutas RTDB; no llama una
Function ni escribe Firestore. Se añade un listener RTDB limitado a 40 elementos,
solo en primer plano y fases habilitadas. La entrega total crece con emisor × receptor.
El chat en votación puede sumar tráfico según cuánto escriban los jugadores.
Como estimación de JSON, cinco rondas con 15 jugadores que usen todos sus emotes
equivaldrían a unos 25 KB por receptor (unos 379 KB sumando receptores). Es multiplicar
la muestra por cinco, no una partida medida ni una promesa sobre su costo facturado.

## Diferencias deliberadas respecto de la mesa anterior

El contador V3 usa el plazo del servidor. En debate aparece ESPERAR mientras el
servidor no ofrezca una intención: no se muestra un supuesto botón para adelantar
la votación. CONTINUAR cierra la presentación de un recuento, no cambia la fase.
V3 no recibe identidades de votantes y representa totales agregados. Al reconectar
tarde, las ceremonias se acortan o presentan estáticas para respetar el plazo.

## Para Claude

Revisar presentación/permisos y no duplicar motores ni cambiar backend en paralelo.
El contrato actualizado describe reactions y chat. En iOS se puede implementar el
mismo nodo/rate y permiso cuando corresponda, respetando la deduplicación y limpieza
por matchId. Este bloque no añadió emotes a iOS.

Antes de abrir la beta faltan aprobación visual del usuario en A56, firma/subida a
prueba interna, App Check de Play real, aislamiento con APK antiguo real, partida
Cloud completa y medición productiva. No hubo commit/push ni promoción de Play.
