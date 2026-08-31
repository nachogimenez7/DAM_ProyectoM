---
quick_id: 260831-web
status: complete
---

# Landing beta y seguridad estática

## Delivered

- La portada incorpora una sección de beta con IA local, salas online experimentales, perfil y
  progreso, chat/emotes/moderación de mesa y una advertencia honesta sobre el estado del online.
- La descarga muestra el enlace de verificación SHA-256 del APK.
- La CSP ya no permite scripts ni estilos inline; sólo habilita recursos propios y las fuentes
  de Google requeridas.
- Se agregaron `X-Frame-Options`, COOP, CORP y caché de revalidación para descargas.
- Se publican en el repositorio `llms.txt` informativo y `.well-known/security.txt` para
  reportes de vulnerabilidades.
- Se documentó el análisis, los controles aplicables y las reglas para futuras rutas dinámicas
  en `Sitio-Web-Traidores/AUDITORIA_SEGURIDAD.md`.

## Audit conclusion

El sitio es estático: no tiene formularios, login, cookies propias, analytics, APIs, Server
Actions ni datos enviados por visitantes. Rate limiting, sanitización de input remoto, CSRF y
hash de contraseñas no aplican hoy; sí serán requisitos antes de agregar cualquier endpoint.
`robots.txt` ya existía y no es un control de seguridad. `llms.txt` es descriptivo, no una
defensa ni una condición de SEO.

## Verification

- `vercel.json` válido como JSON y CSP comprobada sin `unsafe-inline`.
- `script.js` validado con `node --check`.
- Sitemap validado como XML y referencias locales de la portada verificadas.
- SHA-256 del APK comparado contra `traidores.apk.sha256`: coincide.
- `git diff --check`: sin errores.
- No se desplegó la web; la producción actual mantiene sus headers y contenido previos hasta
  que el usuario revise una preview y autorice la publicación.
