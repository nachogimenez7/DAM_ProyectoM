# Contrato reservado para la etapa online

Referencia: Android `32e4fc09dfb726d5264b27804bb4f8c1f5ae1a21`. La etapa actual no se conecta a Firebase. Este documento preserva decisiones para evitar que el desarrollo local obligue a cambiar Android después.

- Claves de mapas, fases y roles: `TraidoresCore/GameCatalog.swift`, contrastadas con `Tests/TraidoresCoreTests/Fixtures/android-catalog.json`. El fixture guarda SHA-256 de las fuentes Kotlin.
- Nombres de UI no sustituyen claves: Detective/Comisario se transporta como `policia`; Grecia como `grecia`.
- Rol desconocido debe seguir siendo desconocido, nunca un Aldeano por defecto. Los codecs actuales rechazan identificadores desconocidos.
- El futuro motor local recibirá reloj y fuente de aleatoriedad inyectables. Vistas y animaciones no resolverán fases.
- El modelo de jugador conservará identidad estable y orden; el online añadirá UID Firebase, sin usar el nombre mostrado como identidad única.
- No se implementan aún transporte, ID de acción, semántica de snapshots, autoridad ni DTO completos de sala. Sus requisitos y casos de prueba están en las secciones 4–6 y 9 del [plan](PLAN_MIGRACION_IOS.md).
- El futuro codec debe preservar `versionEstado = 2`, protocolo de voto 2 y compatibilidad legacy donde corresponda; `authorityEpoch`, `stateSequence`, el checkpoint antes de RTDB, UUID v3 para acciones, identificador literal de voto y datos privados separados.

Al iniciar online, cotejar la build Android vigente y las reglas desplegadas. Estos valores no son una afirmación de lo que hoy está publicado en Firebase.
