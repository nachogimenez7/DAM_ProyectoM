# Piloto privado Android V3 — preparación del 8/10/2026

La clave de subida está en la otra computadora del usuario. Por su decisión, la
firma y la subida quedan para después de entregar y sincronizar los cambios.
No se generó otra clave ni se publicó una versión. No abrir V3 a todos ni promover
a beta abierta sin el OK explícito del usuario.

## Estado actual

- Paquete: `com.traidores.juego`; versión preparada: 52 / 0.1.51.
- Release compila con R8 y `traidoresServerOnlineV3=true`; emuladores deshabilitados.
- El AAB de esta Mac no tiene firma de subida. No subir ese archivo directamente.
- Backend y reglas ya desplegados; ambos interruptores siguen apagados.
- El menú admite la lista opcional `allowedUids`. Vacía o mal formada bloquea el
  acceso; ausente permite el comportamiento general del interruptor. Durante el
  piloto siempre debe existir una lista con los UID concretos de los testers.
- Al cambiar de cuenta se recalcula el permiso con el nuevo UID, reutilizando la
  lectura de configuración de esa entrada al menú. La lista del menú no sustituye
  la validación del servidor: este exige creador autorizado y sala autorizada.

## Cuando retomemos la firma en la otra computadora

1. Bajar los cambios entregados y abrir este proyecto en Android Studio.
2. Verificar que Play no tenga ya un artefacto con versionCode 52. Si ya se usó,
   aumentar el código y ajustar el mínimo de configuración antes de distribuir.
3. Para que el asistente de firma genere V3, añadir temporalmente
   `traidoresServerOnlineV3=true` al archivo de propiedades del usuario de Gradle:
   `%USERPROFILE%\.gradle\gradle.properties` en Windows, o
   `~/.gradle/gradle.properties` en macOS/Linux. Conservar las demás propiedades.
   Este ajuste es local; no agregar contraseñas ni rutas de la clave al repositorio.
4. Android Studio → Build → Generate Signed Bundle/APK → Android App Bundle → módulo
   app. Elegir el `.jks` existente, su alias y contraseñas en el asistente. Seleccionar
   Release. No crear una clave distinta. Usar el AAB generado por ese asistente.
5. Verificar en `app/build/generated/source/buildConfig/release/com/traidores/juego/BuildConfig.java`:
   `SERVER_ONLINE_V3 = true`, `USE_ONLINE_AUTHORITY_EMULATOR = false`, paquete y versión.
   Si cambió la ruta generada, localizar el BuildConfig de Release, no el de Debug.
6. Retirar después la propiedad temporal del archivo de Gradle del usuario.

El procedimiento de firma está descrito en la [documentación oficial de Android](https://developer.android.com/studio/publish/app-signing).

## Play Console y certificado

En la cuenta de desarrollador Bandido Games, seleccionar Traidores:

1. Probar y publicar → Pruebas → Prueba interna. En Testers, añadir la cuenta Google
   que usa Play Store en el A56 y guardar la lista. En Versiones, crear una versión,
   subir el AAB firmado, revisar los avisos y distribuir únicamente en prueba interna.
2. Abrir el enlace de participación con esa misma cuenta en el A56, aceptar e instalar
   desde Play Store. Confirmar que la versión instalada sea 0.1.51 / 52.

Estos pasos corresponden al [canal de prueba interna de Play](https://support.google.com/googleplay/android-developer/answer/9845334?hl=es).

En la página **Firma de aplicaciones / App signing** de Play Console, copiar SHA-256
del **certificado de firma de aplicaciones**, que firma la app instalada desde Play.
El certificado de subida solo valida el AAB enviado. No confundirlos ni usar la huella
Debug. Si hay rotación de la clave, revisar los certificados aplicables a la entrega.
La huella concreta todavía no se verificó; no hay un valor inventado en este documento.
La distinción está explicada en [Firma de aplicaciones de Play](https://support.google.com/googleplay/android-developer/answer/9842756?hl=es).

Registrar/verificar esa SHA-256 en Firebase → Configuración del proyecto → app Android
`com.traidores.juego`, y en App Check → esa app → proveedor Play Integrity. Revisar
también SHA-1 para el acceso con Google. Play Integrity debe estar vinculado al mismo
proyecto Cloud de Firebase: `traidores` (99323018581). Mantener enforcement; no usar
un token Debug para aprobar una entrega Release. Véase [App Check con Play Integrity](https://firebase.google.com/docs/app-check/android/play-integrity-provider).

## Activación privada: únicamente después de instalar desde Play

Todavía no se ejecutó ninguna de estas modificaciones.

1. Identificar el UID de Firebase Auth del usuario instalado. No es el número público
   del perfil, el correo ni el identificador de Play Games.
2. En Firestore, habilitar su menú con `config/onlineV3`:

   ```json
   {"enabled": true, "minVersionCode": 52, "allowedUids": ["UID_DEL_USUARIO"]}
   ```

   Conservar cerrado el interruptor del servidor mientras crea la sala privada.
   Tomar su ID de documento Firestore; no asumir que es el código visible de invitación.
3. En `onlineMaintenance/serverAuthority`, habilitar solo esa combinación:

   ```json
   {"enabled": true, "allowedHostUids": ["UID_DEL_USUARIO"], "allowedRoomIds": ["ID_DOCUMENTO_SALA"]}
   ```

4. Primero aprobar la mesa en el A56. El inicio requiere el mínimo de jugadores:
   no equivale a una partida de un solo teléfono. Para la partida completa, añadir
   a `allowedUids` únicamente los UID de los otros testers acordados. La lista
   `allowedHostUids` puede seguir conteniendo solo al creador de esta sala.
5. Probar partida completa, reconexión, abandono, resultado, historial y revancha.
   Registrar hora, sala/matchId, jugadores y acciones para cruzar consumo y latencia
   con Cloud. La medición previa de emotes es parcial, no la factura de una partida.
6. Probar apagado desde la consola con la misma build instalada desde Play:
   servidor `enabled=false`, listas de host/sala vacías; cliente `enabled=false`,
   `minVersionCode=52`, `allowedUids=[]`. Salir y volver a entrar al menú para releer
   configuración. Debe impedir partidas nuevas; las ya empezadas siguen y permiten
   recuperación. El cliente no sondea el interruptor durante el gameplay.
7. Probar aislamiento con el APK **real** 0.1.50. Conservar el artefacto original o
   descargar el APK firmado correspondiente desde Play Console. Probarlo en otro
   dispositivo/emulador: no desinstalar el A56 sin tener registrada su cuenta/UID.

## Evidencia y pendientes

- Nueva política del piloto: tres pruebas unitarias aprobadas (UID autorizado,
  ajeno/ausente, listas vacías/mal formadas, apagado y versión mínima).
- Bundle Release compilado; los escenarios anteriores de mesa/emotes y sus resultados
  siguen en `AJUSTES_MESA_EMOTES_V3_ANDROID_2026-10-08.md`.
- Pendientes: firma, distribución interna, huella real de Play, App Check Release,
  aprobación visual en A56, partida completa Cloud, consumo total, apagado con Play
  e instalación del APK antiguo. No marcarlos como aprobados por compilar el AAB.
