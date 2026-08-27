# Cartero de comentarios de Traidores

Este Apps Script revisa cada cinco minutos la colección privada `comentarios` del proyecto
Firebase `traidores`, envía los documentos pendientes a
`bandidogamesestudio@gmail.com` y actualiza su estado.

No se despliega como aplicación web. El APK no contiene una URL pública ni una contraseña.

## Configuración única

1. Abrir <https://script.google.com> con una cuenta que tenga acceso al proyecto Firebase
   `traidores` y crear un proyecto. El destinatario continúa fijado en el código como
   `bandidogamesestudio@gmail.com`.
2. Pegar el contenido de `Code.gs` en el archivo de código del proyecto.
3. En **Configuración del proyecto**, activar **Mostrar el archivo de manifiesto
   appsscript.json en el editor**.
4. Reemplazar el manifiesto por el contenido de `appsscript.json`.
5. En esa misma configuración, cambiar el proyecto de Google Cloud por el número
   `99323018581`, correspondiente al proyecto Firebase `traidores`.
6. Volver al editor, seleccionar `enviarCorreoDePrueba` y tocar **Ejecutar**. Aceptar los
   permisos solicitados y comprobar que el correo llegue.
7. Seleccionar `instalarCartero` y tocar **Ejecutar** una sola vez.
8. En **Activadores**, verificar que exista `procesarComentariosPendientes`, basado en tiempo,
   cada cinco minutos.

## Uso y diagnóstico

- `procesarComentariosPendientes`: revisa manualmente el buzón ahora.
- `enviarCorreoDePrueba`: comprueba únicamente el envío por Gmail.
- `reintentarErrores`: vuelve a poner en cola los documentos con estado `error`.
- La pestaña **Ejecuciones** de Apps Script muestra errores y registros.
- Firestore conserva `estado`, `procesadaEn` y `errorEnvio` en cada documento.

La cuenta Gmail personal dispone de una cuota diaria limitada. Los comentarios que excedan
esa cuota quedan en estado `pendiente` hasta la siguiente ejecución con cuota disponible.
