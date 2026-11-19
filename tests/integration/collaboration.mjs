import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { once } from 'node:events';
import { setTimeout as delay } from 'node:timers/promises';
import { io } from 'socket.io-client';
const origin='http://localhost:4200';
async function request(base,path,token,method='GET',body){return fetch(base+path,{method,headers:{...(token?{Authorization:`Bearer ${token}`} : {}),...(body!==undefined?{'Content-Type':'application/json'}:{})},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(30000)});}
export async function runCollaborationChecks({api,realtime,auth,socketPort,firestore,restartApi}){
 const suffix=randomUUID().slice(0,8),clients=[];
 async function account(n){
  const email=`room-${suffix}-${n}@example.test`;
  const response=await request(auth,'/identitytoolkit.googleapis.com/v1/accounts:signUp?key=local',null,'POST',{email,password:'StudySprints42!',returnSecureToken:true});assert.equal(response.status,200);
  const user=await response.json();assert.equal((await request(api,'/me/complete',user.idToken,'POST',{firstName:'User',lastName:String(n),username:`r_${suffix}_${n}`,avatar:'avatar-1'})).status,200);return user;
 }
 async function connected(user){
  const client=io(`http://127.0.0.1:${socketPort}`,{auth:{token:user.idToken,proofSession:'session-a'},transports:['websocket'],extraHeaders:{Origin:origin},reconnection:false,autoConnect:false,timeout:10000});clients.push(client);
  const promise=new Promise((resolve,reject)=>{client.once('connect',resolve);client.once('connect_error',reject);});client.connect();await promise;return client;
 }
 const event=(client,name,roomId,payload={})=>new Promise((resolve,reject)=>client.timeout(15000).emit(name,{v:1,eventId:randomUUID(),roomId,payload},(error,result)=>error?reject(error):resolve(result)));
 try{
  const users=[];for(let n=0;n<8;n++)users.push(await account(n));
  const owner=users[0],guest=users[1];
  assert.equal((await request(api,'/rooms',null)).status,401);
  for(const name of ['',' '.repeat(4),'x'.repeat(101)])assert.equal((await request(api,'/rooms',owner.idToken,'POST',{name})).status,400);
  const created=await request(api,'/rooms',owner.idToken,'POST',{name:'  Study  '});assert.equal(created.status,201);
  const room=await created.json();assert.equal(room.name,'Study');assert.equal(room.role,'OWNER');assert.equal(room.ownerUid,owner.localId);
  assert.equal((await request(api,`/rooms/${room.id}`,guest.idToken)).status,403);
  assert.equal((await request(api,`/rooms/${room.id}`,guest.idToken,'PATCH',{name:'spoof'})).status,403);
  assert.equal((await request(api,`/rooms/${room.id}`,guest.idToken,'DELETE')).status,403);
  assert.equal((await request(api,`/rooms/${randomUUID()}/join`,guest.idToken,'POST')).status,404);
  for(const user of users)assert.equal((await request(api,`/rooms/${room.id}/join`,user.idToken,'POST')).status,200);
  assert.equal((await request(api,`/rooms/${room.id}/join`,guest.idToken,'POST')).status,200);
  const owned=await (await request(api,'/rooms?limit=1',owner.idToken)).json();assert.equal(owned.items[0].id,room.id);
  const joined=await (await request(api,'/rooms?scope=member',guest.idToken)).json();assert.ok(joined.items.some(r=>r.id===room.id));
  const another=await (await request(api,'/rooms',owner.idToken,'POST',{name:'Other'})).json();
  const page=await (await request(api,'/rooms?limit=1',owner.idToken)).json();assert.ok(page.nextCursor);
  const next=await (await request(api,`/rooms?limit=1&cursor=${encodeURIComponent(page.nextCursor)}`,owner.idToken)).json();assert.equal(next.items.length,1);assert.notEqual(next.items[0].id,page.items[0].id);
  const ice=await (await request(api,'/rtc/config',owner.idToken)).json();assert.ok(ice.iceServers.some(s=>s.urls.includes('stun:stun.l.google.com:19302')));assert.equal(ice.maxConnections,6);
  console.log('PASS salas: creación, validación, permisos OWNER, unión idempotente, listado/paginación y configuración ICE');
  for(let n=0;n<7;n++)await connected(users[n]);
  const admissions=await Promise.all(clients.map(client=>event(client,'room:join',room.id)));
  assert.equal(admissions.filter(a=>a.ok).length,6);assert.equal(admissions.filter(a=>a.error?.code==='ROOM_FULL').length,1);
  const winnerIndex=admissions.findIndex(a=>a.ok),sender=clients[winnerIndex],senderUser=users[winnerIndex];
  const receiverIndex=admissions.findIndex((a,i)=>a.ok&&i!==winnerIndex),receiver=clients[receiverIndex];
  const receiverId=admissions[receiverIndex].data.connectionId;
  const isolated=await connected(users[7]);assert.equal((await event(isolated,'room:join',room.id)).error.code,'ROOM_FULL');
  assert.equal((await request(api,`/rooms/${another.id}/join`,users[7].idToken,'POST')).status,200);
  const isolatedJoin=await event(isolated,'room:join',another.id);assert.equal(isolatedJoin.ok,true);
  const forbidden=await event(sender,'rtc:signal',room.id,{toConnectionId:isolatedJoin.data.connectionId,signal:{type:'offer',sdp:'synthetic'}});assert.equal(forbidden.error.code,'FORBIDDEN');
  const signal=once(receiver,'rtc:signal',{signal:AbortSignal.timeout(10000)});
  assert.equal((await event(sender,'rtc:signal',room.id,{toConnectionId:receiverId,signal:{type:'offer',sdp:'synthetic'}})).ok,true);
  assert.equal((await signal)[0].payload.fromConnectionId,admissions[winnerIndex].data.connectionId);
  const media=once(receiver,'media:update',{signal:AbortSignal.timeout(10000)});
  assert.equal((await event(sender,'media:update',room.id,{micOn:true,camOn:false,screenSharing:true})).ok,true);
  assert.equal((await media)[0].payload.participant.screenSharing,true);
  console.log('PASS realtime: seis admisiones atómicas, séptima rechazada, snapshot, estados y SDP aislado por sala');
  let leaked=false;isolated.on('chat:message',()=>{leaked=true;});
  const delivered=once(receiver,'chat:message',{signal:AbortSignal.timeout(10000)});
  const clientId=randomUUID();const first=await event(sender,'chat:send',room.id,{clientMessageId:clientId,text:'  Hola  '});assert.equal(first.ok,true);assert.equal(first.data.sender.uid,senderUser.localId);assert.equal(first.data.text,'Hola');
  assert.equal((await delivered)[0].payload.message.id,first.data.id);
  const duplicate=await event(sender,'chat:send',room.id,{clientMessageId:clientId,text:'Hola'});assert.equal(duplicate.data.id,first.data.id);
  assert.equal((await event(sender,'chat:send',room.id,{clientMessageId:clientId,text:'changed'})).error.code,'MESSAGE_ID_CONFLICT');
  assert.equal((await event(sender,'chat:send',room.id,{clientMessageId:randomUUID(),text:'   '})).error.code,'INVALID_MESSAGE');
  assert.equal((await event(sender,'chat:send',room.id,{clientMessageId:randomUUID(),text:'spoof',senderUid:'someone'})).error.code,'INVALID_EVENT');
  for(let n=0;n<34;n++)assert.equal((await event(sender,'chat:send',room.id,{clientMessageId:randomUUID(),text:`message-${n}`})).ok,true);
  const historyPath=`/rooms/${room.id}/messages`;
  assert.equal((await request(realtime,historyPath,null)).status,401);
  assert.equal((await request(realtime,`/rooms/${another.id}/messages`,guest.idToken)).status,403);
  const recent=await (await request(realtime,historyPath+'?limit=10',owner.idToken)).json();assert.equal(recent.items.length,10);assert.ok(recent.nextCursor);
  assert.ok(recent.items.every((m,i,a)=>!i||m.createdAt>=a[i-1].createdAt));
  const older=await (await request(realtime,historyPath+`?limit=10&before=${encodeURIComponent(recent.nextCursor)}`,owner.idToken)).json();assert.equal(older.items.length,10);assert.ok(older.items.every(m=>!recent.items.some(n=>n.id===m.id)));
  const catchup=await (await request(realtime,historyPath+`?limit=100&after=${encodeURIComponent(first.data.cursor)}`,owner.idToken)).json();assert.equal(catchup.items.length,34);
  assert.equal((await request(realtime,historyPath+'?before=bad',owner.idToken)).status,400);
  await delay(100);assert.equal(leaked,false);
  console.log('PASS chat/historial: persistencia antes de ack, reintentos sin duplicados, aislamiento, cursor estable before/after y entradas rechazadas');
  const renamed=once(receiver,'room:updated',{signal:AbortSignal.timeout(10000)});
  assert.equal((await request(api,`/rooms/${room.id}`,owner.idToken,'PATCH',{name:'Renamed'})).status,200);assert.equal((await renamed)[0].payload.name,'Renamed');
  const deleted=once(receiver,'room:deleted',{signal:AbortSignal.timeout(15000)});
  assert.equal((await request(api,`/rooms/${room.id}`,owner.idToken,'DELETE')).status,202);await deleted;
  assert.equal((await event(sender,'chat:send',room.id,{clientMessageId:randomUUID(),text:'after delete'})).ok,false);
  let removed=false;for(let n=0;n<30;n++){if((await request(api,`/rooms/${room.id}`,owner.idToken)).status===404){removed=true;break;}await delay(500);}assert.ok(removed,'Limpieza completa después del tombstone');
  assert.equal((await request(realtime,historyPath,owner.idToken)).status,404);
  assert.equal((await request(api,`/rooms/${another.id}`,owner.idToken,'DELETE')).status,202);
  const documents=`${firestore}/v1/projects/demo-app-meet/databases/(default)/documents`;
  async function clean(id){
   for(const collection of ['messages','members']){
    const response=await request(documents,`/rooms/${id}/${collection}`,'owner');assert.equal(response.status,200);
    assert.equal(((await response.json()).documents??[]).length,0,'No dejar subcolecciones huérfanas');
   }
   const job=await (await request(documents,`/roomDeletionJobs/${id}`,'owner')).json();assert.equal(job.fields.status.stringValue,'DONE');
  }
  await clean(room.id);
  // Simular un cierre justo después del commit de DELETING/job; el emulador conserva los datos.
  const recovery=await (await request(api,'/rooms',owner.idToken,'POST',{name:'Recovery job'})).json();
  await restartApi(async()=>{
   assert.equal((await request(documents,`/rooms/${recovery.id}?updateMask.fieldPaths=status`,'owner','PATCH',{fields:{status:{stringValue:'DELETING'}}})).status,200);
   assert.equal((await request(documents,`/rooms/${recovery.id}/messages/recovery`,'owner','PATCH',{fields:{text:{stringValue:'pending cleanup'}}})).status,200);
   assert.equal((await request(documents,`/roomDeletionJobs/${recovery.id}`,'owner','PATCH',{fields:{roomId:{stringValue:recovery.id},status:{stringValue:'PENDING'}}})).status,200);
  });
  let recovered=false;for(let n=0;n<30;n++){if((await request(api,`/rooms/${recovery.id}`,owner.idToken)).status===404){recovered=true;break;}await delay(500);}assert.ok(recovered);
  await clean(recovery.id);console.log('PASS recuperación de borrado: nuevo proceso retoma job PENDING y elimina subcolecciones sin huérfanos');
  console.log('PASS ciclo de sala: rename notificado, eliminación expulsa/impide escrituras y limpieza durable de historial/membresías');
 }finally{for(const client of clients)client.close();}
}
