# Preparación pública Android 0.1.50 (código 51)

La partida 0.1.49 instalada desde Google Play cerró las comprobaciones online previstas; detalles en `prueba-recuperacion-2026-10-01.md`.

## Cambios de presentación

- Release oculta contadores y controles de medición de Opciones. Debug conserva copia del reporte y reinicio.
- Partida, lobby y Opciones públicas ofrecen Reportar un problema. El formulario pide solo la descripción; conserva el nombre de perfil y un asunto predeterminado sin pedir que se escriban otra vez. El comentario general del menú mantiene su formulario anterior.
- En partidas y lobbies online, se captura al abrir el formulario un resumen de la fase y eventos de diagnóstico del reporte existente. No incluye chats, correos ni UID completos en ese resumen. La descripción permanece íntegra hasta 600 caracteres; el resumen se ajusta al límite existente de 1200 del mensaje. El buzón también conserva el UID autenticado, nombre, versión y dispositivo según su esquema anterior.
- En modo local, las opciones para que la IA siga votos del chat, no mate al jugador o no lo vote están disponibles en release. Forzar empates sigue reservado a debug. Se elimina la explicación pública de herramientas debug.
- Estilos del perfil y selector de emotes explican que algunos cosméticos están abiertos durante la beta y su disponibilidad puede cambiar. No se modifican desbloqueos ni se añaden pagos.

## Recepción y límites

Los envíos reutilizan `comentarios` en Firestore, sin un listener adicional ni cambios del protocolo online. La versión final añade `limitesComentarios/{uid}`: una transacción lee el contador propio y escribe atómicamente el mensaje y el contador (dos escrituras por envío aceptado, más lecturas del contador y de reglas). Se mantiene el botón deshabilitado mientras se envía y solo se confirma recepción tras éxito del servidor. La pantalla de error permite reintentar.

El campo destino del documento NO envía un email por sí mismo. La revisión de mensajes se realiza desde Firebase; no se ha configurado reenvío por correo ni desplegado un servicio nuevo. Los envíos reales iniciales están descritos abajo; todavía no se ha comprobado un envío real con la transacción de cuota desde el APK final.

## Envíos comprobados

Se consultaron únicamente los dos comentarios más recientes mediante acceso administrativo. Ambos llegaron desde 0.1.50, a las 03:05:14 y 03:05:42 de Argentina del 2 de octubre, con estado pendiente. Ninguno contiene el separador de resumen de partida. Se pidió aclarar si el envío desde gameplay era local (comportamiento esperado) u online (requiere investigar la falta de contexto). No se imprimieron nombres, UID ni contenidos del mensaje.

En la primera revisión las reglas solo validaban autenticación, campos y tamaño; no limitaban la frecuencia. La protección implementada después se describe abajo.

El usuario confirmó que el envío desde gameplay fue contra la IA: la ausencia de resumen online es esperada. Recepción real desde menú y partida local comprobada. Sigue sin verificarse un envío con contexto de partida online.

## Cuota de reportes

Máximo tres mensajes por cuenta en una ventana de 24 horas desde el primer envío; separación mínima de cinco minutos. Las reglas usan tiempo del servidor y validan contador y mensaje juntos: no admiten crear mensajes sin contador, reiniciarlo antes de tiempo, borrarlo, actualizar mensajes previos ni enviar varios mensajes con una única actualización del contador. Los clientes solo pueden consultar su propio contador. El reloj local permite anticipar el aviso en el formulario; no puede eludir las reglas.

Las pruebas de Firestore pasaron, incluidas primera creación, intervalo, tercer y cuarto mensaje, reinicio de ventana, acceso de otra cuenta, escritura del contador sin mensaje, lote de varios mensajes y envíos simultáneos. También pasaron pruebas Android del límite y compilación debug/release.

Las reglas de producción se compararon con el archivo local: solo difieren en las nuevas restricciones de comentarios. Activar la cuota requiere que el cliente use la transacción: versiones anteriores conservan el juego online, pero sus formularios sin contador serán rechazados. No hay excepción por versión que permita saltarse la cuota. Se debe distribuir el APK/AAB final de 0.1.50.

La cuota es por UID autenticado. No constituye un límite global ni protección frente a creación masiva de cuentas; consultas e intentos rechazados también pueden generar lecturas. No se activó facturación ni se desplegaron funciones o servicios de correo.

Despliegue completado el 2 de octubre a las 03:13 de Argentina, únicamente `firestore:rules` en el proyecto traidores. La lectura posterior de las reglas publicadas confirmó coincidencia exacta con el archivo local. El APK final se compiló, pero no se instaló automáticamente sobre los dispositivos ni se publicó un AAB.
