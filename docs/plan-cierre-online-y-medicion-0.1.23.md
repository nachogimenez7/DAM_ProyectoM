# Plan de cierre del online y medición de consumo

**Fecha:** 10 de septiembre de 2026  
**Base funcional aprobada una vez:** `0.1.23` (`versionCode 24`)  
**Versión candidata actual:** `0.1.24` (`versionCode 25`)  
**Objetivo:** llegar a una beta abierta estable en Spark, con partidas de 5 a 15 jugadores y
una medida real del consumo por participación.

## Decisión inmediata

La primera partida de `0.1.23`, realizada con cuatro emuladores y un teléfono real, completó el
flujo sincronizado y conservó las presentaciones. `0.1.24` mantiene ese protocolo y sólo corrige
presencia inicial, navegador de salas, distribución del chat, escala de la carta inferior y el
texto de la barrera automática posterior a los anuncios.

La prueba abre dos caminos:

- **Si aparece un error crítico o alto:** conservar capturas y reportes, reproducirlo una vez,
  corregir una sola causa y generar una nueva APK. La medición obtenida sirve para diagnosticar,
  pero no será la línea base oficial.
- **Si pasa:** congelar la lógica funcional y preparar la versión de medición. Luego ejecutar la
  matriz de 5, 10 y 15 jugadores antes de reducir tráfico o autoridad del anfitrión.

## Primera prueba: cinco jugadores

Instalar exactamente la APK `0.1.24` en los cinco dispositivos y comenzar con identidades
Firebase distintas. La primera situación ya pasó en `0.1.23`; se repite brevemente como prueba de
regresión y se completan las otras dos:

| Partida | Situación deliberada | Qué debe ocurrir |
|---|---|---|
| 1 | Flujo normal | Todos ven reparto, noche, amanecer, votación, expulsión y victoria completos. |
| 2 | Un invitado demora cada confirmación visual | Nadie recibe un spoiler ni queda sin poder actuar; la fase espera al invitado conectado. |
| 3 | Cierre y reingreso de un invitado | Recupera rol, estado y fase; la partida no retrocede ni crea otro jugador. |

En las tres partidas también se debe comprobar:

- el anfitrión ve los anuncios en la misma secuencia y no puede actuar antes;
- todos pueden votar y realizar su acción cuando corresponde;
- el resultado coincide en los cinco dispositivos;
- «volver al lobby» incluye a todos y restablece los listos;
- una sala vieja propia en espera desaparece al crear la siguiente.

### Clasificación de fallos

| Prioridad | Ejemplos | Decisión |
|---|---|---|
| Crítica | roles ajenos visibles, estados o ganadores distintos, anfitrión con resultado anticipado | Detener la prueba y corregir. |
| Alta | un cliente no inicia, pierde una cinemática esencial, no puede actuar o no vuelve al lobby | Corregir antes de aumentar jugadores. |
| Media | texto cortado, superposición o demora visual sin pérdida de estado | Registrar y corregir en el mismo ciclo si el cambio es aislado. |

Ante un error, copiar **REPORTE BETA** tanto en el dispositivo afectado como en el anfitrión y
guardar: hora aproximada, fase, ronda, acción anterior y captura. El reporte ya incluye versión,
estado reciente, cantidad de estados recibidos, cinemáticas completadas, tiempos de publicación y
tráfico total de la aplicación desde el inicio de la partida. No contiene nombres, chat, correos ni
UID completos.

## Camino si la prueba sale bien

### Etapa 1 — Observabilidad y salida segura de esperas

Preparar una versión que haga persistente el cierre de cada medición e incluya en un único reporte:

- bytes recibidos y enviados por el proceso;
- recuentos estimados de lecturas y escrituras Firestore;
- reconexiones, cambios de anfitrión y duración de la partida;
- tiempo de recepción e inicio de cada presentación;
- jugador pendiente cuando la barrera de confirmaciones no puede avanzar.

La espera estricta de `0.1.23` evita adelantar al anfitrión, pero puede bloquear una partida si un
cliente permanece marcado como conectado aunque su aplicación esté trabada. La pantalla debe
identificar al pendiente y permitir **seguir esperando**, **reintentar la sincronización** o
**retirarlo de la partida** mediante una decisión visible del anfitrión. No debe existir un timeout
silencioso que salte una cinemática.

### Etapa 2 — Resistencia funcional

Ejecutar pruebas de reconexión durante reparto, noche, amanecer, votación y resultado. Después,
probar caída y reemplazo del coordinador. Cada recuperación debe reconstruirse con identificador de
partida, índice de fase y secuencia; no con el reloj local del teléfono.

### Etapa 3 — Escala

Repetir el flujo completo con 10, 12 y 15 jugadores. En cada tamaño realizar como mínimo:

1. una partida normal;
2. una partida con un dispositivo lento;
3. una partida con una reconexión.

Si un tamaño falla, conservar la prueba menor que sí pasó como referencia y corregir antes de
seguir aumentando.

### Etapa 4 — Reducir carga e información del anfitrión

Con una línea base confiable, aplicar las mejoras ya auditadas:

