---
quick_id: 260901-ols
status: complete
---

# Simular latencia del inicio online

## Goal

Medir de forma repetible el inicio autoritativo local con diferentes cantidades de jugadores,
reintentos y concurrencia, sin tocar Firebase de produccion.

## Tasks

1. [x] Crear un simulador reproducible sobre Firestore y RTDB Emulator.
2. [x] Medir inicios y reintentos con 3, 5, 10 y 15 jugadores.
3. [x] Probar concurrencia sobre una misma sala y salas independientes.
4. [x] Comparar con la simulacion integral de clientes y documentar los limites del resultado.

## Estado de verificacion

- Flujo integral de clientes: 10/10 escenarios correctos, promedio 865 ms, maximo 1783 ms.
- Inicio directo caliente: promedios entre 78 y 91 ms segun cantidad de jugadores.
- Callable HTTP caliente: promedio 100,4 ms y p95 112 ms con 10 jugadores.
- Primera callable fria del emulador: 4718,6 ms.
- Cinco solicitudes sobre la misma sala: una inicia y cuatro son idempotentes.
- Ocho salas independientes simultaneas conservaron estado y repartos completos.

## Must Haves

- No usar el proyecto Firebase real.
- Excluir el sembrado de datos de la medicion del inicio.
- Calentar SDK y emuladores antes de registrar resultados.
- Verificar consistencia, no solo tiempo.
