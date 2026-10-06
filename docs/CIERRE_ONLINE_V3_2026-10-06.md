# Cierre de la primera parte: autoridad de servidor V3

6 de octubre de 2026. Punto de continuación para Codex y Claude.

## Qué queda guardado

- Backend V3: motor, callables, outbox, Cloud Tasks, recuperación, salida voluntaria,
  revancha y reglas de aislamiento. Incluye las decisiones confirmadas sobre Desertor,
  Alcalde silenciado, Mercenario y Oráculo. Historial idempotente calculado en servidor.
- Android: contrato y cliente Firebase real, entrada/recuperación desde el lobby,
  presentación experimental separada, acciones privadas, resultados y revancha.
  Todavía falta integrar la presentación habitual del gameplay.
- Chat: máximo 16 posiciones por jugador/canal, consulta de los últimos 60 mensajes,
  pausa de 2 segundos compartida entre canales, permisos por fase y borrado atómico
  del chat anterior al cambiar la partida. Sin una escritura Firestore por mensaje.
- iOS: paridad de reglas del núcleo y cambios de Claude en sonidos, haptics,
  ilustraciones y presentación de habilidades/resultados. Se conservan junto con
  sus documentos de revisión. La adaptación de gameplay iOS a V3 sigue pendiente.

## Pruebas comprobadas

- Android: 709 pruebas, cero fallos; APK Debug y Kotlin Release compilaron.
- Backend: 52 pruebas unitarias, cero fallos y comprobación de sintaxis.
- Backend emulado: 15 integraciones V3 y 62 verificaciones de reglas específicas.
- Ensayo nativo completo con Firebase local: código 0. Comprobó inicio, rol privado,
  confirmación de intención, avance con la app cerrada, reconexión por el lobby,
  chat real, resultado, historial por cuenta y revancha sin chat anterior.
- La aceptación conjunta Android/iOS y la revisión de las nuevas vistas/sonidos
  de Claude se retoman en la segunda parte. No afirmar que ese gameplay iOS ya usa V3.

## Estado productivo

Este cierre sube código a Git; no despliega Functions ni reglas Firebase.
`SERVER_ONLINE_V3=false` en Debug normal y Release. El gate Admin productivo sigue
sin habilitarse. El online publicado conserva el protocolo anterior.
Se retiró `app/src/debug/google-services.json`, que era temporal para QA, y se
reconstruyó Debug con la configuración normal. Los usuarios QA solo existieron en
emuladores aislados. Las capturas generadas son pruebas, no propuestas de rediseño.

## Segunda parte, orden propuesto

1. Integrar la presentación habitual Android sobre el cliente V3, sin arbitraje local;
   revisar acciones, chat, reconexión, resultado y revancha con accesibilidad.
2. Adaptar gameplay iOS al mismo contrato y cerrar su paridad de presentación.
3. Jugar una sesión real Android + iOS con el creador desconectado.
4. Medir partidas completas de 5/10/15, reconexiones, fotos y chats: operaciones
   Firestore, bytes RTDB por receptor, Functions/Tasks, latencias y errores. Los
   fixtures y logs locales no son una estimación de la factura del servidor.
5. Validar Tasks/IAM real y reparación/alertas para tareas agotadas; después,
   desplegar y habilitar V3 gradualmente.

Contrato detallado y comandos de reproducción:
`docs/CONTRATO_AUTORIDAD_SERVIDOR_V3.md`.

Claude puede revisar y testear contrato, aislamiento, revancha y paridad iOS;
no hace falta reabrir las reglas ya confirmadas. Evitar editar backend/reglas
simultáneamente con Codex sin acordar el reparto de archivos.
