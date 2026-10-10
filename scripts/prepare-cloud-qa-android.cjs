'use strict';
// Local Debug-only config for the isolated APK; never edits the production config.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'), target=path.join(root,'app/src/debug/google-services.json');
const operation=process.argv[2];
assert.ok(['prepare','cleanup'].includes(operation),'Use prepare or cleanup');
if(fs.existsSync(target)) assert.equal(JSON.parse(fs.readFileSync(target)).codex_cloud_qa,true,
  'Another Debug configuration exists; leave it intact');
if(operation==='cleanup') {if(fs.existsSync(target))fs.unlinkSync(target);}
else {
  const source=JSON.parse(fs.readFileSync(path.join(root,'app/google-services.json')));
  assert.equal(source.project_info.project_id,'traidores');
  const client=source.client.find(c=>c.client_info.android_client_info.package_name==='com.traidores.juego');
  assert.ok(client); client.client_info.android_client_info.package_name='com.traidores.juego.v3qa';
  source.client=[client]; source.codex_cloud_qa=true;
  fs.mkdirSync(path.dirname(target),{recursive:true});fs.writeFileSync(target,JSON.stringify(source,null,2)+'\n');
}
console.log('Cloud QA Debug configuration '+(operation==='prepare'?'prepared':'removed')+'.');
