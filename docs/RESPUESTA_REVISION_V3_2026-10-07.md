# Respuesta a la revisión V3 de Claude

Entrega del 6–7 de octubre de 2026. Aplicada en el árbol local de `ios-port`;
no se desplegó ni habilitó V3 y no se editaron archivos de iOS. Se conservaron
los cambios de Claude presentes durante el trabajo.

## Decisiones y correcciones aplicadas

**A — Abandono también después de morir.** Se conserva `ABANDONO → won:false`.
Se fijaron los dos casos pedidos: Aldeano muerto que sale y después gana Pueblo;
Bufón expulsado que sale antes del final. Las integraciones escriben un historial
por cuenta y estadísticas `partidas:1, victorias:0`, sin duplicar ante otro archivado.
Salir después del resultado no altera una victoria ya concedida.

**B — Bando automático privado.** Al vencer `REPARTO`, si falta elección, usa
`crypto.randomInt(2)` y la persiste con la primera noche. No usa `stableNoise` ni
nombres/código. La extracción es perezosa y se conserva fuera del cuerpo reintentable
de la transacción: una repetición usa el mismo valor. Una elección explícita y
una tarea vieja no vuelven a sortear. En debate/ventana, no responder mantiene bando.

**N-1 — Ventana sin bucle.** Al abrir se guarda `deserterReturn` privado. Una salida
que rompe la paridad devuelve `AMANECER → DIA_DEBATE` o `RESULTADO → NOCHE` de la
ronda siguiente, sin consumir el cambio ni modificar el bando. Si la ventana se
abrió por un abandono en otra fase, conserva su tiempo restante y las intenciones
de participantes que siguen presentes; no restaura las del que salió. Si sigue
habiendo paridad, conserva la ventana y el deadline originales. Una victoria de
Pueblo cierra inmediatamente.

Cada vencimiento cambiado aumenta `phaseIndex` o fija ganador; el plazo de una fase
activa nueva es futuro. Hay comprobación en motor y antes de escribir en el servicio.
Un estado corrupto genera `phase-stuck`; el cron registra `online_v3_recovery_stuck`
y omite estado/outbox/publicación. Solo puede guardar el cursor del lote. La alerta
y reparación operativa de corrupción siguen siendo trabajo de despliegue.

**N-3 — Revancha protegida.** Las reglas bloquean edición y eliminación de marcadores
de autoridad/protocolo/generación/revancha, estado y datos iniciales/resultados V3.
La restricción global cubre todas las ramas de actualización, no solo al anfitrión.
No se puede borrar y recrear la revancha como legacy. Siguen funcionando ajustes
de lobby y «listo», y crear/cancelar un lobby V3 antes de su primer inicio. Se
bloquea falsificar marcadores de servidor al crear la sala. Se reordenó la rama
habitual del anfitrión para mantener margen bajo el límite de expresiones de reglas;
las suites legacy y V3 pasan.

Android rechaza degradación antes de recuperar/abrir el lobby. Recuerda que una sala
usó V3 también al recrear la app. Esa preferencia es un indicador defensivo, nunca
una autorización. La recuperación de revancha V3 verifica membresía Firestore y no
consulta presencia legacy para decidir si todavía existe.

**N-4 — Eventos tipados.** Se eliminó el valor por defecto `INFO`. AFK incluye UID;
los códigos añadidos son `AFK_EXPULSION`, `NIGHT_START`, `COUNTERPOINT_OPEN`,
`DESERTER_WINDOW`, `DESERTER_WINDOW_CLOSED`, `VICTORY`, `MATCH_CANCELLED` y
`MAYOR_NO_DECISION`. Se mantienen los anuncios anteriores de votos, Oráculo y Alcalde.

**N-7 — Secuencia y reconexión.** Cada evento incorpora `seq` positivo y creciente
por partida. No se reinicia al recortar el anillo; solo se publica la ronda actual.
Intenciones secretas no aumentan `seq`. Android valida orden/identificadores, conserva
el piso fase/revisión entre reconexiones y guarda el cursor de presentación al recrear
la Activity. Su anuncio de accesibilidad usa eventos nuevos. Futuros sonidos/animaciones
deben consumir este mismo resultado: clave `matchId+seq`; fases `matchId+phaseIndex`.
La revancha inicia otro cursor, sin reutilizar intenciones ni presentación anteriores.

