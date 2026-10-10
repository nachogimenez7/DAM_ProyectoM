# Resultado de la repetición de fotos — 9/10/2026

## Para Claude

Se recompiló el árbol actual como hostqa 53 / 0.1.52 y se instaló con actualización, conservando datos, en el A56 y los emuladores. Firebase real; SERVER_ONLINE_V3=false y USE_ONLINE_AUTHORITY_EMULATOR=false. No se editaron los archivos Android de Claude, no se desplegó backend y no hubo commit ni push.

Mismas cuentas QA: Jugador #48 en A56 y Nacho #49 en Pixel_10. Se recuperó la sala PLLPGU. Un segundo emulador participó como invitado Mufa 2109 para completar tres clientes Android reales; no fueron bots ni resultados sembrados.

**El fallo original de representación está corregido.** El muñeco tejido publicado en Storage aparece en las capturas revisadas, donde antes aparecía la ilustración de reemplazo.

| Comprobación | Resultado | Captura en output/fotos-corregidas-2026-10-09/ |
| --- | --- | --- |
| A56: menú después de actualizar y reabrir | Correcto; muestra el muñeco | a56-menu-reapertura.png |
| A56: tarjeta online propia | Correcto | a56-tarjeta-online.png |
| A56: lobby propio | Correcto | a56-lobby-original.png |
| Pixel_10: lobby de otra cuenta | Correcto | emulador-lobby-original.png |
| Pixel_10: perfil público de #48 | Correcto | emulador-perfil-publico-original.png |
| Pixel_10: carta de #48 en la mesa nocturna | Correcto | emulador-mesa-original.png |
| A56: retrato propio en mesa | Capturado | a56-mesa-original.png |
| Pixel_10: foto en lobby con modo avión, wifi y datos apagados | Visible, con atenuación de jugador desconectado | emulador-lobby-sin-conexion.png |

La caché de disco contiene el JPEG de 68.859 bytes en cache/profile_photos. La prueba sin conexión anterior se hizo con el proceso vivo: confirma la foto ya cargada sin red, **no** una reapertura en frío desde disco. Se restauró la conexión del emulador.

Logs recogidos: a56-app.log y emulador-app.log. No contienen profile_photo_download_failure. No copiar URLs con tokens ni credenciales a documentación pública.

## Límites y pendientes

El usuario vio un funcionamiento satisfactorio y pidió cerrar la tanda. El A56 volvió a bloquearse al continuar; no se insistió con más partidas. Se volvió al lobby durante el recorrido, así que no se obtuvieron capturas válidas de recuento, desempate ni ganadores en esta tanda. Una captura durante el cierre de la votación no equivale a verificar esas pantallas.

Quedan por repetir: cambiar la foto y comprobar la nueva revisión en la otra cuenta; reapertura en frío sin conexión; perfil propio ampliado; recuento, desempate y ganadores debajo del rol. No se afirma paridad completa ni cierre de toda la regresión online.

Nota menor: el lobby conserva la descripción accesible «Foto de Play Juegos de Jugador» aunque la imagen procede de Storage. La imagen es correcta; conviene ajustar el texto accesible cuando Claude continúe.

APK de esta tanda: output/fotos-corregidas-2026-10-09/traidores-hostqa-fotos-53.apk. La app habitual del teléfono se conservó. Se cerraron los clientes QA al finalizar para detener sus listeners; V3 no se abrió ni se modificó.
