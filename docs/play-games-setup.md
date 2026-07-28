# Google Play Games: estado y configuración

Fecha de revisión: 25 de julio de 2026.

## Estado del código

La integración está implementada, pero queda **inactiva de forma segura** hasta completar los
IDs de Play Console en
`app/src/main/res/values/play_games_ids.xml`.

Ya está implementado:

- autenticación automática de Play Games Services v2;
- vinculación con Firebase Auth conservando el `uid` anónimo;
- recuperación de una cuenta de Play Games ya existente;
- refresco del token de Firebase para que las reglas reconozcan la cuenta;
- gamertag como nombre inicial;
- espejo de los 10 logros locales;
- tablas de victorias y partidas;
- un snapshot de perfil e historial con resolución de conflictos;
- lista de amigos con permiso opcional;
- accesos a logros, tablas y amigos desde el perfil.

No hace falta desplegar reglas de Firestore ni de Realtime Database para activar Play Games.
Tampoco hay que subir un APK todavía. La activación depende primero de Play Console y Firebase.

## Datos confirmados del proyecto

| Dato | Valor |
|---|---|
| Firebase / Google Cloud project ID | `traidores` |
| Firebase project number | `99323018581` |
| Android application ID | `com.traidores.juego` |
| SHA-1 de la clave debug | `03:5D:41:08:AF:A4:4B:34:1D:6B:2B:99:D5:C3:1E:C4:4A:45:8E:DC` |
| SHA-256 de la clave debug | `71:20:8C:F9:65:0F:47:B7:60:DF:AE:45:64:E5:BD:FC:36:38:0C:75:34:00:08:38:E3:38:AC:50:2B:88:B8:A8` |

El `google-services.json` actual todavía no contiene clientes OAuth. Hay que volver a
descargarlo desde Firebase después de crear y vincular las credenciales.

## Orden exacto en las consolas

### 1. Crear la ficha Android

En Play Console: **Inicio > Crear aplicación**.

Valores recomendados:

- nombre: `Traidores`;
- idioma predeterminado: español de Latinoamérica;
- tipo: juego;
- precio: gratis;
- package name cuando lo solicite: `com.traidores.juego`;
- aceptar Play App Signing.

Crear la ficha no publica la app.

### 2. Crear o vincular el proyecto de Play Games Services

Dentro de la app:
**Crecer usuarios > Play Games Services > Configuración y gestión > Configuración**.

Cuando pregunte por el proyecto de Google Cloud, elegir **usar un proyecto existente** y
seleccionar el mismo proyecto de Firebase: `traidores`.

No dejar que Play Console cree otro proyecto.

Anotar el **ID numérico de aplicación de Play Games** que muestre la configuración. Ese valor
irá en `game_services_project_id`; aunque normalmente está relacionado con el proyecto de
Google Cloud, no se debe adivinar ni copiar el project number sin confirmarlo en Play Console.

### 3. Credencial Android de depuración

En **Play Games Services > Configuración y gestión > Credenciales**:

- package name: `com.traidores.juego`;
- SHA-1: `03:5D:41:08:AF:A4:4B:34:1D:6B:2B:99:D5:C3:1E:C4:4A:45:8E:DC`.

Esto habilita las pruebas del APK generado por Android Studio.

### 4. Credencial Android de Play App Signing

La huella de release no existe todavía en el proyecto. Se obtiene después de preparar la
primera versión en:
**Configuración > Integridad de la aplicación > Firma de aplicaciones de Play**.

Copiar su SHA-1 y crear una segunda credencial Android de Play Games con el mismo package.
No usar la huella debug para una versión distribuida por Play.

### 5. Credencial Web

Crear una credencial OAuth de tipo **Aplicación web** vinculada al mismo proyecto.

Guardar:

- el client ID web, que termina en `.apps.googleusercontent.com`;
- el client secret.

El client ID se copia a `play_games_web_client_id`. El client secret **no se guarda en el
repositorio ni en el APK**.

### 6. Activar Play Games en Firebase Auth

En Firebase Console:
**Authentication > Sign-in method > Play Games**.

Pegar allí el client ID y el client secret de la credencial Web y habilitar el proveedor.
Correo/contraseña debe seguir habilitado.

Después, descargar de nuevo `google-services.json` para Android y reemplazar el archivo local
`app/google-services.json`.

### 7. Testers

En **Play Games Services > Configuración y gestión > Testers**, agregar la cuenta de Google que
está iniciada en el dispositivo de prueba.

Mientras la configuración de Play Games no esté publicada, una cuenta que no sea tester no
podrá autenticarse.

### 8. Partidas guardadas

En la configuración de Play Games, editar propiedades y activar **Partidas guardadas**.
La propagación puede demorar hasta 24 horas.

