import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';

const stamp = randomUUID().replaceAll('-','').slice(0,10);
const password = 'StudySprints42!';
const draft = username => ({firstName:'Ana',lastName:'Pérez',username,avatar:'avatar-1'});

async function request(url, token, method='GET', data) {
  const headers={}; if(token) headers.Authorization=`Bearer ${token}`;
  if(data!==undefined) headers['Content-Type']='application/json';
  return fetch(url,{method,headers,body:data===undefined?undefined:JSON.stringify(data),signal:AbortSignal.timeout(30000)});
}
async function authCall(auth, method, body) {
  return request(`${auth}/identitytoolkit.googleapis.com/v1/accounts:${method}?key=local`,null,'POST',body);
}
async function signUp(auth, label) {
  const email=`${label}-${stamp}@example.test`;
  const response=await authCall(auth,'signUp',{email,password,returnSecureToken:true});
  assert.equal(response.status,200,'Alta Firebase Auth debe funcionar');
  return {email,...await response.json()};
}
async function google(auth) {
  const claims={sub:`google-${stamp}`,email:`google-${stamp}@example.test`,email_verified:true,name:'Google Student',picture:'https://example.org/avatar.png'};
  const postBody=new URLSearchParams({providerId:'google.com',id_token:JSON.stringify(claims)}).toString();
  const response=await authCall(auth,'signInWithIdp',{postBody,requestUri:'http://localhost',returnSecureToken:true});
  assert.equal(response.status,200,'Google emulado debe funcionar');
  return response.json();
}

export async function runIdentityChecks({api,auth}) {
  for(const path of ['/session','/me','/me/complete','/session/logout','/not-implemented']) {
    const method=path==='/me/complete'||path==='/session/logout'?'POST':'GET';
    const response=await request(api+path,null,method);
    assert.equal(response.status,401,`Sin sesión: ${path}`);
    assert.equal(response.headers.get('www-authenticate'),'Bearer');
    assert.equal((await response.json()).code,'UNAUTHORIZED');
  }
  assert.equal((await request(api+'/session','invalid')).status,401);
  const manual=await signUp(auth,'manual');
  const session=await request(api+'/session',manual.idToken);
  assert.equal(session.status,200,'Sesión Firebase válida');
  const pending=await session.json();
  assert.equal(pending.uid,manual.localId); assert.equal(pending.status,'PENDING');
  assert.equal(pending.nextAction,'COMPLETE_PROFILE');
  assert.equal((await request(api+'/me',manual.idToken)).status,403);
  assert.equal((await request(api+'/not-implemented',manual.idToken)).status,403);
  for(const invalid of [{...draft('valid_name'),firstName:''},{...draft('valid_name'),avatar:'javascript:alert(1)'},
      draft('too short username'),{...draft('valid_name'),uid:'forged'},{...draft('valid_name'),email:'forged@example.test'}]) {
    assert.equal((await request(api+'/me/complete',manual.idToken,'POST',invalid)).status,400,'Campos inválidos o identidad impuesta');
  }
  const completeDraft=draft(`manual_${stamp}`);
  const completed=await request(api+'/me/complete',manual.idToken,'POST',completeDraft);
  assert.equal(completed.status,200,'Completar perfil');
  const profile=await completed.json();
  assert.equal(profile.uid,manual.localId); assert.equal(profile.email,manual.email);
  assert.equal(profile.status,'COMPLETE'); assert.ok(profile.createdAt); assert.ok(profile.updatedAt);
  assert.deepEqual(await (await request(api+'/me/complete',manual.idToken,'POST',completeDraft)).json(),profile,'Reintento no cambia fechas ni duplica datos');
  assert.equal((await request(api+'/me/complete',manual.idToken,'POST',draft('different_name'))).status,409);
  const privateProfile=await request(api+'/me',manual.idToken);
  assert.equal(privateProfile.status,200); assert.equal(privateProfile.headers.get('cache-control'),'no-store');
  const login=await authCall(auth,'signInWithPassword',{email:manual.email,password,returnSecureToken:true});
  assert.equal(login.status,200);
  const logged=await login.json();
  assert.equal((await (await request(api+'/session',logged.idToken)).json()).nextAction,'ENTER');
  assert.equal((await authCall(auth,'signInWithPassword',{email:manual.email,password:'wrong-password',returnSecureToken:true})).status,400);
  assert.equal((await authCall(auth,'signInWithPassword',{email:'absent@example.test',password,returnSecureToken:true})).status,400);
  assert.equal((await authCall(auth,'signUp',{email:manual.email,password,returnSecureToken:true})).status,400);
  assert.equal((await authCall(auth,'signUp',{email:'not-an-email',password,returnSecureToken:true})).status,400);
  console.log('PASS US-01/03: registro/login Firebase, errores, uid confiable, perfil pendiente/privado, validación e idempotencia');

  const left=await signUp(auth,'race-left'),right=await signUp(auth,'race-right');
  await Promise.all([left,right].map(user=>request(api+'/session',user.idToken)));
  const shared=`shared_${stamp}`;
  const race=await Promise.all([
    request(api+'/me/complete',left.idToken,'POST',draft(shared.toUpperCase())),
    request(api+'/me/complete',right.idToken,'POST',draft(shared))
  ]);
  assert.deepEqual(race.map(r=>r.status).sort(),[200,409],'Solo un ganador concurrente');
  const loser=race[0].status===409?left:right;
  assert.equal((await (await request(api+'/session',loser.idToken)).json()).status,'PENDING');
  assert.equal((await request(api+'/me/complete',loser.idToken,'POST',draft(`retry_${stamp}`))).status,200);
  console.log('PASS US-01: username normalizado único bajo concurrencia real y reintento del perdedor');

  const g=await google(auth);
  const googlePending=await request(api+'/session',g.idToken);
  assert.equal(googlePending.status,200);
  const googleSession=await googlePending.json();
  assert.equal(googleSession.provider,'google.com'); assert.equal(googleSession.nextAction,'COMPLETE_PROFILE');
  assert.equal((await request(api+'/me',g.idToken)).status,403);
  assert.equal((await request(api+'/me/complete',g.idToken,'POST',draft(shared))).status,409);
  assert.equal((await request(api+'/me/complete',g.idToken,'POST',draft(`google_${stamp}`))).status,200);
  const recurrent=await google(auth);
  assert.equal(recurrent.localId,g.localId);
  assert.equal((await (await request(api+'/session',recurrent.idToken)).json()).nextAction,'ENTER');
  console.log('PASS US-02: Google inicial pendiente, colisión de username y Google recurrente con perfil completo');

  const expired=manual.idToken.split('.');
  const claims=JSON.parse(Buffer.from(expired[1],'base64url').toString());
  claims.exp=Math.floor(Date.now()/1000)-3600;
  expired[1]=Buffer.from(JSON.stringify(claims)).toString('base64url');
  assert.equal((await request(api+'/session',expired.join('.'))).status,401,'Token expirado');
  const pendingLogout=await signUp(auth,'pending-logout');
  assert.equal((await request(api+'/session/logout',pendingLogout.idToken,'POST')).status,204);
  assert.equal((await request(api+'/session',pendingLogout.idToken)).status,401);
  const logout=await signUp(auth,'logout');
  assert.equal((await request(api+'/me/complete',logout.idToken,'POST',draft(`logout_${stamp}`))).status,200);
  assert.equal((await request(api+'/session/logout',logout.idToken,'POST')).status,204);
  assert.equal((await request(api+'/me',logout.idToken)).status,401,'Token actual revocado inmediatamente');
  assert.equal((await request(api+'/me',manual.idToken)).status,200,'Logout no afecta otro usuario');
  console.log('PASS US-03: expiración y logout de perfiles pendientes/completos; token actual invalidado sin afectar otro usuario');
  return {goodToken:manual.idToken,pendingToken:(await signUp(auth,'pending-socket')).idToken,revokedToken:logout.idToken};
}

