# Cuenta, privacidad y eliminación

## Decisiones

- Audiencia declarada: mayores de 13 años.
- Eliminación: inmediata y permanente desde la app, confirmada escribiendo `ELIMINAR`.
- Alcance: perfil público de Firebase, cuenta Firebase Auth, datos locales y respaldo propio
  de Traidores en Play Games.
- Sitio oficial: `https://www.traidores.me`.
- Contacto oficial: `bandidogamesestudio@gmail.com`.

## Implementación

1. Reautenticar al usuario antes de tocar datos remotos.
2. Borrar el snapshot `traidores_profile_v1` de Play Games.
3. Borrar `perfiles_publicos/{uid}` antes de eliminar Firebase Auth.
4. Eliminar Firebase Auth y limpiar `TraidoresPrefs`.
5. Mantener fuera de `TraidoresPrefs` una marca que impida revincular Play Games
   automáticamente después del borrado.
6. Publicar accesos a privacidad y eliminación dentro de la app.
7. Crear `/privacidad` y `/eliminar-cuenta` en el repositorio independiente del sitio.
8. Probar reglas, tests JVM, compilación Android y navegación estática del sitio.

## Publicación

Los cambios se preparan y verifican localmente. Las reglas, el sitio y la app no se publican
sin una confirmación explícita del propietario.
