# Para Codex: prueba de los anuncios en el online con anfitrión (10/10/2026)

Claude implementó los anuncios de video entre partidas (AdMob, solo anuncios de prueba de
Google). La regla de frecuencia y el caso vs IA ya están probados. Falta lo que necesita
3 clientes reales en una sala.

## Reglas decididas por el usuario

- Un video cada 2 partidas terminadas, con 4 minutos de separación y sin videos en las
  2 primeras partidas del jugador. Sin banners ni recompensados.
- **Nunca** en el menú al abrir el juego ni antes de arrancar una partida. Si alguien
  cierra el juego, ese video se pierde: no se lo persigue.
- Momentos en que aparece:
  - **vs IA:** al salir de la pantalla de victoria.
  - **Online, invitado:** al volver al lobby después de la partida, antes de LISTO,
    1,5 s después de que el lobby se asienta.
  - **Online, anfitrión:** nunca dentro de la sala; al salir de la sala, en el menú
    online.

## Código

- `InterstitialAds.kt`: regla (`InterstitialAdPolicy`), consentimiento UMP, carga y
  muestra. `AdFreeEntitlement` devuelve `false` hasta que existan las compras.
- `LobbyActivity.maybeShowGuestAdAfterMatch()`: vuelve a comprobar todo en el último
  momento. No muestra el video si el teléfono es anfitrión (`hostId` o `hostActivoId`),
  si la sala es V3, si la sala está `en_juego`, si el jugador ya está LISTO o si está
  saliendo o entrando a una partida.
- `LobbyActivity.onStop`: al salir de la sala marca `onLeftOnlineRoom()`, y
  `OnlineModeActivity.onResume` muestra el video una sola vez.
- Debug: `-PtraidoresAdsDemo=true` muestra un video después de **cada** partida (sin los
  mínimos). Release no muestra anuncios hasta recibir
  `-PtraidoresAdmobAppId` / `-PtraidoresAdmobInterstitialId`.
- Las pruebas Android pasan (746, incluidas 4 de `InterstitialAdPolicyTest`).

## Build

Con tu `hostqa.init.gradle` y un `app/src/debug/google-services.json` temporal con el
paquete `com.traidores.juego.hostqa` (después borralo):

```sh
node scripts/with-jdk.cjs sh ./gradlew :app:assembleDebug -PtraidoresAdsDemo=true \
  --init-script output/beta-anfitrion-cloud-2026-10-09/hostqa.init.gradle
```

## Qué probar (A56 como anfitrión, más 2 emuladores, sala de prueba de 3)

1. Terminar una partida. Los dos **invitados** ven el video al volver al lobby. El
   **anfitrión no** lo ve.
2. Durante el video de un invitado, el anfitrión intenta arrancar. ¿Aparece «Jugar con
   presentes»? ¿Deja afuera al invitado que está mirando? Registrá exactamente qué pasa.
   Es el punto que más importa.
3. Al cerrar el video, el invitado sigue en la sala, sin desconexión ni pérdida de lugar.
   Si la partida ya arrancó, entra.
4. Que ningún video provoque `host_handoff_*` ni `host_promoted`.
5. El anfitrión sale de la sala y ve el video en el menú online. Al volver a entrar al
   menú, no lo ve de nuevo.
6. Reabrir la app no muestra ningún video en el menú principal.

Mandá logs (`ads_*`, `host_*`) y capturas. No edites los archivos de Android de Claude.
Sin commit, push ni despliegue.
