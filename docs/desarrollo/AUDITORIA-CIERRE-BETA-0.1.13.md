# Auditoría de cierre — beta 0.1.13

Fecha: 20 de agosto de 2026
Objetivo: preparar una **prueba cerrada**, no producción abierta.

## Estado general

Traidores ya tiene un núcleo jugable amplio: modo local contra IA, online con salas públicas y privadas, tres mapas, roles exclusivos, perfiles, historial, chat y sincronización de partidas. La versión `0.1.13` agrega un tutorial de primer inicio, compacta Ayuda, incorpora normas iniciales del online, limpia Opciones y corrige la lista de salas públicas.

La base es adecuada para una prueba cerrada con compañeros. Todavía no conviene abrir matchmaking al público general hasta completar moderación, publicación y pruebas de dispositivos.

## Resuelto en 0.1.13

- Las salas públicas vuelven a mostrarse sin intentar leer presencia privada antes de entrar.
- Se reducen lecturas de Firebase: una consulta limitada de Firestore reemplaza una lectura RTDB por sala más reintento.
- La entrada sigue validando estado y cupo dentro de una transacción.
- Tutorial saltable de cuatro pasos al entrar al primer lobby, repetible desde Ayuda.
- Ayuda incorpora un índice desplegable, diferencias Local/Online, controles y reconexión.
- El primer ingreso al online exige aceptar normas breves de comunidad.
- Ayuda diferencia silencio local, silencio de mesa, reporte y expulsión del anfitrión.
- El perfil del jugador ya permite reportar con un motivo y detalle opcional.
- Se elimina por completo `PROBAR FIREBASE` de Opciones.
- El selector de inglés queda oculto y las preferencias viejas se normalizan a español.
- El modo de prueba de tres jugadores conserva Asesino, Médico y Detective.
- Los presets de roles representan roles ausentes con cero de manera consistente.
- El inicio online espera y libera correctamente la entrada coordinada de todos los clientes.
- Al iniciar se normaliza el orden autoritativo de jugadores para que acciones y roles conserven su UID.
- La expulsión saca al afectado del lobby obsoleto y muestra un único botón para volver al online.
- Suite JVM: 511 pruebas aprobadas.
- Reglas generales y de invitados verificadas en emulador y publicadas en Firebase el 20 de agosto de 2026.

## Prioridad 0 — antes de entregar la beta cerrada

### Prueba online real

Probar con cinco teléfonos distintos:

1. Cuenta A crea sala pública y permanece en el lobby.
2. Cuenta B abre Buscar partida y confirma que la sala aparece.
3. C entra desde la lista; D entra con código; E entra tras cerrar y reabrir la app.
4. Todos marcan LISTO e inician una partida de cinco jugadores.
5. Completar al menos noche, amanecer, debate y votación.
6. Desconectar y reconectar un invitado durante lobby y durante gameplay.
7. Confirmar que una sala privada nunca aparece en Buscar partida.
8. Confirmar que una sala llena o iniciada rechaza nuevos ingresos aunque todavía figure unos segundos en una lista vieja.

### Firma y Play App Signing

El proyecto no tiene configuración de firma release local. Para Play Console hay que activar Play App Signing y generar una clave de carga. El APK directo de la web puede seguir siendo debug durante pruebas, pero no debe confundirse con el AAB de la prueba cerrada.

### Servicios de Google y Firebase

- Verificar en Play Console y Firebase las huellas SHA-1/SHA-256 de la clave de carga y de Play App Signing.
- Verificar Play Integrity/App Check con una instalación entregada por el canal cerrado.
- Confirmar que Firestore, Realtime Database, Auth y Crashlytics apuntan al proyecto de producción correcto.
- La corrección del navegador sigue siendo de cliente y conserva privada la presencia; las reglas nuevas publicadas refuerzan la identidad de las acciones durante gameplay.
- El índice compuesto remoto de `partidas` por estado, visibilidad y actualización ya está publicado y fue verificado por CLI.

## Prioridad 1 — antes de una beta abierta o producción

### Seguridad y moderación

Hoy existen silenciamiento local, silencio de mesa, reporte y expulsión a cargo del anfitrión. También hay aceptación inicial de normas. Antes de admitir desconocidos falta convertir esos reportes en un proceso operativo real.

Pendientes recomendados:

- las reglas de `reportes` ya están desplegadas; falta verificar el flujo operativo con un reporte real de prueba;
- definir quién revisa los reportes, con qué frecuencia y qué evidencia se conserva;
- crear un punto de contacto de seguridad y una vía de apelación;
- publicar las normas también fuera de la app, con una URL estable;
- decidir criterios y duración para suspensiones globales del online.

### Edad, anuncios y compras

El objetivo declarado es adolescente/adulto y excluye menores de 13 años. Antes de incorporar monetización:

- definir grupos de edad exactos en Play Console;
- integrar un SDK de anuncios compatible con el público declarado;
- usar Google Play Billing para bienes digitales;
- actualizar política de privacidad y Seguridad de datos;
- diseñar anuncios para que nunca interrumpan acciones cronometradas, debate o votación;
- dejar compras y recompensas fuera del equilibrio competitivo.

La beta 0.1.13 no incorpora todavía anuncios ni Billing.

### Estabilidad observable

- Revisar Crashlytics después de cada tanda de cinco dispositivos.
- Registrar versión, modelo, Android, modo y fase en cada reporte de prueba.
- Probar red lenta, cambio Wi-Fi/datos y bloqueo de pantalla.
- Confirmar limpieza de salas abandonadas y ausencia de listeners duplicados.

## Prioridad 2 — pulido de producto

- Revisar fuentes grandes y pantallas pequeñas; el gameplay conserva muchas dimensiones fijas.
- Probar accesibilidad con TalkBack, contraste y objetivos táctiles.
- Agregar tutorial específico de online sólo si los testers siguen trabándose después de la nueva Ayuda.
- Completar traducción al inglés en la materia correspondiente antes de volver a mostrar el selector.
- Reducir gradualmente responsabilidades de `GameplayMockActivity` y renombrarla cuando se planifique una refactorización segura.
- Evaluar soporte de tablets solamente después de cerrar teléfono vertical.

## Web y materiales

- Reemplazar el APK de prueba por `0.1.13` y actualizar versión/tamaño visibles.
- Corregir la FAQ que describía el modo local como varios celulares en la misma Wi-Fi; el modo real es un jugador contra IA.
- Mantener el sitio sin desplegar hasta completar la prueba rápida del APK local.
- Usar la ficha documentada en `Google Play/FICHA_PLAY_CONSOLE_ES-419.md`.

## Criterio de salida de la beta cerrada

La beta puede entregarse cuando:

- el build y las 511 pruebas siguen en verde;
- el APK se instala en al menos dos teléfonos;
- una sala pública aparece y permite completar una partida de cinco jugadores;
- una sala privada sólo admite código;
- reconexión y abandono no bloquean al resto;
- tutorial, Ayuda, Opciones y enlaces legales funcionan;
- no aparecen cierres críticos nuevos en Crashlytics.
