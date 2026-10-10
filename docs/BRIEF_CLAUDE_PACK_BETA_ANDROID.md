# Trabajo paralelo: cerrar el catálogo cosmético Android

Codex trabaja en mesa Android V3 y pruebas online. Tu tarea es preparar una propuesta
concreta del pack de apoyo y de emotes/estilos individuales; todavía no implementar
Google Play Billing ni cambiar backend, reglas, motores o la mesa de juego.

Lee `docs/PLAN_BETA_ABIERTA_ANDROID.md`, `docs/banners-pack-bienvenida-2026-09-13.md`
y `app/src/main/java/com/traidores/juego/CosmeticPilot.kt`. Revisa los recursos de
`assets/pack_bienvenida/`. `support_preview` es una prueba visual, no una compra.

Entregar un documento y, si hace falta, propuestas visuales en archivos nuevos:

1. Inventario de recursos existentes: terminado, a retocar o faltante.
2. Propuesta de contenido exacto del pack: estilo, marco, insignia, banners y emotes.
3. Qué queda gratis, qué incluye el pack y qué puede venderse individualmente.
   Identificar los cosméticos que ya tienen los jugadores para no retirarlos sin decisión.
4. Propuesta de catálogo inicial pequeño, nombres y precios para discutir con el usuario.
   No dar precios, exclusividad o beneficio de quitar publicidad por aprobados.
5. Cómo se presenta cada producto: vista previa, contenido incluido, equipar y restaurar.
6. Lista de escenas que podrían mostrar estos cosméticos en el tráiler; no grabarlo aún.

Los beneficios propuestos son cosméticos; no cambiar reglas ni dar ventaja en partidas.
Los derechos de compra se implementarán después en Firebase/Play Billing con Codex.
Para evitar conflictos, no editar Kotlin/XML existentes: entregar propuesta y recursos
nuevos bajo `docs/` o una carpeta nueva de `assets/`. No tocar `sources/` ni archivos iOS.
No hacer commit o publicar productos hasta que el usuario lo pida.
