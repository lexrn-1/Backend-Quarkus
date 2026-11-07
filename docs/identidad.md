# Identidad — Sprint 1 (US-01, US-02 y US-03)

El backend integra Firebase Authentication y Firestore. El cliente crea/inicia sesiones con Firebase; Quarkus verifica su ID token con Firebase Admin y comprueba revocación, cuenta habilitada y estado del perfil. No hay endpoints propios de contraseñas ni otro emisor JWT.

## Flujo manual US-01

1. Validar nombres, apellidos, avatar, correo y política de contraseña visible en el cliente.
2. Ejecutar `createUserWithEmailAndPassword(auth,email,password)` con Firebase SDK.
3. Obtener `user.getIdToken()` y consultar `GET /api/v1/session`.
4. Enviar `POST /api/v1/me/complete` con `firstName`, `lastName`, `username`, `avatar`.
5. Continuar al dashboard únicamente tras confirmar el perfil completo desde API.

La API deriva uid y email exclusivamente del token. No guarda contraseñas ni tokens originales. Las cuentas existentes sin perfil son pendientes recuperables. Si el username colisiona, mantener la misma cuenta Auth y repetir solo la finalización con otro username; no volver a crear la cuenta.

## Google US-02

Usar `signInWithPopup(auth,new GoogleAuthProvider())` (o redirect). El backend recibe el ID token Firebase resultante, no el access token Google. `GET /session` devuelve los datos disponibles de Google para prellenado y el siguiente paso:

- Primera entrada sin perfil: `PENDING`, `profileComplete:false`, `nextAction:"COMPLETE_PROFILE"`.
- Entrada posterior con perfil completo: `COMPLETE`, `profileComplete:true`, `nextAction:"ENTER"`.

El usuario debe confirmar nombres/apellidos y avatar y elegir un username disponible. Un perfil incompleto no obtiene acceso a `/me`, otras rutas de negocio ni Socket.IO. Una colisión devuelve 409 y no reserva parcialmente el nombre.

Cerrar/cancelar la ventana Google y mostrar un mensaje accesible corresponde al SDK y a Angular. No se implementaron pantallas ni se declara verificada esa interacción.

## Login y logout US-03

Usar `signInWithEmailAndPassword` o Google y luego consultar `/session`. El cliente debe mostrar «Correo o contraseña incorrectos» tanto para correo inexistente como para contraseña incorrecta, sin revelar qué campo falló. Los errores de registro, como correo ya existente, se presentan junto al campo según la historia.

Para logout, llamar `POST /api/v1/session/logout` con el ID token y ejecutar `signOut(auth)`. API invalida inmediatamente ese token mediante su hash SHA-256 en Firestore. Otras sesiones permanecen válidas. No se revocan todos los refresh tokens de la cuenta: el SDK debe borrar la sesión local y no conservar un refresh token por fuera de Firebase. El endpoint no recibe ni devuelve refresh tokens.

Si el backend está caído, el cliente debe limpiar igualmente su sesión y avisar que no pudo confirmar la invalidación servidor. No afirmar revocación remota si la petición falló. Las respuestas privadas llevan no-store; el frontend debe revalidar sesión al navegar, incluyendo volver atrás, sin confiar en un indicador local de perfil completo.

## Validación acordada para esta implementación

| Dato | Regla |
|---|---|
| Nombres y apellidos | Obligatorios, sin controles, recortados, máximo 80 caracteres cada uno |
| Username | NFKC + trim; 3–20 letras ASCII/números/guion bajo |
| Unicidad | Minúsculas Locale.ROOT; `Ana_01` y `ana_01` compiten por la misma reserva |
| Avatar | `avatar-1` a `avatar-8`, o URL HTTPS hasta 2048 caracteres, sin credenciales ni fragmento |
| Correo | Viene de Firebase, formato válido; dominio exacto si hay lista configurada |
| Contraseña | Administrada por Firebase Auth; política del proyecto y presentación en cliente |

