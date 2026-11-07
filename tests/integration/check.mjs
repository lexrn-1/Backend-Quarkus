import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { createWriteStream, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { io } from 'socket.io-client';
import { runIdentityChecks, runRecoveryChecks, runAccountSecurityChecks, runEmailPolicyChecks } from './identity.mjs';
import { createConnection } from 'node:net';
import { setTimeout as delay } from 'node:timers/promises';

const root = fileURLToPath(new URL('../../', import.meta.url));
const children = [], sockets = [];
const project = 'demo-app-meet';
const origin = 'http://localhost:4200';
const allowedOrigins = ['https://frontln.vercel.app',origin,'http://127.0.0.1:4200'];
const apiPort = 18080, rtPort = 18081, socketPort = 19092;
const firestore = `http://${process.env.FIRESTORE_EMULATOR_HOST || '127.0.0.1:8085'}`;
const auth = `http://${process.env.FIREBASE_AUTH_EMULATOR_HOST || '127.0.0.1:9099'}`;
const env = { ...process.env, GOOGLE_CLOUD_PROJECT: project, QUARKUS_PROFILE: 'local',
  FIRESTORE_EMULATOR_HOST: new URL(firestore).host, FIREBASE_AUTH_EMULATOR_HOST: new URL(auth).host,
  APP_SOCKET_PORT: String(socketPort), APP_PROOF_SESSION: 'session-a,session-b' };

function launch(module, port, overrides = {}) {
  const log = createWriteStream(new URL(`../../${module}/target/integration-${port}.log`, import.meta.url));
  const child = spawn('java', ['-Xms32m', '-Xmx256m', '-XX:ActiveProcessorCount=2', '-jar', `${module}/target/quarkus-app/quarkus-run.jar`], {
    cwd: root, env: { ...env, HTTP_PORT: String(port), ...overrides }, stdio: ['ignore', 'pipe', 'pipe']
  });
  child.stdout.pipe(log); child.stderr.pipe(log);
  // Capture exit at launch time: it may happen before cleanup begins.
  child.finished = new Promise(resolve => child.once('close', (code, signal) => resolve([code, signal])));
  children.push(child);
  return child;
}
async function stop(child) {
  if (child.exitCode !== null || child.signalCode !== null) return child.finished;
  const forced = setTimeout(() => child.kill('SIGKILL'), 15000);
  child.kill('SIGTERM');
  try { return await child.finished; }
  finally { clearTimeout(forced); }
}
async function waitFor(url, status = 200) {
  for (let n = 0; n < 120; n++) {
    try { const r = await fetch(url, {signal: AbortSignal.timeout(4000)}); if (r.status === status) return r; } catch {}
    if (children.some(c => c.exitCode !== null)) throw new Error('Un backend terminó; revisar target/integration-*.log');
    await delay(250);
  }
  throw new Error(`Timeout: ${url}`);
}
async function seed(message) {
  const r = await fetch(`${firestore}/v1/projects/${project}/databases/(default)/documents/demo/sprint0`, {
    method: 'PATCH', headers: {'Content-Type':'application/json', Authorization:'Bearer owner'},
    body: JSON.stringify({fields:{message:{stringValue:message}}})
  });
  assert.equal(r.status, 200);
}
function connection(authData, transport = 'websocket', extraOrigin = origin) {
  const s = io(`http://127.0.0.1:${socketPort}`, {
    auth: authData, transports: [transport], extraHeaders:{Origin:extraOrigin},
    autoConnect:false, reconnection:false, timeout:5000
  });
  sockets.push(s); return s;
}
async function connected(s) {
  const result = new Promise((resolve,reject) => {
    s.once('connect',resolve); s.once('connect_error',reject);
  });
  s.connect(); await result; return s;
}
async function rejected(s, label) {
  console.log(`CHECK rechazo: ${label}`);
  const result = new Promise((resolve,reject) => {
    const timer=setTimeout(()=>reject(new Error('No rechazó conexión')),7000);
    s.once('connect',()=>{clearTimeout(timer);reject(new Error('Aceptó conexión no autorizada'));});
    s.once('connect_error',()=>{clearTimeout(timer);resolve();});
  });
  s.connect(); await result; s.close();
}
const ack = (s,event,data={}) => new Promise((resolve,reject)=>s.timeout(5000).emit(event,data,(err,response)=>err?reject(err):resolve(response)));
try {
  launch('backend-api',apiPort); launch('backend-realtime',rtPort);
  for (const port of [apiPort,rtPort]) {
    await waitFor(`http://127.0.0.1:${port}/q/health/ready`);
    for (const path of ['/q/health/live','/q/openapi','/q/swagger-ui/'])
      assert.equal((await fetch(`http://127.0.0.1:${port}${path}`)).status,200);
  }
  await fetch(`${firestore}/v1/projects/${project}/databases/(default)/documents/demo/sprint0`,{method:'DELETE',headers:{Authorization:'Bearer owner'}});
  for (const port of [apiPort,rtPort]) {
    for (const allowed of allowedOrigins) {
      const response=await fetch(`http://127.0.0.1:${port}/q/openapi`,{headers:{Origin:allowed}});
      assert.equal(response.headers.get('access-control-allow-origin'),allowed);
    }
    const blocked=await fetch(`http://127.0.0.1:${port}/q/openapi`,{headers:{Origin:'https://evil.invalid'}});
    assert.notEqual(blocked.headers.get('access-control-allow-origin'),'https://evil.invalid');
  }
  console.log('PASS CORS: Vercel, localhost y loopback permitidos en ambos servicios; origen ajeno bloqueado');
  const url=`http://127.0.0.1:${apiPort}/api/v1/demo`;
  assert.equal((await fetch(url)).status,404);
  await seed('Sprint 0'); assert.deepEqual(await (await fetch(url)).json(),{message:'Sprint 0'});
  await seed('Actualizado'); assert.deepEqual(await (await fetch(url)).json(),{message:'Actualizado'});
  const cors=await fetch(url,{headers:{Origin:origin}}); assert.equal(cors.headers.get('access-control-allow-origin'),origin);
  const deniedCors=await fetch(url,{headers:{Origin:'https://evil.invalid'}});
  assert.notEqual(deniedCors.headers.get('access-control-allow-origin'),'https://evil.invalid');
  console.log('PASS demo: 404, lectura real del emulador, actualización sin caché, CORS, health y Swagger');
  const preflight=await fetch(`http://127.0.0.1:${apiPort}/api/v1/me/complete`,{method:'OPTIONS',headers:{
    Origin:origin,'Access-Control-Request-Method':'POST','Access-Control-Request-Headers':'authorization,content-type'}});
  assert.ok([200,204].includes(preflight.status));
  assert.equal(preflight.headers.get('access-control-allow-origin'),origin);
  assert.ok(preflight.headers.get('access-control-allow-methods').includes('POST'));
  const spec=await (await fetch(`http://127.0.0.1:${apiPort}/q/openapi`,{headers:{Accept:'application/json'}})).json();
  assert.equal(spec.components.securitySchemes.FirebaseBearer.scheme,'bearer');
  assert.ok(spec.paths['/api/v1/me/complete'].post.security.some(s=>s.FirebaseBearer));
  const {goodToken,pendingToken,revokedToken}=await runIdentityChecks({api:`http://127.0.0.1:${apiPort}/api/v1`,auth});
  await runAccountSecurityChecks({api:`http://127.0.0.1:${apiPort}/api/v1`,auth});
  const direct=await fetch(`${firestore}/v1/projects/${project}/databases/(default)/documents/demo/sprint0`,{headers:{Authorization:`Bearer ${goodToken}`}});
  assert.equal(direct.status,403);
  console.log('PASS reglas Firestore: acceso directo del cliente denegado');
  await rejected(connection({token:pendingToken,proofSession:'session-a'}), 'perfil incompleto');
  await rejected(connection({token:revokedToken,proofSession:'session-a'}), 'sesión cerrada');
  await rejected(connection(undefined), "sin auth");
  await rejected(connection({}), "auth vacío");
  await rejected(connection({token:'invalid',proofSession:'session-a'}), 'token inválido');
  await rejected(connection({token:goodToken,proofSession:'not-allowed'}), 'sesión inválida');
  await rejected(connection({token:goodToken,proofSession:'session-a'},'websocket','https://evil.invalid'), 'origen inválido');
  for (const allowed of allowedOrigins) {
    const client=await connected(connection({token:goodToken,proofSession:'session-a'},'websocket',allowed));
    assert.equal((await ack(client,'proof:ping')).ok,true); client.close();
  }
  console.log('PASS Socket.IO CORS: Vercel, localhost y loopback conectan con token válido');
  for (const transport of ['polling','websocket']) {
    const s=await connected(connection({token:goodToken,proofSession:'session-a'},transport));
    assert.equal((await ack(s,'proof:ping')).ok,true); s.close();
  }
  const a=await connected(connection({token:goodToken,proofSession:'session-a'}));
  const b=await connected(connection({token:goodToken,proofSession:'session-a'}));
  const other=await connected(connection({token:goodToken,proofSession:'session-b'}));
  const bId=(await ack(b,'proof:ping')).data.connectionId;
  const otherId=(await ack(other,'proof:ping')).data.connectionId;
  let leaked=false; other.on('proof:signal',()=>{leaked=true;});
  const received=once(b,'proof:signal',{signal:AbortSignal.timeout(5000)});
  assert.equal((await ack(a,'proof:signal',{toConnectionId:bId,signal:{type:'offer',sdp:'synthetic-test'}})).ok,true);
  assert.equal((await received)[0].signal.type,'offer');
  assert.equal((await ack(a,'proof:signal',{toConnectionId:otherId,signal:{type:'offer'}})).error,'FORBIDDEN');
  assert.equal((await ack(a,'proof:signal',{toConnectionId:'invalid'})).error,'INVALID_SIGNAL');
  await delay(200); assert.equal(leaked,false);
  a.io.reconnection(true);
  const reconnected=once(a,'connect',{signal:AbortSignal.timeout(15000)}); a.io.engine.close(); await reconnected;
  assert.equal((await ack(a,'proof:ping')).ok,true);
  console.log('PASS Socket.IO: auth, rechazo sin token/inválido/origen, polling, websocket, ack, aislamiento y reconexión');
  const loggedOut=await fetch(`http://127.0.0.1:${apiPort}/api/v1/session/logout`,{method:'POST',headers:{Authorization:`Bearer ${goodToken}`}});
  assert.equal(loggedOut.status,204);
  const kicked=once(a,'disconnect',{signal:AbortSignal.timeout(8000)});
  a.emit('proof:ping',{}); await kicked;
  console.log('PASS realtime: logout invalida eventos de una conexión que ya estaba abierta');
  launch('backend-api',18082,{FIRESTORE_EMULATOR_HOST:'127.0.0.1:1'});
  await waitFor('http://127.0.0.1:18082/q/health/live');
  assert.equal((await fetch('http://127.0.0.1:18082/api/v1/demo')).status,503);
  assert.equal((await fetch('http://127.0.0.1:18082/q/health/ready')).status,503);
  console.log('PASS dependencia caída: demo 503, readiness DOWN y liveness UP');
  await runRecoveryChecks({api:`http://127.0.0.1:${apiPort}/api/v1`,failedApi:'http://127.0.0.1:18082/api/v1',auth});
  launch('backend-api',18085,{APP_ALLOWED_EMAIL_DOMAINS:'university.edu.co'});
  await waitFor('http://127.0.0.1:18085/q/health/ready');
  await runEmailPolicyChecks({api:'http://127.0.0.1:18085/api/v1',auth});
  // Release integration services before testing isolated invalid production startup.
  for (const child of children) {
    await stop(child);
    assert.notEqual(child.signalCode,'SIGKILL','Los servicios deben cerrar limpiamente');
  }
  for (const [module,port] of [['backend-api',18083],['backend-realtime',18084]]) {
    const child=launch(module,port,{QUARKUS_PROFILE:'prod'});
    const force=setTimeout(()=>child.kill('SIGKILL'),30000);
    const [code,signal]=await child.finished; clearTimeout(force);
    assert.equal(signal,null); assert.notEqual(code,0);
    assert.match(readFileSync(`${root}${module}/target/integration-${port}.log`,'utf8'),/Emuladores no permitidos/);
  }
  console.log('PASS producción: ambos servicios rechazan variables de emuladores al iniciar');
} finally {
  for (const s of sockets) s.close();
  await Promise.all(children.map(stop));
}
await new Promise((resolve,reject)=>{
  const socket=createConnection({host:'127.0.0.1',port:socketPort});
  socket.once('connect',()=>{socket.destroy();reject(new Error('Listener no cerrado'));});
  socket.once('error',error=>error.code==='ECONNREFUSED'?resolve():reject(error));
});
console.log('PASS procesos cerrados; prueba local finalizada (sin Firebase real, TLS ni WebRTC)');
