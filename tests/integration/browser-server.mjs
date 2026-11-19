// Solo emuladores. No recibe ni necesita credenciales reales.
import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {createWriteStream} from 'node:fs';
const root=fileURLToPath(new URL('../../',import.meta.url));
const children=[];
for(const [module,port] of [['backend-api',20880],['backend-realtime',20881]]) {
 const child=spawn('java',['-Xms32m','-Xmx256m','-XX:ActiveProcessorCount=2','-Dapp.socket.proof-enabled=false','-jar',`${module}/target/quarkus-app/quarkus-run.jar`],{
  cwd:root,env:{...process.env,QUARKUS_PROFILE:'local',GOOGLE_CLOUD_PROJECT:'demo-app-meet',HTTP_PORT:String(port),
   APP_SOCKET_PORT:'20992',APP_ALLOWED_ORIGINS:'http://localhost:4200',FIRESTORE_EMULATOR_HOST:'127.0.0.1:8085',FIREBASE_AUTH_EMULATOR_HOST:'127.0.0.1:9099'},stdio:['ignore','pipe','pipe']});
 const log=createWriteStream(`${root}/${module}/target/browser-integration.log`);child.stdout.pipe(log);child.stderr.pipe(log);children.push(child);
 child.on('exit',code=>{if(!stopping) {console.error(`${module} finalizó: ${code}`);shutdown();}});
}
let stopping=false;
function shutdown(){if(stopping)return;stopping=true;for(const c of children)c.kill('SIGTERM');const timer=setTimeout(()=>{for(const c of children)if(c.exitCode===null)c.kill('SIGKILL');},12000);timer.unref();}
process.on('SIGTERM',shutdown);process.on('SIGINT',shutdown);
