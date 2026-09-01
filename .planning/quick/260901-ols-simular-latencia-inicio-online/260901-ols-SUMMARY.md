---
quick_id: 260901-ols
status: complete
---

# Simulacion de latencia del inicio online

## Delivered

- Se agrego `simulate:functions-local`, una simulacion repetible que solo usa Firebase Emulator.
- Mide inicio y reintento con 3, 5, 10 y 15 jugadores, la callable HTTP, una carrera sobre la
  misma sala y una rafaga de salas independientes.
- Cada escenario verifica documento de partida, cantidad de repartos privados y miembros RTDB.
- La llamada HTTP usa tokens de Auth y App Check exclusivos del modo inseguro del emulador.

## Results

- Flujo integral existente: 10/10 escenarios, promedio 865 ms y maximo 1783 ms.
- Inicio directo caliente: 78-91 ms promedio; p95 maximo observado 137,1 ms.
- Reintento idempotente caliente: 60-81 ms promedio.
- Callable con 10 jugadores: primera llamada 4718,6 ms; calientes 100,4 ms promedio y 112 ms p95.
- Cinco solicitudes simultaneas a una sala: primera confirmacion 2964,6 ms; las cinco terminaron
  en 3608,9 ms con un solo reparto.
- Ocho salas de diez jugadores simultaneas: todas consistentes; 6795,7 ms de pared en el emulador.

## Finding fixed

La primera llamada HTTP mostro que Functions Emulator usaba el namespace `traidores-local`,
mientras las pruebas usaban `traidores-local-default-rtdb`. La inicializacion Admin ahora fija el
namespace `-default-rtdb` solo dentro del emulador. La siguiente corrida completo Firestore y RTDB.

## Interpretation

El camino caliente es rapido y escala practicamente igual entre 3 y 15 jugadores. La demora
visible probable esta en red y arranque frio, no en repartir roles. Los tiempos de concurrencia
del emulador prueban consistencia pero no predicen el escalado real de Cloud Functions. Android
ya bloquea inicios duplicados; esa proteccion debe conservarse al conectar la callable.
