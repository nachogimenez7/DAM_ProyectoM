/**
 * Cartero gratuito de comentarios para la beta de Traidores.
 *
 * Se ejecuta con la cuenta propietaria del proyecto, consulta Firestore mediante OAuth y
 * envía exclusivamente a la dirección fija de Bandido Games. No se publica como Web App:
 * por lo tanto, el APK no contiene una URL ni una clave que alguien pueda reutilizar.
 */
const PROJECT_ID = 'traidores';
const DESTINATION_EMAIL = 'bandidogamesestudio@gmail.com';
const COLLECTION = 'comentarios';
const HANDLER = 'procesarComentariosPendientes';
const MAX_PER_RUN = 10;
const FIRESTORE_ROOT =
  `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents`;

/** Ejecutar una sola vez desde el editor. Autoriza el script e instala el ciclo de 5 minutos. */
function instalarCartero() {
  ScriptApp.getProjectTriggers()
    .filter(trigger => trigger.getHandlerFunction() === HANDLER)
    .forEach(trigger => ScriptApp.deleteTrigger(trigger));

  ScriptApp.newTrigger(HANDLER)
    .timeBased()
    .everyMinutes(5)
    .create();

  procesarComentariosPendientes();
  console.log('Cartero instalado. Se revisarán comentarios cada 5 minutos.');
}

/** Se puede ejecutar manualmente para comprobar que Gmail está autorizado. */
function enviarCorreoDePrueba() {
  MailApp.sendEmail({
    to: DESTINATION_EMAIL,
    subject: '[Traidores] Prueba del cartero de comentarios',
    body: 'El cartero gratuito de Traidores quedó conectado correctamente.',
    htmlBody:
      '<h2 style="color:#b98524">Traidores</h2>' +
      '<p>El cartero gratuito de comentarios quedó conectado correctamente.</p>',
    name: 'Traidores · Bandido Games'
  });
}

/** Función llamada por el activador. También puede ejecutarse a mano. */
function procesarComentariosPendientes() {
  const lock = LockService.getScriptLock();
  if (!lock.tryLock(20000)) {
    console.log('Ya hay otra revisión de comentarios en curso.');
    return;
  }

  try {
    const quota = MailApp.getRemainingDailyQuota();
    if (quota <= 0) {
      console.warn('No queda cuota diaria de correo. Los comentarios seguirán pendientes.');
      return;
    }

    const documents = consultarPorEstado_('pendiente')
      .slice(0, Math.min(MAX_PER_RUN, quota));
    documents.forEach(document => procesarDocumento_(document));
    console.log(`Comentarios procesados: ${documents.length}`);
  } finally {
    lock.releaseLock();
  }
}

/** Devuelve a pendiente los correos fallidos para intentar enviarlos nuevamente. */
function reintentarErrores() {
  consultarPorEstado_('error').forEach(document => {
    actualizarEstado_(document.name, 'pendiente', '');
  });
  procesarComentariosPendientes();
}

function procesarDocumento_(document) {
  const fields = document.fields || {};
  const nombre = texto_(fields, 'nombre', 'Jugador');
  const asunto = texto_(fields, 'asunto', 'Comentario').replace(/[\r\n]+/g, ' ');
  const mensaje = texto_(fields, 'mensaje', 'Sin mensaje');
  const version = texto_(fields, 'version', '—');
  const dispositivo = texto_(fields, 'dispositivo', '—');
  const android = texto_(fields, 'android', '—');
  const uid = texto_(fields, 'uid', '—');
  const id = document.name.split('/').pop();
  const subject = `[Traidores] ${asunto} — ${nombre}`.slice(0, 180);
  const body = [
    'NUEVO COMENTARIO DE TRAIDORES',
    '',
    `Nombre: ${nombre}`,
    `Asunto: ${asunto}`,
    '',
    mensaje,
    '',
    'DATOS TÉCNICOS',
    `Versión: ${version}`,
    `Dispositivo: ${dispositivo}`,
    `Android: ${android}`,
    `UID: ${uid}`,
    `Documento: ${id}`
  ].join('\n');

  try {
    MailApp.sendEmail({
      to: DESTINATION_EMAIL,
      subject: subject,
      body: body,
      htmlBody: construirHtml_({
        nombre,
        asunto,
        mensaje,
        version,
        dispositivo,
        android,
        uid,
        id
      }),
      name: 'Traidores · Bandido Games'
    });
    actualizarEstado_(document.name, 'enviado', '');
  } catch (error) {
    const detail = String(error && error.message ? error.message : error).slice(0, 400);
    console.error(`No se pudo enviar ${id}: ${detail}`);
    try {
      actualizarEstado_(document.name, 'error', detail);
    } catch (updateError) {
      console.error(`Tampoco se pudo registrar el error de ${id}: ${updateError}`);
    }
  }
}

