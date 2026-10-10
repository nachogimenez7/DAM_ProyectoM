'use strict';
// Temporary Debug configuration. Never changes app/google-services.json or Release.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..');
const target = path.join(root, 'app/src/debug/google-services.json');
const marker = 'traidores-local';
const operation = process.argv[2];
const isolatedApp = process.argv.includes('--isolated-app');
assert.ok(['prepare', 'cleanup'].includes(operation), 'Use prepare or cleanup.');
if (fs.existsSync(target)) {
  assert.equal(JSON.parse(fs.readFileSync(target, 'utf8')).project_info.project_id, marker,
    'Refusing to replace or remove another Debug Firebase configuration.');
}
if (operation === 'cleanup') {
  if (fs.existsSync(target)) fs.unlinkSync(target);
  console.log('Removed the temporary Android Firebase emulator configuration.');
} else {
  const config = JSON.parse(fs.readFileSync(path.join(root, 'app/google-services.json'), 'utf8'));
  config.project_info.project_id = marker;
  config.project_info.firebase_url = 'https://traidores-local-default-rtdb.firebaseio.com';
  config.project_info.storage_bucket = 'traidores-local.firebasestorage.app';
  if (isolatedApp) {
    for (const client of config.client) {
      if (client.client_info.android_client_info.package_name === 'com.traidores.juego') {
        client.client_info.android_client_info.package_name = 'com.traidores.juego.v3qa';
      }
    }
  }
  fs.mkdirSync(path.dirname(target), {recursive: true});
  fs.writeFileSync(target, `${JSON.stringify(config, null, 2)}\n`);
  console.log('Prepared isolated Firebase configuration for Android Debug QA. Run cleanup afterward.');
}
