# Cierre de beta Android — 30 de septiembre de 2026

## Decisión de hoy

Terminar pruebas manuales. La revancha de quince en 0.1.45 pasó: nueva partida,
resultado consistente y regreso de los quince a la sala. No pedir otra partida
completa de quince por el aviso visual transitorio de reconexión; el usuario
prefiere no dedicarle pruebas. Una beta pública todavía necesita validación de
varias salas y preparación de distribución/capacidad. No prometer fecha de
publicación: depende del resultado de controles y del acceso/revisión de Play.

## Evidencia ya reunida

- Cinco jugadores: juego normal, recuperación del invitado, resultado y regreso.
- Quince: recuperación inmediata de 5555 después de treinta segundos en segundo
  plano; relevo A56 → 5635; estado vigente al recuperar A56; resultado ronda cinco.
- Revancha de quince en la misma sala: partida distinta, resultado ronda tres,
  otro relevo A56 → 5635 durante la noche, cinco acciones confirmadas y amanecer.
- Regreso a la misma sala y devolución de coordinación al A56, quince miembros,
  LISTO restablecido. Tiempos de publicaciones locales generalmente alrededor
  de 0,5–0,6 segundos; muestras limitadas, no garantía para el público.
- Firebase: 10.735 lecturas de Firestore en 24 horas móviles, pico RTDB quince,
  sin agotamiento observado. Proyecto sin facturación habilitada. Una sala de
  quince probada no demuestra capacidad de cien usuarios ni del QR escolar.

## Trabajo técnico previo al último ensayo

1. Revisar el relevo nocturno de la revancha. Funciona resolución de acciones,
   pero avisos privados del plan de asesinos recibieron rechazos al comenzar el
   relevo. Revisar si se publican antes de confirmar autoridad y permiso RTDB;
   preparar un ajuste con pruebas locales si la secuencia lo requiere.
   No relajar permisos para esconder errores.
   La razón de ausencia del A56 en esta revancha no está confirmada.
2. Terminar tratamiento de salas abandonadas. Está probado en fuente el filtro
   de cero plazas ocupadas; eso no equivale a cero conexiones. Mantener el margen
   de tres minutos del anfitrión y no borrar una sala por un solo teléfono ausente.
   Revisar solución de resumen de disponibilidad o limpieza central, sin aumentar
   lecturas consultando todos los miembros de cada sala desde el buscador.
3. Incorporar al próximo APK el texto de eliminado más corto y legible, ya
   compilado. Cancelar LISTO queda pendiente por preferencia de minimizar lecturas.
4. Ejecutar comprobaciones pertinentes de recuperación, seguridad y compilación;
   preparar una versión identificada y un AAB firmado para distribuir por Play.
   Los ajustes recientes están en el checkout; no afirmar que ya están en GitHub.

## Ensayo final para mañana

Primero preparar el APK y conectar/instalar por ADB o Run múltiple; el usuario
no debe repetir instalaciones manuales dispositivo por dispositivo.

1. Prueba dirigida con cinco y dos cuentas registradas: A56 deja coordinación,
   vuelve, y después se pausa el coordinador sustituto cerca de recuento/noche.
   Debe aplicarse la fase vigente, continuar la mesa y permitir únicamente las
   acciones correspondientes a rol y condición vivo/eliminado. Conservar logs y
   reportes de coordinadores; no extender la prueba sin una incidencia concreta.
2. Tres salas de cinco simultáneas con los mismos dispositivos: iniciar partidas
   cortas, enviar un mensaje y emote identificables por sala, comprobar que no
   aparezcan en otra sala y terminar con regreso al lobby. Esta prueba busca
   aislamiento, no demostrar capacidad para más de quince conexiones.
3. Instalar o actualizar la versión firmada desde Google Play en el A56; comprobar
   invitado/cuenta registrada, ingreso por código, chat, resultado y regreso.
   La versión instalada debe coincidir con el AAB candidato. Evitar repetir una
   partida larga de quince sólo por distribución.

Si estos controles pasan sin problemas de avance, permisos persistentes o pérdida
de lugar, cerrar validación funcional y no seguir añadiendo pruebas por rutina.

## Publicación y exposición

- Comprobar en Play Console que el acceso a producción solicitado haya sido
  aprobado y el canal de pruebas abiertas esté disponible; estado actual no
  consultado. Los requisitos de testers anteriores ya fueron completados.
- Revisar ficha, privacidad/declaraciones y aviso de beta; explicar que ciertos
  cosméticos se habilitan temporalmente y que monetización futura puede cambiar
  su disponibilidad. No hace falta incluir anuncios/compras para abrir la beta.
- Decidir capacidad para audiencia desconocida: la base Spark limita a cien
  conexiones, incluyendo lobbies y dispositivos de prueba. Elegir acceso gradual
  con control real o facturación y monitoreo. No habilitar facturación ni gastar
  automáticamente. Un saldo de tarjeta o alerta de presupuesto no es un corte.
- Lanzar primero a un grupo pequeño y observar errores/consumo; ampliar después.
  Mantener un margen de fechas respecto de la exposición tentativa del 27/10.

Consulta de métricas realizada por API con la sesión Firebase existente. No es
necesario dejar Chrome abierto. Logs y snapshots detallados están ignorados en
`output`, sin copiarlos al repositorio; los documentos sólo usan IDs truncados.

Fuentes verificadas el 30/09:
- https://firebase.google.com/docs/database/usage/limits
- https://firebase.google.com/docs/firestore/quotas
- https://support.google.com/googleplay/android-developer/answer/14151465?hl=es