function consultarPorEstado_(status) {
  const response = solicitarFirestore_(
    `${FIRESTORE_ROOT}:runQuery`,
    'post',
    {
      structuredQuery: {
        from: [{ collectionId: COLLECTION }],
        where: {
          fieldFilter: {
            field: { fieldPath: 'estado' },
            op: 'EQUAL',
            value: { stringValue: status }
          }
        },
        limit: MAX_PER_RUN
      }
    }
  );
  return response
    .map(result => result.document)
    .filter(document => document && document.name);
}

function actualizarEstado_(documentName, status, errorMessage) {
  const fields = {
    estado: { stringValue: status },
    procesadaEn: { timestampValue: new Date().toISOString() },
    errorEnvio: { stringValue: errorMessage || '' }
  };
  const masks = ['estado', 'procesadaEn', 'errorEnvio']
    .map(field => `updateMask.fieldPaths=${encodeURIComponent(field)}`)
    .join('&');
  solicitarFirestore_(
    `https://firestore.googleapis.com/v1/${documentName}?${masks}`,
    'patch',
    { fields }
  );
}

function solicitarFirestore_(url, method, payload) {
  const response = UrlFetchApp.fetch(url, {
    method: method,
    contentType: 'application/json',
    payload: JSON.stringify(payload),
    headers: {
      Authorization: `Bearer ${ScriptApp.getOAuthToken()}`,
      'X-Goog-User-Project': PROJECT_ID
    },
    muteHttpExceptions: true
  });
  const code = response.getResponseCode();
  const content = response.getContentText();
  if (code < 200 || code >= 300) {
    throw new Error(`Firestore respondió ${code}: ${content.slice(0, 800)}`);
  }
  return content ? JSON.parse(content) : {};
}

function texto_(fields, key, fallback) {
  const value = fields[key];
  if (!value) return fallback;
  if (Object.prototype.hasOwnProperty.call(value, 'stringValue')) {
    return String(value.stringValue || fallback);
  }
  if (Object.prototype.hasOwnProperty.call(value, 'integerValue')) {
    return String(value.integerValue);
  }
  return fallback;
}

function construirHtml_(data) {
  const safe = {};
  Object.keys(data).forEach(key => safe[key] = escaparHtml_(String(data[key])));
  return `
    <div style="font-family:Arial,sans-serif;max-width:640px;color:#21170d">
      <div style="background:#1b140d;color:#e5b94f;padding:18px 22px;border-radius:12px 12px 0 0">
        <h2 style="margin:0">Nuevo comentario de Traidores</h2>
      </div>
      <div style="border:1px solid #b98524;padding:20px 22px;border-radius:0 0 12px 12px">
        <p><strong>Nombre:</strong> ${safe.nombre}</p>
        <p><strong>Asunto:</strong> ${safe.asunto}</p>
        <div style="white-space:pre-wrap;background:#f6f0e5;padding:14px;border-radius:8px">${safe.mensaje}</div>
        <hr style="border:0;border-top:1px solid #dbc99d;margin:20px 0">
        <p style="color:#6e6253;font-size:13px">
          Versión ${safe.version}<br>
          ${safe.dispositivo} · Android ${safe.android}<br>
          UID ${safe.uid}<br>
          Documento ${safe.id}
        </p>
      </div>
    </div>`;
}

function escaparHtml_(value) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}
