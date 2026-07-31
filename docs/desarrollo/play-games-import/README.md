# Paquete de logros para Google Play Games

Este directorio contiene los metadatos que se empaquetan junto con los diez medallones en
`logros-traidores-play-games.zip`.

La consola actual exige que el archivo de mapeo se llame exactamente
`AchievementsIconsMappings.csv`, con `Icons` en plural.

## Diseño aprobado

- Cinco logros progresivos: Asesino (25), Bufón (5), Desertor (10), Alcalde (15) y victorias
  totales (50).
- Cuatro logros normales visibles: registro, expulsión de Asesinos, Mercenario y Aldeano.
- `Traidores Supremo` es normal y permanece oculto hasta desbloquearse.
- Puntuación total: 285 puntos.

## Importación

En Play Console, abrir:

**Play Games Services > Configuración y gestión > Logros > Importar logros**

Subir `logros-traidores-play-games.zip` y guardarlo como borrador. No publicar los logros hasta
haber verificado nombres, descripciones, tipos, pasos, puntos e íconos en la vista previa.

La consola asignará un ID remoto opaco a cada logro. Esos diez IDs deben copiarse después en
`app/src/main/res/values/play_games_ids.xml`.

## Importante

El tipo (normal o progresivo) y el estado inicial (visible u oculto) no pueden cambiarse después
de publicar los logros. Los nombres usan dos puntos o puntos suspensivos en lugar de comas para
cumplir con el formato de importación masiva.
