# Acceso con Google y visibilidad de salas

Implementado en julio de 2026.

## Cuenta

- Despues de la presentacion de Bandido Games, un invitado ve una invitacion una sola vez.
- Puede continuar con Google, usar correo o seguir como invitado.
- Google usa Credential Manager y vincula la credencial al usuario anonimo actual para
  conservar uid, perfil y numero publico.
- Si esa cuenta ya existia en otro dispositivo, se inicia sesion y se recupera su perfil.
- El boton tambien esta disponible en Perfil.
- Si falta `default_web_client_id`, la app explica que Google todavia no esta configurado y
  mantiene disponible el registro por correo.

Configuracion externa necesaria:

1. Firebase Authentication: habilitar el proveedor Google.
2. Firebase > Configuracion del proyecto > app Android: cargar SHA-1 de debug y de Play App
   Signing.
3. Descargar el `google-services.json` actualizado y reemplazar
   `app/google-services.json`.
4. Mantener Play Games y Firebase dentro del mismo proyecto de Google Cloud.

## Salas

Dos opciones independientes se guardan al crear:

- `visibilidad = publica`: aparece en Buscar partida.
- `visibilidad = privada`: solo se encuentra ingresando el codigo.
- `soloCuentas = false`: pueden entrar cuentas e invitados.
- `soloCuentas = true`: solo entran cuentas registradas.

Crear una sala continua siendo exclusivo de cuentas. Los valores predeterminados son publica
y con invitados permitidos.

La consulta del navegador requiere el indice compuesto:
`estado ASC, visibilidad ASC, actualizadaEn DESC`.

La sala privada no es un secreto criptografico: cualquier cliente modificado autenticado
podria consultar Firestore. Antes de produccion hay que sumar App Check y limitar intentos de
codigos.
