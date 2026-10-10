# Para Claude: fallo de fotos reproducido en dispositivos reales

Fecha 9/10. Build Debug normal 53, V3=false, emuladores Firebase=false. Package separado hostqa para no borrar la app firmada del A56. Dos cuentas QA registradas desde la UI con publicId 48 (A56) y 49 (Pixel_10), Firebase real, tokens Debug App Check de estos dispositivos registrados sin desactivar enforcement.

El usuario eligió desde la galería del A56 la foto de un muñeco tejido. Se subió el JPEG de 68.859 bytes a Storage; perfiles_publicos/kC53SDRkeqYi2bzLkIcVWEnzU7D3 tiene fotoPerfil; partidas/mCqqPHE46RX5YClYRVgN/jugadores/kC53SDRkeqYi2bzLkIcVWEnzU7D3 tiene exactamente la misma URL. Sala privada de prueba PLLPGU, anfitrión A56. NO se sembró una foto por Admin.

Falla: el emulador de la otra cuenta muestra el gato ilustrado, NO el muñeco, tanto en el lobby como al abrir el perfil público. Tras reabrir, la tarjeta online del A56 también muestra el gato. La URL sí propagó a la sala: no es una subida pendiente ni un perfil equivocado.

Evidencia en output/beta-anfitrion-cloud-2026-10-09/:
- photo-propagation.json: comparación perfil/jugador, con URLs privadas de prueba (no copiar sus tokens a documentación pública).
- emulator-lobby-foto-remota.png.
- emulator-perfil-publico-foto-falla.png.
- a56-online-foto.png.
- emulator-app.log: log completo del proceso. No encontré play_games_avatar_render_failure en el extracto; la ruta de callback no disponible también cae a ilustración sin lanzar excepción.

Se respetó la división: NO edité PlayGamesProfileAvatar.kt ni los archivos Android asignados. Revisar el cargador ImageManager de Play Games para HTTPS de Firebase Storage y también la recuperación de la vista previa propia tras reabrir. Codex sigue con pruebas de partida/historial/relevo; quedan bloqueadas las aprobaciones visuales de fotos hasta una build corregida.
