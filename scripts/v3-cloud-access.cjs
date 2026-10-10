"use strict";
const fs = require("node:fs");
const path = require("node:path");
const assert = require("node:assert/strict");
const cliAuth = require("firebase-tools/lib/auth");
const cliApi = require("firebase-tools/lib/api");
const root = path.resolve(__dirname,"..");
function encode(value) {
  if (value === null) return {nullValue:null};
  if (typeof value === "string") return {stringValue:value};
  if (typeof value === "boolean") return {booleanValue:value};
  if (typeof value === "number") return Number.isInteger(value) ? {integerValue:String(value)} : {doubleValue:value};
  if (Array.isArray(value)) return {arrayValue:{values:value.map(encode)}};
  return {mapValue:{fields:Object.fromEntries(Object.entries(value).map(([k,v])=>[k,encode(v)]))}};
}
function decode(value) {
  if ("nullValue" in value) return null;
  if ("stringValue" in value) return value.stringValue;
  if ("booleanValue" in value) return value.booleanValue;
  if ("integerValue" in value) return Number(value.integerValue);
  if ("doubleValue" in value) return value.doubleValue;
  if ("timestampValue" in value) return value.timestampValue;
  if ("arrayValue" in value) return (value.arrayValue.values || []).map(decode);
  return Object.fromEntries(Object.entries(value.mapValue?.fields || {}).map(([k,v])=>[k,decode(v)]));
}
async function cloudAccess() {
  for (const key of ["FIRESTORE_EMULATOR_HOST","FIREBASE_DATABASE_EMULATOR_HOST","FIREBASE_AUTH_EMULATOR_HOST","FUNCTIONS_EMULATOR"])
    assert.ok(!process.env[key],"Cloud commands cannot run with emulator routing");
  const google = JSON.parse(fs.readFileSync(path.join(root,"app/google-services.json"),"utf8"));
  assert.equal(google.project_info.project_id,"traidores");
  const android = google.client.find(c=>c.client_info.android_client_info.package_name === "com.traidores.juego");
  const config = {projectId:"traidores",projectNumber:String(google.project_info.project_number),appId:android.client_info.mobilesdk_app_id,
    apiKey:android.api_key[0].current_key,databaseURL:google.project_info.firebase_url};
  const account = cliAuth.getProjectDefaultAccount(root);
  assert.ok(account?.tokens?.refresh_token,"Firebase CLI login required");
  cliApi.setScopes(["https://www.googleapis.com/auth/cloud-platform"]);
  const token = await cliAuth.getAccessToken(account.tokens.refresh_token,cliApi.getScopes());
  async function api(url,options={}) {
    const host = new URL(url).hostname;
    assert.ok(host.endsWith(".googleapis.com") || host === new URL(config.databaseURL).hostname,"Unexpected Cloud API host");
    const response = await fetch(url,{...options,headers:{Authorization:`Bearer ${token.access_token}`,"Content-Type":"application/json",...options.headers},signal:AbortSignal.timeout(60000)});
    if (!response.ok) {
      const result = await response.json().catch(()=>({}));
      const error = new Error(`Cloud API ${new URL(url).pathname} ${response.status}: ${result.error?.message || response.statusText}`);
      error.status = response.status; throw error;
    }
    return response.status === 204 ? null : response.json();
  }
  const documentBase = "https://firestore.googleapis.com/v1/projects/traidores/databases/(default)/documents/";
  async function getDoc(documentPath) {
    try { const result=await api(documentBase+documentPath); return {fields:result.fields,data:decode({mapValue:{fields:result.fields}}),updateTime:result.updateTime}; }
    catch(e) {if (e.status===404) return null; throw e;}
  }
  async function setDoc(documentPath,data) {
    return api(documentBase+documentPath,{method:"PATCH",body:JSON.stringify({fields:encode(data).mapValue.fields})});
  }
  return {config,api,documentBase,getDoc,setDoc};
}
module.exports = {cloudAccess,encode,decode};
if (require.main === module) (async()=>{
  const {api,config,getDoc}=await cloudAccess();
  const [billing,database,fn,gate,release,fields,indexes]=await Promise.all([
    api("https://cloudbilling.googleapis.com/v1/projects/traidores/billingInfo"),
    api("https://firestore.googleapis.com/v1/projects/traidores/databases/(default)"),
    api("https://cloudfunctions.googleapis.com/v2/projects/traidores/locations/southamerica-west1/functions/iniciarPartidaV2"),
    getDoc("onlineMaintenance/serverAuthority"),
    api("https://firebaserules.googleapis.com/v1/projects/traidores/releases/cloud.firestore"),
    api("https://firestore.googleapis.com/v1/projects/traidores/databases/(default)/collectionGroups/-/fields?filter=indexConfig.usesAncestorConfig%3Dfalse%20OR%20ttlConfig%3A%2A"),
    api("https://firestore.googleapis.com/v1/projects/traidores/databases/(default)/collectionGroups/-/indexes")]);
  const ruleset=await api("https://firebaserules.googleapis.com/v1/"+release.rulesetName);
  const rtdbRules=await api(config.databaseURL.replace(/\/$/,"")+"/.settings/rules.json");
  fs.mkdirSync(path.join(root,"output/v3-cloud"),{recursive:true});
  fs.writeFileSync(path.join(root,"output/v3-cloud/preflight.json"),JSON.stringify({billing,location:database.locationId,
    serviceAccountEmail:fn.serviceConfig.serviceAccountEmail,gate,release,fields,indexes},null,2));
  fs.writeFileSync(path.join(root,"output/v3-cloud/firestore-rules-before.json"),JSON.stringify(ruleset,null,2));
  fs.writeFileSync(path.join(root,"output/v3-cloud/database-rules-before.json"),JSON.stringify(rtdbRules,null,2));
  console.log(JSON.stringify({billingEnabled:billing.billingEnabled,firestoreLocation:database.locationId,
    runtimeIdentity:fn.serviceConfig.serviceAccountEmail,gate:gate?.data || null,backups:"output/v3-cloud/"}));
})().catch(e=>{console.error(e.message);process.exitCode=1;});