export async function runRecoveryChecks({api,failedApi,auth}) {
  const user=await signUp(auth,'recover');
  assert.equal((await request(failedApi+'/session',user.idToken)).status,503,'Auth creado pero Firestore no disponible');
  const session=await request(api+'/session',user.idToken); assert.equal(session.status,200);
  assert.equal((await session.json()).status,'PENDING');
  const retry=await request(api+'/me/complete',user.idToken,'POST',draft(`recover_${stamp}`));
  assert.equal(retry.status,200);
  console.log('PASS recuperación: Auth creado sin Firestore, bootstrap y finalización reintentables sin duplicar cuenta');
}

export async function runAccountSecurityChecks({api,auth}) {
  const disabled=await signUp(auth,'disabled');
  const update=await request(`${auth}/identitytoolkit.googleapis.com/v1/accounts:update?key=local`,'owner','POST',
    {localId:disabled.localId,disableUser:true});
  assert.equal(update.status,200);
  assert.equal((await request(api+'/session',disabled.idToken)).status,401,'Cuenta deshabilitada en Firebase');
  const deleted=await signUp(auth,'deleted');
  const remove=await request(`${auth}/identitytoolkit.googleapis.com/v1/accounts:delete?key=local`,'owner','POST',{localId:deleted.localId});
  assert.equal(remove.status,200);
  assert.equal((await request(api+'/session',deleted.idToken)).status,401,'Cuenta borrada en Firebase');
  const revoked=await signUp(auth,'revoked');
  const revoke=await request(`${auth}/identitytoolkit.googleapis.com/v1/accounts:update?key=local`,'owner','POST',
    {localId:revoked.localId,validSince:String(Math.floor(Date.now()/1000)+2)});
  assert.equal(revoke.status,200);
  assert.equal((await request(api+'/session',revoked.idToken)).status,401,'Revocación Firebase');
  console.log('PASS seguridad: cuenta Firebase deshabilitada, eliminada y tokens revocados rechazados');
}

export async function runEmailPolicyChecks({api,auth}) {
  const user=await signUp(auth,'domain');
  assert.equal((await request(api+'/session',user.idToken)).status,200);
  const response=await request(api+'/me/complete',user.idToken,'POST',draft(`domain_${stamp}`));
  assert.equal(response.status,403);
  assert.equal((await response.json()).code,'EMAIL_DOMAIN_NOT_ALLOWED');
  assert.equal((await (await request(api+'/session',user.idToken)).json()).status,'PENDING');
  console.log('PASS política configurable: dominio fuera de lista bloqueado antes de completar perfil');
}