## Verificación y consumo

- 61 unitarias backend y sintaxis; 4 pruebas del contador.
- 42 casos únicos de integración V3/recuperación/limpieza aprobados: suite conjunta
  de 41 y repetición completa de recuperación con 16, incluida la nueva prueba de
  historial. La repetición final fue 16/16. Un intento anterior tuvo timeout de gRPC
  al arrancar el emulador; no se ocultó ni se cambió el resultado esperado del test.
- 90 comprobaciones específicas de seguridad V3; suites legacy Firestore/RTDB aprobadas.
- 711 pruebas Android sin fallos, incluidas 11 de contrato; APK Debug y Kotlin Release.
- Sonda original de Claude ejecutada sin editarla: el abandono pasa a debate y los
  vencimientos siguientes avanzan. La elección posterior es la opcional del debate.
- Tres sesiones SDK de 5/10/15: mismos documentos y callbacks; JSON aproximado baja
  de 134/429/1.178 KiB a 119/392/1.050 KiB. No son bytes facturados ni tarifas Cloud.

Evidencia local: `output/v3-review-unit.log`, `output/v3-review-emulators.log`
(integraciones), `output/v3-review-rules.log` (reglas finales),
`output/v3-review-sdk-final.log`, `output/v3-review-android-final.log`.
El intento de reglas dentro del primer log detectó el límite de evaluación; el
segundo valida el orden corregido. No se repitió el ensayo nativo completo ni HTTP
Tasks en esta revisión; las pruebas anteriores no certifican IAM/entrega en Cloud.

## Para Claude y siguiente bloque

Claude verificó y cerró A, B, N-1, N-3, N-4 y N-7 en la sección 6 de
`ios/docs/REVISION_CONTRATO_V3.md`. No queda otro cambio bloqueante de esa revisión.

Se atendieron también sus dos notas menores:

- Android ya reconstruye la mesa desde el snapshot actual en `renderState()`:
  jugadores, fase, roles visibles, condición propia y resultado no dependen de
  eventos antiguos. Los anuncios de accesibilidad usan solo eventos nuevos del
  cursor; iOS puede seguir el criterio propuesto sin ampliar la proyección pública.
- Las reglas ahora exigen `protocolVersion=3` en cada alta/actualización de jugador
  de una sala V3. Bloquean quitarlo con `deleteField`, reemplazar el documento sin
  versión y hacerlo desde la cuenta del creador. Usan `getAfter` para cubrir el
  alta atómica de sala y creador. Siguen pasando alta válida, «listo» y expulsión
  del lobby; las salas legacy mantienen la versión opcional. No añade listeners,
  callables ni consultas periódicas.

Verificación de este cierre: **102 comprobaciones V3**, suite Firestore general y
suite de invitados aprobadas en los emuladores aislados (18081/19000).
Evidencia: `output/v3-player-protocol-rules-final.log`. La primera ejecución
aprobó V3 y la suite general, pero la suite de invitados apuntaba al puerto fijo
8081 y no conectó; se ajustó para respetar `FIRESTORE_EMULATOR_HOST`, y la ejecución
completa posterior terminó con código 0. No se cambiaron puertos ni archivos iOS
de Claude. Este cierre continúa local, sin despliegue ni habilitación V3.

Contrato vigente: `docs/CONTRATO_AUTORIDAD_SERVIDOR_V3.md`. Para iOS, añadir
`seq` al DTO y consumir eventos
por `matchId+seq`, sin convertir `ClassicGame` en autoridad de una sala V3. Respetar
la ventana idéntica del Alcalde secreto y la coherencia público/privado/permisos.
No hace falta volver a decidir reglas ni modificar backend/reglas en paralelo.

Queda integrar la presentación habitual Android y gameplay/adaptadores reales V3
iOS, probar reconexión/revancha con ambos clientes, preparar alertas/índices/TTL y
hacer ensayo reducido de Tasks/IAM/Scheduler, concurrencia y consumo en Cloud.
Desplegar con gate cerrado y abrir gradualmente después de aprobar esas verificaciones.
El online productivo conserva por ahora su protocolo anterior.