### 9. Logros

El código tiene **10 logros**, no 11 como decía inicialmente la spec. Todos deben crearse como
logros estándar: la app los desbloquea al cumplir el objetivo.

| ID local | Nombre actual | Recurso donde pegar el ID remoto |
|---|---|---|
| `profile_created` | Te agradezco infinitamente | `play_games_achievement_profile_created` |
| `assassin_kills_25` | Ser primero no es lo importante, es lo único | `play_games_achievement_assassin_kills_25` |
| `jester_wins_5` | Y me gusta el rol, el maldito rol | `play_games_achievement_jester_wins_5` |
| `expel_all_killers` | Hoy dormís afuera | `play_games_achievement_expel_all_killers` |
| `deserter_wins_10` | Lo que no te mata, te infecta | `play_games_achievement_deserter_wins_10` |
| `mercenary_same_target_3` | Ya nadie va a escuchar tu voto | `play_games_achievement_mercenary_same_target_3` |
| `villager_survives_12` | Sobreviviendo dije, sobreviviendo | `play_games_achievement_villager_survives_12` |
| `mayor_power_wins_15` | El alcalde que fue prometido | `play_games_achievement_mayor_power_wins_15` |
| `total_wins_50` | Quién te ha visto y quién te ve | `play_games_achievement_total_wins_50` |
| `traidores_supremo` | Traidores Supremo | `play_games_achievement_traidores_supremo` |

La propuesta de los 10 íconos y sus puntos está en
[`achievement-icon-concepts.md`](achievement-icon-concepts.md). Los textos completos ya están
en `ProfileCustomizationCatalog.kt`.

### 10. Tablas

Crear dos tablas con orden descendente y mejor puntaje:

| Tabla | Recurso donde pegar el ID |
|---|---|
| Victorias totales | `play_games_leaderboard_total_wins` |
| Partidas jugadas | `play_games_leaderboard_total_matches` |

### 11. Completar recursos y publicar la configuración de PGS

Pegar el ID de aplicación, el client ID web, los 10 IDs de logros y los 2 IDs de tablas en
`app/src/main/res/values/play_games_ids.xml`.

Después publicar los cambios de Play Games Services en la consola. Publicar esa configuración
no equivale a lanzar la app en producción.

## Prueba de aceptación

Usar un dispositivo físico o un emulador con Play Store:

1. iniciar la cuenta tester en Google Play Games;
2. instalar el APK debug;
3. abrir la app y comprobar que el perfil dice “Cuenta vinculada con Google Play Games”;
4. crear una sala sin registrar correo;
5. abrir logros, tablas y amigos desde el perfil;
6. terminar una partida y comprobar los dos rankings;
7. cambiar el perfil, reinstalar y verificar que se recuperen perfil, historial y número.

La negativa al permiso de amigos no debe bloquear ninguna otra función.

## Pendientes antes de subir una versión a Play

- Verificar en dispositivos Android 15 y 16 la migración ya aplicada a `targetSdk 36` y
  `compileSdk 36.1`, especialmente barras del sistema, teclado y navegación hacia atrás.
- Crear una clave de subida y configurar la firma release. No se debe guardar su contraseña en
  Git.
- Generar un Android App Bundle (`.aab`), que es el formato normal de Play, no repartir el APK
  release manualmente.
- Completar ficha, política de privacidad, seguridad de datos, clasificación de contenido,
  público objetivo y el resto de declaraciones de Play Console.
- Por ser una cuenta personal nueva, producción exige un test cerrado con al menos 12 testers
  inscritos continuamente durante 14 días. El test interno puede empezar antes y no publica la
  app al público.
- Revisar la política de chat anónimo y público objetivo antes de producción; el juego tiene
  chat entre usuarios y moderación todavía en desarrollo.

## Decisión pendiente sobre conflictos

La implementación actual sigue la spec: gana el snapshot con más partidas; a igualdad, el más
reciente. Es determinista, pero un cambio de avatar hecho en un segundo dispositivo con menos
partidas podría perderse. Antes de producción conviene reemplazarlo por una fusión por campos o
mostrar una elección al jugador.

## Referencias oficiales

- [Configurar Play Games Services](https://developer.android.com/games/pgs/console/setup)
- [Migrar e integrar autenticación PGS v2](https://developer.android.com/games/pgs/android/migrate-to-v2)
- [Partidas guardadas](https://developer.android.com/games/pgs/android/saved-games)
- [Acceso a amigos](https://developer.android.com/games/pgs/android/friends)
- [Requisitos de pruebas para cuentas personales nuevas](https://support.google.com/googleplay/android-developer/answer/14151465)
- [Requisitos de target API](https://support.google.com/googleplay/android-developer/answer/11926878?hl=es)
