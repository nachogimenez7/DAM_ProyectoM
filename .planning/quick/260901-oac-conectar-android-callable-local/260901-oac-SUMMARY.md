# Resumen: conectar Android a la callable local

## Entregado

- Build debug opt-in que conecta Firestore, RTDB y Functions a Firebase Emulator.
- Cliente Android de `iniciarPartidaV2` con contrato y respuestas tipadas.
- Integracion del boton de inicio sin alterar el flujo legacy normal.
- Release y debug normal mantienen desactivada la ruta de emuladores.
- Comandos repetibles y guia de prueba para emulador Android o USB.

## Verificacion

- 594 pruebas unitarias Android aprobadas.
- Compilacion debug opt-in, debug normal y release aprobadas.
- Integracion backend emulada: inicio, permisos, empate y sincronizacion aprobados.
- Simulacion con 3, 5, 10 y 15 jugadores, reintentos y carreras aprobada.

## Pendiente manual

- Ejecutar la APK en dos o mas dispositivos/emuladores y recorrer la sala desde la interfaz.
