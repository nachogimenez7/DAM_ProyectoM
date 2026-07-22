# SPEC — Modo Práctica: elegir tu rol dentro de "Opciones avanzadas" (Jugar vs IA)

> Para Codex. Español, `archivo:línea`. **No compilar** (el usuario valida en Android Studio). Alcance: **mover y exponer** a cualquier jugador un control que hoy ya existe y funciona, pero está escondido en el menú de debug. Cero cambios de motor/lógica de reparto — solo relocalizar UI y sacarle el gate de `isDebugBuild`.
>
> **Solo modo local** ("Jugar vs IA"). No tocar el lobby online (`showOnlineAdvancedOptionsDialog()`).

## Hallazgo clave (por qué esto es barato)

`LocalGameFactory.roleDeckFor` (`GameModels.kt:815-835`) ya garantiza el rol forzado exista en el mazo aunque la composición actual no lo incluya:

```kotlin
if (forcedHumanRoleKey.isNotBlank() && roles.none { it.key == forcedHumanRoleKey }) {
    val replaceIndex = roles.indexOfLast { it.key == "aldeano" }.takeIf { it >= 0 } ?: roles.lastIndex
    roles[replaceIndex] = roleForKey(forcedHumanRoleKey, suffix)
}
```

Reemplaza un Aldeano por el rol pedido (`GameModels.kt:828-833`), y `forceHumanRole` (`:837-852`) después te intercambia con quien lo tenga. Esto ya corre hoy a través de `LocalGameFactory.assignRoles(session, selectedRoleKey)` (`GameModels.kt:570`), llamado desde `startButton.setOnClickListener` (`LobbyActivity.kt:350`). **No hay que tocar nada de esto.**

Lo único que hoy es "de debug" es **dónde vive el selector** y que está gateado a builds debuggable:
- Lista de roles: `debugRoles` (`LobbyActivity.kt:159-172`, 12 entradas: AZAR + 11 roles).
- Estado: `debugRoleIndex` (`LobbyActivity.kt:152`).
- El control real hoy vive dentro de **"Opciones de testeo"** (`showTestOptionsDialog()`, `LobbyActivity.kt:3475-3559`), como un botón que cicla roles al tocarlo (`:3519-3527`) y solo aparece si `isDebugBuild` (`:3482,3513`).
- La validación (mapa + cantidad mínima de jugadores) ya existe en `startButton` (`LobbyActivity.kt:326-348`) usando `RoleCatalog.isAvailableOnMap` y `LocalGameFactory.minimumPlayersForRole` (`GameModels.kt:566-568`) — **no tocar, sigue funcionando igual sin importar dónde esté el picker**.
- Nota aparte: hay un `debugRoleSection`/`debugRoleButton` que se pone `View.GONE` siempre (`LobbyActivity.kt:277-281`) y parece vestigial (superado por el dialog de testeo). Si al tocar el código ves que ya no lo usa nadie más, se puede limpiar; si no estás seguro, dejalo — no es parte central de esta spec.

---

## Parte 1 — Mover el selector de "Opciones de testeo" a "Opciones avanzadas"

