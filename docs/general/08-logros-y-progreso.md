# Logros y progreso

## Estado actual

La app tiene un detector local de logros en `AchievementTracker.kt`. El detector corre sobre partidas locales y guarda el progreso en `SharedPreferences`, dentro del namespace `TraidoresPrefs`.

El catalogo visible de logros vive en `ProfileCustomizationCatalog.kt`. Cada logro tiene:

- `id`: clave estable para progreso local y futura integracion externa.
- `name`: nombre completo mostrado en el selector.
- `shortName`: texto compacto para los tres destacados del perfil.
- `description`: condicion de desbloqueo visible para el jugador.
- `obtainedDate`: se completa desde el progreso guardado; si no se desbloqueo, queda como `Pendiente`.
- `rarity`: bronce, plata u oro.

El perfil muestra todos los logros en el selector, pero solo permite destacar logros ya desbloqueados. Los pendientes quedan visibles con su condicion para que el jugador sepa como conseguirlos.

## Como se detectan hoy

- `Te agradezco infinitamente`: se desbloquea al abrir/crear el perfil local.
- `Ser primero no es lo importante, es lo unico`: cuenta acciones nocturnas de asesinato registradas por el humano como Asesino; desbloquea al llegar a 25.
- `Y me gusta el rol, el maldito rol!`: cuenta victorias especiales del humano como Bufon; desbloquea al llegar a 5.
- `Hoy dormis afuera`: detecta una victoria del Pueblo donde todos los roles asesinos aparecen como expulsados en el historial publico reciente. Es una regla aproximada hasta que el motor guarde un historial exacto de expulsiones.
- `Lo que no te mata, te infecta`: cuenta victorias del humano como Desertor; desbloquea al llegar a 10.
- `Ya nadie va a escuchar tu voto`: detecta 3 silencios al mismo objetivo en una misma partida como Mercenario.
- `Sobreviviendo dije, sobreviviendo`: detecta victoria como Aldeano, vivo al final, en partida de mas de 12 jugadores.
- `El alcalde que fue prometido`: cuenta victorias como Alcalde despues de revelar/usar poder de desempate; desbloquea al llegar a 15.
- `Quien te ha visto y quien te ve`: cuenta victorias totales del humano; desbloquea al llegar a 50.
- `Traidores Supremo`: se desbloquea cuando todos los demas logros ya estan desbloqueados.

## Reglas tecnicas

- El tracker no inventa fechas retroactivas. La fecha se guarda el dia en que el logro se desbloquea.
- Cada partida local finalizada se procesa una sola vez con una clave basada en `session.code`, `startedAtEpochMs` e `initialPlayerCount`.
- Las victorias especiales del Bufon tienen una clave separada para no bloquear el registro posterior del resultado final de la misma partida.
- El modo online todavia no escribe progreso de logros. Esto evita mezclar datos locales con partidas sincronizadas hasta que el contrato online este cerrado.

## Integracion futura con Google Play Games

Google Play Games no decide por si solo cuando se cumple una condicion del juego. La app debe detectar el evento y avisarle a Google.

La integracion recomendada es mantener `AchievementTracker` como fuente local y agregar una salida de sincronizacion cuando haya cliente Play Games disponible:

1. Definir los mismos logros en Google Play Console.
2. Crear una tabla `achievementIdLocal -> achievementIdGoogle`.
3. Cuando `AchievementTracker` desbloquee un logro, llamar a la API de Google Play Games para desbloquearlo.
4. Para logros incrementales, enviar tambien el incremento si Google Play se usa como contador externo.
5. Si el jugador esta offline o no inicio sesion en Google, conservar el progreso local y sincronizar en el siguiente inicio de sesion.

La ventaja de esta separacion es que el perfil funciona aun sin Google Play, y Google queda como espejo externo del progreso en lugar de fuente unica.