1. escuchar cambios individuales en RTDB en vez de volver a descargar árboles completos;
2. separar presencia y pulso del estado de juego;
3. hacer que sólo el coordinador escuche estados individuales y publique un resumen pequeño;
4. impedir lectura anticipada de acciones y de chats privados no correspondientes;
5. conservar en Firestore membresía, checkpoints, reparto privado y resultado; evitar pulsos;
6. volver a ejecutar exactamente las mismas pruebas y comparar bytes y operaciones.

En Spark, el teléfono coordinador todavía resuelve parte del juego. Puede quedar sin ventaja visual
y con menos carga, pero eliminar por completo su capacidad de conocer o fabricar estados requiere
un backend autoritativo futuro en Blaze.

## Cómo medir MB por participación

Una **participación** es una persona/dispositivo que completa una partida. Una partida de cinco
jugadores equivale a cinco participaciones; diez partidas de cinco equivalen a cincuenta.

Se usan tres mediciones distintas porque responden preguntas diferentes:

| Fuente | Qué responde | Límite |
|---|---|---|
| Reporte beta del dispositivo | Qué dispositivo recibió más y si el anfitrión carga más | Mezcla RTDB, Firestore, Auth y demás tráfico de la app. |
| `firebase database:profile` | Qué rutas y operaciones RTDB producen el tráfico | Estima payload; no incluye toda la sobrecarga facturable. |
| Uso/Cloud Monitoring de RTDB | Cuántos bytes salieron realmente del servidor | Es el total del proyecto durante la ventana, no una partida identificada. |

Para aproximar la facturación debe usarse `network/sent_bytes_count` o **Downloads** de RTDB,
porque incluye payload, protocolo y cifrado. El profiler se usa para encontrar la ruta responsable,
no para calcular el costo.

### Protocolo de medición

1. Elegir una franja sin otras pruebas ni jugadores y anotar su inicio y fin.
2. Medir 30 minutos de línea base con los mismos cinco dispositivos conectados al lobby, sin jugar.
3. Ejecutar diez partidas comparables de cinco jugadores. Registrar duración, rondas, chat,
   reconexiones y total de participaciones.
4. Copiar el reporte beta del anfitrión y de al menos dos invitados por partida.
5. Durante las pruebas, ejecutar el profiler RTDB para atribuir tráfico a presencia, sincronización,
   chat, acciones y estado autoritativo.
6. En Firebase, obtener `sent_bytes_count` de la ventana y restar la línea base proporcional al
   tiempo. Si se usa la pestaña **Uso**, esperar su actualización y guardar capturas de antes y
   después.
7. Repetir el lote con 10 y 15 jugadores, manteniendo una duración y cantidad de rondas semejantes.

Fórmulas:

```text
MB netos RTDB = (bytes enviados en la ventana - bytes de línea base ajustados) / 1.048.576

MB por participación = MB netos RTDB / participaciones completadas

MB por jugador-hora = MB netos RTDB / suma de minutos conectados de todos los jugadores * 60

GB mensuales = MB por participación * participaciones mensuales / 1.024
```

La cifra por participación sirve para proyectar costo. La cifra por jugador-hora permite comparar
partidas de distinta duración. También se deben guardar por separado las lecturas y escrituras de
Firestore, porque no forman parte de los MB de RTDB.

## Lotes de medición

| Lote | Jugadores | Partidas | Participaciones | Finalidad |
|---|---:|---:|---:|---|
| Base | 5 | lobby inactivo 30 min | 0 | Restar tráfico de conexión y fondo. |
| Normal | 5 | 10 | 50 | Obtener la primera cifra estable. |
| Carga media | 10 | 5 | 50 | Detectar crecimiento por listeners y estado grupal. |
| Carga máxima | 15 | 4 | 60 | Validar el objetivo de lanzamiento. |
| Adverso | 5 o 10 | 3 | 15–30 | Chat intenso, cliente lento y reconexión. |

No se debe sacar una conclusión de una única partida. La primera cifra oficial será la mediana de
las partidas válidas y se acompañará con el peor caso observado.

## Criterio de beta abierta

El online estará listo para una beta abierta limitada cuando cumpla todo lo siguiente:

- tres partidas consecutivas limpias en 5, 10 y 15 jugadores;
- ningún spoiler del anfitrión, salto de presentación esencial o diferencia de resultado;
- recuperación comprobada de invitado y coordinador;
- retorno conjunto al lobby y limpieza de salas verificadas;
- al menos 30 partidas completas en la versión candidata sin fallos críticos o altos sin resolver;
- MB por participación, peor caso y operaciones Firestore medidos;
- reporte de diagnóstico recuperable aunque la aplicación se cierre;
- prueba interna o cerrada de Google Play antes de ampliar la cantidad de testers.

## Siguiente decisión

La próxima acción depende únicamente de la prueba de cinco jugadores:

- con un fallo crítico o alto, se abre una corrección pequeña basada en los dos reportes beta;
- con tres partidas limpias, se implementa la Etapa 1 y se inicia la medición controlada;
- Blaze se evalúa después de conocer consumo, concurrencia y fallos reales. Todo el trabajo de
  sincronización visual, métricas y protocolo realizado en Spark se conserva para esa migración.