1. En `showTestOptionsDialog()` (`LobbyActivity.kt:3475-3559`): **quitar** el botón "Forzar tu rol" y su lógica de refresco (`:3519-3534`, el `roleButton`/`refreshForcedRole`/`forcedRoleIndex`). El resto del diálogo (switches de debug: "IA obedece votos del chat", "Forzar empates", "Bots no te matan de noche", "Bots no te votan") **queda igual**, gateado a `isDebugBuild` como hoy — son herramientas internas, no pasan a ser públicas.
2. En `showAdvancedOptionsDialog()` (`LobbyActivity.kt:3562` en adelante, rama local — la rama online ya desvía a `showOnlineAdvancedOptionsDialog()` en `:3563-3566` y no se toca), agregar una sección nueva **sin gate de `isDebugBuild`** (visible para cualquier jugador, siempre). Ubicarla junto a "COMPOSICION DE ROLES" (`:3679` en adelante), ya que ambas tratan de qué roles aparecen en la partida:
   - Título de sección con `dialogSectionTitle("MODO PRACTICA")` (mismo helper que ya usa el resto del diálogo).
   - Texto explicativo corto: *"Elegí un rol para asegurarte de que te toque esta partida. Si no está en la composición actual, reemplaza a un Aldeano."*
   - El mismo botón cíclico que había en el menú de debug (mismo patrón texto: `"Practicar: $label$requirement"`, reusando la lógica de `renderDebugRole()` — `LobbyActivity.kt:3199-3204` — para el sufijo `(N+)` cuando el rol necesita más jugadores de los actuales).
   - Un ícono/botón chico "ver rol" al lado, que abra `RoleDetailDialog.show(context, role)` (`RoleDetailDialog.kt:14`) con el rol actualmente seleccionado en el ciclador — así el jugador lee qué hace el rol antes de confirmarlo. Construir el `Role`/`GameRole` de la misma forma que ya se arma para mostrarlo en la pantalla de referencia de roles (`RolesActivity`/`RoleAdapter`) — seguir ese mismo patrón para no duplicar lógica de construcción.
   - Cuando el rol elegido es "AZAR" (índice 0), no mostrar el botón "ver rol" (no hay nada que mostrar).
3. En el botón **APLICAR** del diálogo (`LobbyActivity.kt:3547` en el diálogo de testeo original — replicar el mismo patrón en `showAdvancedOptionsDialog()`), guardar el índice elegido en `debugRoleIndex` (o renombrarlo a algo como `practiceRoleIndex` si preferís más claridad ahora que deja de ser "debug" — a tu criterio, total es una sola variable y sus usos son fáciles de rastrear). Lo importante: `startButton` (`LobbyActivity.kt:331`) tiene que seguir leyendo el mismo valor sin cambios en su lógica.
4. Default: si el jugador nunca toca "Opciones avanzadas", el valor queda en `AZAR` (índice 0) y el reparto es 100% aleatorio, igual que hoy — cero cambio de comportamiento para quien no usa la feature.

## Parte 2 — Nada más que tocar

- No cambiar `LocalGameFactory.assignRoles`, `roleDeckFor`, `forceHumanRole`, `minimumPlayersForRole`, ni la validación de `startButton`.
- No tocar los switches de debug que quedan en "Opciones de testeo" (siguen gateados a `isDebugBuild`).
- No tocar el lobby online.
- No renombrar ni promover "Modo test rápido" (`quickTestMode`) a feature pública — eso, si se hace, es una spec aparte.

## Verificación

1. **Default (AZAR)**: sin tocar "Opciones avanzadas", jugar una partida local — reparto 100% aleatorio, como siempre.
2. **Forzar un rol que ya está en la composición** (ej. Asesino con cualquier cantidad): te toca ese rol, sin sorpresas.
3. **Forzar un rol que NO está en la composición actual** (ej. Bufón jugando en Medieval con 8+ jugadores pero habiendo bajado su cupo a 0 en "Composición de roles"): al iniciar, un Aldeano se reemplaza por Bufón y te toca a vos. Confirmar que el reemplazo realmente ocurre (jugar y ver tu carta).
4. **Forzar un rol no disponible en el mapa actual** (ej. Oráculo en Pampa): mismo toast de bloqueo que existe hoy ("Oráculo no está disponible en este mapa"), no inicia.
5. **Forzar un rol que pide más jugadores de los presentes** (ej. Alcalde con 5 jugadores): mismo toast de bloqueo que existe hoy ("Ese rol necesita al menos 8 jugadores").
6. **Botón "ver rol"**: abre el diálogo correcto con nombre/descripción/imagen del rol seleccionado en el ciclador, y no aparece cuando el ciclador está en AZAR.
7. **"Opciones de testeo"** (solo en build debug): sigue abriendo, ya sin el botón de rol (movido), con los demás switches intactos y funcionando igual que antes.
