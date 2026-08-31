---
quick_id: 260831-web
status: complete
---

# Actualizar sitio web y endurecer seguridad estática

## Goal

Actualizar la landing de Traidores para reflejar la beta actual y aplicar mejoras de seguridad
proporcionales a un sitio estático sin APIs ni formularios.

## Tasks

1. [x] Auditar el sitio estático, rutas, scripts, contenido dinámico y headers actuales.
2. [x] Actualizar la portada con capacidades reales de la beta y descarga verificable.
3. [x] Endurecer CSP y headers sin romper fuentes, vídeo ni descarga.
4. [x] Agregar mecanismos públicos apropiados: `llms.txt` informativo y `security.txt`.
5. [x] Validar enlaces, HTML, JSON y cambios sin desplegar producción.

## Must Haves

- No afirmar características no disponibles en el APK beta.
- No introducir formularios, trackers ni cookies innecesarias.
- Mantener privacidad, accesibilidad y el funcionamiento sin JavaScript.
- No publicar la web sin una instrucción explícita del usuario.