El backend no descarga la imagen del avatar ni ofrece almacenamiento de imágenes. La lista institucional quedó explícitamente pendiente: `APP_ALLOWED_EMAIL_DOMAINS=universidad.edu.co,otra.edu.co`; vacía permite cualquier correo válido autenticado. Los subdominios deben declararse expresamente. No se afirma validación institucional hasta definir esta lista.

## Consistencia y recuperación

La finalización ejecuta una transacción Firestore que lee perfil y reserva antes de escribir `users/{uid}` y `usernames/{usernameNormalized}`. Bajo concurrencia solo un uid gana. Un reintento idéntico del ganador devuelve el mismo perfil; uno distinto no modifica un perfil ya completado. No hay reservas previas fuera de esa transacción.

Auth y Firestore no tienen transacción global. Si Auth se creó y Firestore falla o el username está ocupado, la cuenta sigue autenticable y `GET /session` reconoce el estado pendiente. Reintentar después con el mismo uid. La política inicial conserva cuentas pendientes para recuperación, sin borrado automático. Cualquier limpieza administrativa debe hacerse de forma explícita tras revisar la cuenta; no hay tarea destructiva programada en esta entrega.

Un timeout de la transacción puede dejar resultado desconocido: volver a consultar sesión y reintentar con el mismo formulario. No intentar compensar eliminando la cuenta Auth ni liberando usernames a ciegas.

## Ejemplo de integración SDK (para Angular)

```javascript
import {createUserWithEmailAndPassword, signInWithEmailAndPassword,
  GoogleAuthProvider, signInWithPopup, signOut} from 'firebase/auth';

async function api(path, method = 'GET', body) {
  const token = await auth.currentUser.getIdToken();
  const response = await fetch(API_URL + '/api/v1' + path, {
    method,
    headers: {Authorization: `Bearer ${token}`, ...(body ? {'Content-Type':'application/json'} : {})},
    body: body ? JSON.stringify(body) : undefined,
    cache: 'no-store'
  });
  if (!response.ok) throw {status: response.status, detail: await response.json()};
  return response.status === 204 ? undefined : response.json();
}

// Registro: Firebase crea la identidad, API completa el perfil.
await createUserWithEmailAndPassword(auth, email, password);
await api('/me/complete', 'POST', {firstName, lastName, username, avatar});

// Login manual o Google: el backend indica la ruta siguiente.
await signInWithEmailAndPassword(auth, email, password);
// Alternativa: await signInWithPopup(auth, new GoogleAuthProvider());
const session = await api('/session');
// COMPLETE_PROFILE → formulario obligatorio; ENTER → ruta solicitada/dashboard.

try { await api('/session/logout', 'POST'); }
finally { await signOut(auth); }
```

`auth` debe estar inicializado con la configuración pública Firebase del frontend. Capturar errores del SDK y de API, conservar el formulario y mostrar mensajes accesibles; el ejemplo no constituye una pantalla implementada.

## Configuración externa y límites

Activar Email/Password y Google en Firebase Console, registrar dominios autorizados y decidir la política de contraseña. La cuenta administrativa backend no reemplaza esos ajustes. No se modificó la configuración remota ni se crearon usuarios reales en esta entrega; las pruebas son con emuladores.

`revokedTokens/{sha256}` solo guarda expiresAt, sin token original. API y realtime consultan su existencia antes de permitir acceso. Realtime comprueba también cuenta habilitada y revocación Firebase antes de cada evento técnico. Configurar TTL Firestore sobre expiresAt para limpieza automática si se desea; no se ha desplegado esa política y no es requisito para rechazar tokens. El borrado de cuentas/perfiles y la edición pertenecen a Sprint 2.

Referencias: [verificar tokens](https://firebase.google.com/docs/auth/admin/verify-id-tokens), [sesiones Firebase](https://firebase.google.com/docs/auth/admin/manage-sessions), [transacciones](https://firebase.google.com/docs/firestore/manage-data/transactions), [emulador Auth](https://firebase.google.com/docs/emulator-suite/connect_auth).
