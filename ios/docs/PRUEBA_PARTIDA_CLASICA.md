# Primera partida clásica en iPhone

Abrir `TraidoresIOS.xcodeproj` en la carpeta **TraidoresIOS** del escritorio. Conservar `Packages` dentro de esa carpeta. Elegir el iPhone en Xcode y ejecutar con ⌘R.

## Disponible

Menú → Jugar → Jugar contra IA → Nueva partida. Pampa, cinco jugadores, cuatro roles clásicos. Nombre editable y rol al azar o elegido para practicar. Sin Firebase ni conexión a Internet. El debate usa mensajes y acciones guiadas; todavía no admite chat libre.

La partida avanza con tus confirmaciones, sin cuenta regresiva. Los bots completan sus acciones nocturnas al avanzar; la muerte se aplica al amanecer. Podés salir y continuar una partida guardada. No se revelan las cartas de los eliminados hasta el final.

## Pruebas en el teléfono

1. Elegí **Comisario**. Empezá, seleccioná un jugador y confirmá. Comprobá que el resultado aparezca en tus investigaciones privadas. Compartilo durante el debate y revisá el mensaje público.
2. Elegí **Médico** en otra partida. Confirmá que podés protegerte. Protegé también a otro jugador en una noche posterior.
3. Elegí **Asesino**. Elegí una víctima. Comprobá que el resultado se anuncie al amanecer y que una protección coincidente pueda cancelar la muerte.
4. Jugá como **Aldeano**. Leé el debate, señalá una sospecha y votá. El botón de confirmar debe estar deshabilitado hasta seleccionar un objetivo válido.
5. Revisá el recuento: votos individuales y candidatos de un empate. Si se repite el empate, nadie es expulsado. Al eliminar al Asesino gana el Pueblo; al alcanzar paridad ganan los Traidores.
6. Si te eliminan, continuá como espectador hasta el final. Ya no debés poder votar, investigar o acusar.
7. Salí al menú y usá **Continuar**. Cerrá la app y reabrila: la partida debe conservar fase, rol y decisiones. Nueva partida pide confirmación antes de reemplazar una pendiente.
8. Entrá y salí de la partida con música activada: se pausa durante la partida y vuelve al menú. Probá bloquear el teléfono y volver, también con texto grande.

El botón «Volver a jugar» del resultado lleva a la preparación de otra partida.

## Alcance técnico y comprobaciones

- Motor Swift portable, separado de SwiftUI y de cualquier servicio remoto.
- Reglas clásicas trasladadas de `GameModels.kt` (`roleCompositionPreset(CLASSIC)`, `GameRules.winnerFor`) y `GameEngine.kt` (noche, protección, investigación, votación, recuento y segundo empate sin alcalde).
- Las investigaciones se calculan antes del amanecer; un investigador atacado sigue actuando esa noche. El Médico puede repetir protección y protegerse.
- La IA inicial es una implementación acotada de iOS. Usa información pública, su propio rol y sus propias investigaciones. No es un port completo de `LocalBotAi`, ni promete igual dificultad o diálogos. Hay un solo Asesino.
- Los bots detectivos comparten sus investigaciones; las acusaciones humanas influyen en las sospechas públicas. El Asesino puede priorizar a quien se declara Comisario y el Médico protegerlo.
- El guardado local está versionado y mantiene el estado del generador aleatorio. No es el esquema de Firebase. Los identificadores existentes del catálogo siguen intactos; el online requerirá sus propios DTOs y pruebas del protocolo Android.
- Las acciones usan una revisión de fase para rechazar confirmaciones repetidas o antiguas. La lógica no depende de temporizadores en segundo plano.
- Pruebas: 13 tests (3 catálogo, 10 motor/guardado), incluyendo 500 partidas deterministas con guardado/restauración en cada transición. No sustituyen pruebas visuales, de accesibilidad ni mediciones de rendimiento en iPhone.
- Compilación iOS validada sin firma. La firma y la ejecución física de esta entrega deben comprobarse con el iPhone del propietario.

## Próximos bloques

Tras esta prueba: corregir experiencia de la mesa y añadir dificultad/diálogo con memoria de votos. Luego incorporar roles especiales con pruebas individuales, cantidad de jugadores y mapas. Firebase y compatibilidad de salas Android/iOS se trabajan después de estabilizar el motor local.
