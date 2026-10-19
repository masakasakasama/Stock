// Recover a debuggable legacy APK WITHOUT using its compromised signing key or uninstalling it.
import { spawnSync } from 'node:child_process';
import { pbkdf2Sync, randomBytes, createCipheriv, createDecipheriv, createHash } from 'node:crypto';
import { writeFile, readFile } from 'node:fs/promises';
import { resolve, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
export function parsePreferences(xml) {
  const python=process.env.PYTHON || (process.platform==='win32'?'python':'python3');
  const result=spawnSync(python,[resolve(dirname(fileURLToPath(import.meta.url)),'parse-android-preferences.py')],{input:xml,maxBuffer:16*1024*1024,windowsHide:true});
  if(result.error||result.status!==0)throw Error('Preferences XML could not be validated; original app has not been changed');
  const prefs=JSON.parse(result.stdout.toString('utf8'));
  for(const entry of Object.values(prefs))if(entry.t==='set')entry.v.sort();
  return prefs;
}
export function encrypt(raw,password) {
  if(!password||password.length<12)throw Error('Set BACKUP_PASSPHRASE to at least 12 characters; do not put it on the command line');
  const plain=Buffer.from(raw);if(plain.length>16*1024*1024)throw Error('Backup too large');
  const salt=randomBytes(16),iv=randomBytes(12),header=Buffer.from([72,66,75,1]);
  const key=pbkdf2Sync(password,salt,210000,32,'sha256');const cipher=createCipheriv('aes-256-gcm',key,iv);cipher.setAAD(header);
  const bytes=Buffer.concat([header,salt,iv,cipher.update(plain),cipher.final(),cipher.getAuthTag()]);key.fill(0);return bytes;
}
export function decrypt(bytes,password) {
  if(!bytes.subarray(0,4).equals(Buffer.from([72,66,75,1])))throw Error('Invalid backup header');
  const key=pbkdf2Sync(password,bytes.subarray(4,20),210000,32,'sha256');
  const cipher=createDecipheriv('aes-256-gcm',key,bytes.subarray(20,32));cipher.setAAD(bytes.subarray(0,4));cipher.setAuthTag(bytes.subarray(-16));
  try{return Buffer.concat([cipher.update(bytes.subarray(32,-16)),cipher.final()]).toString('utf8');}finally{key.fill(0);}
}
async function main() {
  const [app,serial,output,qa]=process.argv.slice(2);
  if(!['Home','Stock'].includes(app)||!serial||!output||qa&&qa!=='--qa')throw Error('Usage: node scripts/legacy-adb-backup.mjs Home|Stock DEVICE_SERIAL OUTPUT.hbk [--qa]');
  const password=process.env.BACKUP_PASSPHRASE;if(!password||password.length<12)throw Error('BACKUP_PASSPHRASE must contain at least 12 characters');
  const pkg=(app==='Home'?'com.masakasakasama.home':'com.example.stockwidget')+(qa?'.securityqa':'');
  function adb(args,optional=false) {
    const result=spawnSync('adb',['-s',serial,...args],{maxBuffer:16*1024*1024+1024,windowsHide:true});
    if(result.error)throw Error('ADB unavailable');
    if(result.status!==0) {
      if(optional&&Buffer.concat([result.stdout,result.stderr]).toString().includes('No such file or directory'))return null;
      throw Error('ADB read failed. A debuggable legacy APK and USB debugging authorization are required. Do not uninstall the app.');
    }
    return result.stdout;
  }
  if(qa&&adb(['shell','getprop','ro.boot.qemu']).toString().trim()!=='1')throw Error('QA mode requires an emulator');
  const stopped=()=>{
    const result=spawnSync('adb',['-s',serial,'shell','pidof',pkg],{windowsHide:true});
    if(result.stdout?.toString().trim())throw Error('Save all edits and close/stop the app and its widgets first. This script does not stop or alter your app.');
  };
  adb(['exec-out','run-as',pkg,'id']);stopped();
  const stores=app==='Home'?['home_config','priority_progress','personal_gmail_update_counts']:['stock_widget_prefs','stock_updater'];
  const names=adb(['exec-out','run-as',pkg,'ls','shared_prefs'],true)?.toString().trim().split(/\s+/).filter(Boolean)||[];
  const excluded=['personal_home_secrets','stock_secrets','androidx.work.util.preferences','androidx.work.workdb'];
  const unknown=names.filter(name=>!stores.concat(excluded).some(store=>name===store+'.xml'||name===store+'.xml.bak'));
  if(unknown.length)throw Error('Unknown preferences were found. Extend the data inventory before exporting; no data has been changed.');
  const data={},reads=[];
  for(const store of stores) {
    const path='shared_prefs/'+store+'.xml';
    if(names.includes(store+'.xml.bak'))throw Error('Unfinished preference write found. Reopen/save/close the old app before retrying.');
    const xml=adb(['exec-out','run-as',pkg,'cat',path],true);reads.push([path,xml]);
    data[store]=xml?parsePreferences(xml):{};
    delete data[store].anthropic_api_key;
  }
  const files={};if(app==='Stock') {const raw=adb(['exec-out','run-as',pkg,'cat','files/crash.log'],true);reads.push(['files/crash.log',raw]);files['crash.log']=raw?raw.toString('utf8'):null;}
  stopped();
  for(const [path,original]of reads){const check=adb(['exec-out','run-as',pkg,'cat',path],true);if((original===null)!==(check===null)||original&&!original.equals(check))throw Error('Data changed during capture. Retry after saving and closing the app.');}
  const raw=JSON.stringify({version:1,app:pkg,stores:data,files});
  const bytes=encrypt(raw,password);await writeFile(resolve(output),bytes,{flag:'wx',mode:0o600});
  if(decrypt(await readFile(resolve(output)),password)!==raw)throw Error('Saved backup readback failed');
  console.log(JSON.stringify({result:'encrypted-legacy-backup-readback-verified',app,items:Object.fromEntries(Object.entries(data).map(([key,value])=>[key,Object.keys(value).length])),sha256:createHash('sha256').update(raw).digest('hex'),credentials:'excluded; reauthenticate',originalApp:'unchanged; do not uninstall until another-device restore succeeds'}));
}
if(process.argv[1]&&import.meta.url===pathToFileURL(resolve(process.argv[1])).href)main().catch(error=>{console.error(error.message);process.exitCode=1;});
