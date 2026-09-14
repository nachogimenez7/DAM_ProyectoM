# Corrección online 0.1.23

## Problemas observados

- Un cliente podía recibir la liberación de la partida unos instantes después que los demás y comenzar su presentación en otro momento.
- La ruta de recuperación abría directamente el gameplay aunque la partida siguiera en el reparto inicial, omitiendo la cinemática.
- Los timeouts de inicio y de anuncios públicos podían dejar avanzar al coordinador con un cliente conectado todavía sin confirmar.
- Una aplicación cerrada a la fuerza podía dejar publicada una sala de espera vieja.

## Cambios

- La liberación de entrada guarda una marca de tiempo del servidor. Todos los clientes apuntan a una misma ventana futura de inicio de la cinemática.
- Un reingreso en `REPARTO` con `phaseIndex == 0` vuelve a pasar por la presentación obligatoria.
- La primera noche requiere que todos los jugadores esperados hayan cargado la misma plantilla y confirmado la lectura del rol.
- Los anuncios públicos requieren el ACK de cada jugador conectado. Un timeout ya no adelanta la fase.
- Antes de crear una sala, una cuenta elimina únicamente sus propias salas antiguas que continúen en estado `esperando` y bajo su autoridad. También elimina el código reservado y la raíz RTDB correspondiente.
- La limpieza del cliente funciona en Spark. No usa Functions ni requiere Blaze.

## Alcance de la limpieza en Spark

El cliente no puede borrar salas ajenas. Esto evita que un APK modificado destruya partidas de otras personas. La limpieza global programada sigue siendo una tarea de backend para una futura activación de Blaze. El navegador ya oculta salas sin actividad después de su ventana de vigencia.

## Prueba manual recomendada

1. Instalar el mismo APK 0.1.23 en los cinco emuladores.
2. Cerrar cualquier partida recuperable anterior y crear una sala nueva con la cuenta que creó `AAAAAAAAA`.
3. Buscar salas desde los otros cuatro emuladores y comprobar que la sala anterior desapareció.
4. Marcar los cinco jugadores como listos e iniciar.
5. Confirmar que todos muestran el reparto y que ninguno abre la primera noche antes de que los cinco cierren su carta de rol.
6. Durante amanecer, muerte y recuento, dejar deliberadamente un emulador lento. La fase debe esperar su presentación mientras siga conectado.
7. Cerrar y abrir un cliente durante el reparto inicial. Al recuperar, debe volver a mostrar la presentación antes de entrar al gameplay.

## Validación automática

- 645 pruebas unitarias Android, sin fallos.
- Reglas de Firestore, aprobadas en emulador.
- Reglas de Realtime Database, aprobadas en emulador.
- 17 pruebas unitarias del backend, sin fallos.
- `lintDebug`, `assembleDebug` y `assembleRelease`, correctos.
