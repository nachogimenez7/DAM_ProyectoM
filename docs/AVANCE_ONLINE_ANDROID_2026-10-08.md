# Avance del online Android — 8 de octubre de 2026

Se retoma la mesa habitual después de las tres partidas completas contra Firebase
real (5/10/15 participantes automatizados autenticados) y los 21 recorridos locales.
El informe de esa medición sigue en `MEDICION_V3_BETA_ANDROID.md`. La tabla de
tarifas describe USD por 100.000 operaciones cobrables, no el precio de una partida
ni el total de las pruebas. La carga reducida observada no prueba todavía capacidad
con varias salas simultáneas o una beta con personas.

## Publicaciones: reducir trabajo repetido sin perder recuperación

- RTDB guarda `snapshot/delivery` junto con la proyección, en la misma transacción:
  matchId, generation, revision y queuedTaskId. Solo lo puede leer el servidor.
- Si RTDB ya confirmó esa versión pero faltó marcar su entrega en Firestore,
  se repara la confirmación con una lectura pequeña; no se vuelve a descargar ni
  escribir toda la mesa ni a crear su tarea.
- Si dos publicadores coinciden, el segundo evita una escritura idéntica. Antes
  de confirmar una transacción abortada comprueba de nuevo el dato del servidor;
  el valor optimista de la caché del SDK nunca basta para confirmar la entrega.
- La lectura pequeña usa REST con la credencial Admin existente y timeout. Un
  error conserva el outbox pendiente para los reintentos y la recuperación.
  No se añadieron secretos, claves nuevas ni dependencias.
- Las salas de versiones anteriores sin ese dato siguen siendo compatibles.
  La normalización de la proyección se hace una sola vez por publicación.
- Logging y la herramienta de métricas incluyen reusedDelivery y realtimeAttempts.
  projectionBytes sigue siendo JSON de un snapshot; no representa bytes facturados.

Esto evita trabajo redundante comprobado en las pruebas. El ahorro de tráfico,
dinero y latencia todavía debe medirse contra Cloud antes de atribuirle un porcentaje.
La lectura REST puede añadir otra petición en una carrera; no equivale a eliminar
todo el tráfico de cualquier publicación simultánea. Fuente de autenticación:
[documentación oficial de REST](https://firebase.google.com/docs/database/rest/auth).

## Android: continuar con los recursos habituales

- El Desertor usa `dialog_desertor_choice`: elección inicial y revisión única desde
  ronda 4, incluyendo mantener el bando. El plazo sigue avanzando en el servidor.
  El diálogo se descarta al cambiar de fase, perder sincronía o cerrar la actividad.
- Se reutiliza SilenceRevealAnimator y la jaula original para jugadores vivos
  silenciados en el amanecer público. No se muestran roles privados de aliados.
- Se reutiliza el panel del Oráculo al comenzar el debate, con el nombre de la voz
  invitada publicada por el servidor y su sonido existente. La carta muestra
  VOZ INVITADA; sigue siendo un jugador eliminado, sin voto ni poderes.
- Las revelaciones no se repiten en cada snapshot. Se cancelan al cambiar de fase
  o suspender la actividad; ninguna animación detiene ni resuelve el motor.
- No se modifica iOS, GameEngine, el diseño base ni el paquete del juego habitual.

## Verificado y límites

- 718 pruebas Android, cero fallos; compilación Debug habitual y QA separada aprobadas.
- 66 unitarias del backend y 39 integraciones servicio/recuperación, cero fallos.
  Incluyen fallos antes/después de publicar, caché optimista no confirmada, seis
  publicadores simultáneos (una escritura), compatibilidad y recuperación de revancha.
- Reglas: 104 comprobaciones aprobadas, incluyendo negar lectura y escritura de
  snapshot/delivery a clientes. No cambian las reglas desplegadas.
- Recorrido nativo Android en AVD con Auth, Functions, Firestore, RTDB y Tasks
  emulados: rol/acción, app cerrada y avance, reconexión, chat, resultado, historial
  de cuenta y revancha aprobados. Se ejecutó antes del último ajuste para abortar
  escrituras idénticas; ese ajuste quedó cubierto por las 39 integraciones posteriores.
- Las nuevas ventanas especiales tienen pruebas de política/contrato y compilan;
  todavía falta recorrerlas visualmente contra el servidor, además de aprobar la
  mesa completa en el A56. El recorrido nativo general no prueba todos los roles.

APK QA: `output/android-v3/traidores-v3-a56-qa.apk`, package com.traidores.juego.v3qa.
Configuración temporal de Firebase eliminada después de compilar. No se instaló
esta entrega en el A56 ni se desplegaron estos cambios nuevos en Cloud.

## Siguiente trabajo, en orden

1. Completar ventanas habituales de desempate/Alcalde y Contrapunto; revisar audio,
   feedback, crónica y el protocolo de emotes. Probar Desertor, silencio y Oráculo
   en recorridos dirigidos, luego una partida completa en el A56.
2. Desplegar la optimización con V3 restringido y comparar consumo/latencia; pasar
   a varias salas simultáneas en escalones pequeños, incluyendo lobby, fotos y chat.
3. Probar con personas, cortes de red y versión firmada distribuida por Play antes
   de abrir V3 gradualmente para la beta.

La base del servidor está mucho más avanzada que la presentación. No hay que
rehacer el arte ni las reglas del juego para cerrar esos pasos. Release mantiene
SERVER_ONLINE_V3=false; el gate de Cloud no se abrió en esta entrega.
