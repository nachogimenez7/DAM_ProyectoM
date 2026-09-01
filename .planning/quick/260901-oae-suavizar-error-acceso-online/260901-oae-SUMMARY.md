# Resumen: suavizar el error automatico de acceso online

## Causa

`OnlineModeActivity` verificaba el documento de bloqueo al entrar. Si Firestore aun estaba
reconectando, mostraba un aviso largo y repetia el mismo aviso cada cinco segundos.

## Entregado

- El fallo transitorio se muestra como una linea discreta dentro de la pantalla.
- La linea desaparece y los botones se habilitan automaticamente al recuperarse el servidor.
- Los reintentos se cancelan al salir y las respuestas obsoletas se ignoran.
- Los reintentos repetidos ya no vuelcan una traza completa cada cinco segundos.
- Los mensajes visibles hablan del servidor y no exponen Firebase, Firestore ni reglas.
- El dialogo obligatorio se conserva exclusivamente para un bloqueo real de moderacion.

## Verificacion

- 597 pruebas unitarias Android aprobadas.
- Compilacion debug y release aprobadas.
- Reproduccion real con Firestore inaccesible: sin ventana emergente.
- Recuperacion real al reiniciar el servidor: estado oculto y botones habilitados solos.
