# Contratos implementados y propuestos

## Implementado: API

`GET /api/v1/demo` es público, sin escritura ni autenticación. Respuesta 200: `{"message":"Hola desde Firestore"}`. Errores JSON:

| HTTP | code | Significado |
|---|---|---|
| 404 | DEMO_NOT_FOUND | Documento de demostración ausente |
| 503 | DEPENDENCY_UNAVAILABLE | Credenciales, Firestore o dato no disponible |

El cuerpo de error contiene `code` y `message`, sin stacktrace ni datos de Firebase. Swagger/OpenAPI describen el endpoint. Ambos servicios exponen `/q/health/live`, `/q/health/ready`, `/q/health`, `/q/openapi` y `/q/swagger-ui/`.

## Implementado: prueba técnica Socket.IO

Engine.IO 4 / Socket.IO v4 en `/socket.io/`, puerto 9092. Origen debe estar autorizado. Enviar `auth: {token: firebaseIdToken, proofSession: "local-proof"}`. El token se verifica con Firebase Admin. La sesión debe figurar en `APP_PROOF_SESSION`.

Los eventos siguientes solo existen con prueba habilitada en local/emuladores. Las sesiones son canales de laboratorio elegidos explícitamente por los participantes, no membresías ni autorización de salas. Nunca habilitarlos para datos reales.

| Evento | Entrada | Resultado |
|---|---|---|
| proof:ping | `{}` | ack `{ok:true,data:{connectionId}}` |
| proof:signal | `{toConnectionId,signal:{...}}` | ack `{ok:true}` y evento al destino |
| proof:signal recibido | — | `{fromConnectionId,signal}` |

El remitente se deriva de la conexión. No puede señalar destinos fuera de su sesión. Acks de error: `{ok:false,error:"FORBIDDEN"}` o `INVALID_SIGNAL`. Conexiones inválidas reciben `connect_error`; eventos de conexiones no autenticadas o expiradas provocan desconexión. Los payloads están acotados a 16 KiB. No se persisten señales ni se transportan medios.

## Implementado: identidad Sprint 1

Todos estos endpoints requieren `Authorization: Bearer <Firebase ID token>`. No se aceptan uid, email, roles ni contraseñas en el cuerpo. Los permisos se resuelven desde Firebase y Firestore en cada solicitud.

| Método | Ruta | Perfil pendiente | Resultado |
|---|---|---|---|
| GET | `/api/v1/session` | Permitido | uid, email, displayName, photoUrl, provider, status, profileComplete, nextAction |
| POST | `/api/v1/me/complete` | Permitido | Perfil completo y username reservado atómicamente |
| GET | `/api/v1/me` | 403 | Perfil propio completo |
| POST | `/api/v1/session/logout` | Permitido | 204; invalida inmediatamente el ID token actual |

`GET /session` es de solo lectura: si Auth existe sin documento de perfil devuelve `PENDING` y `COMPLETE_PROFILE`. Al completar devuelve `COMPLETE` y `ENTER`. El perfil persistido se crea en la transacción de finalización.

Entrada de `/me/complete`:

```json
{"firstName":"Ana","lastName":"Pérez","username":"ana_perez","avatar":"avatar-1"}
```

Respuesta: `uid`, `email`, `firstName`, `lastName`, `username`, `avatar`, `status`, `createdAt`, `updatedAt`. Identidad y email vienen del token; los timestamps provienen del servidor Firestore. Un reintento semánticamente idéntico devuelve el mismo perfil sin modificar fechas. Cambiar un perfil ya completado devuelve 409: la edición se implementará en Sprint 2.

| HTTP | code | Situación |
|---|---|---|
| 400 | INVALID_PROFILE | Campos inválidos; validación de nombres, username o avatar |
| 400 | error JSON de Quarkus | Cuerpo malformado o campos desconocidos, como uid/email |
| 401 | UNAUTHORIZED | Sin token, token inválido/expirado, cuenta Firebase eliminada/deshabilitada o token revocado |
| 403 | PROFILE_INCOMPLETE | Intento de entrar a recurso privado antes de completar perfil |
| 403 | EMAIL_DOMAIN_NOT_ALLOWED | Correo fuera de la lista institucional configurada |
| 403 | ACCOUNT_UNAVAILABLE / FORBIDDEN | Perfil bloqueado o en eliminación |
| 409 | USERNAME_TAKEN | Colisión de username normalizado |
| 409 | PROFILE_ALREADY_COMPLETE | Intento de cambiar los datos mediante finalización |
| 503 | DEPENDENCY_UNAVAILABLE | Firebase/Firestore no disponible; operación reintentable |

Las respuestas de identidad llevan `Cache-Control: no-store`. Las rutas `/api/v1/*` quedan privadas por defecto; demo es pública y las rutas de bootstrap/logout requieren sesión sin exigir perfil completo. `OPTIONS` se atiende mediante CORS.

Socket.IO exige también perfil COMPLETE y comprueba revocación/estado antes de cada evento técnico. Su auth sigue siendo `{token,proofSession}` en laboratorio. No se aceptan perfiles pendientes aunque el ID token sea válido.

## Propuesto para siguientes sprints: no disponible

REST de API: PATCH/DELETE `/api/v1/me`, GET/POST `/api/v1/rooms`, PATCH/DELETE `/api/v1/rooms/{id}`, POST `/api/v1/rooms/{id}/join` y GET `/api/v1/rtc/config`. Realtime REST: GET `/api/v1/rooms/{id}/messages` con cursor estable.

Eventos futuros: `room:join`, `room:leave`, `room:snapshot`, `room:deleted`, `participant:joined`, `participant:left`, `chat:send`, `chat:message`, `media:update` y `rtc:signal`. Envoltura propuesta `{v,eventId,roomId,payload}` y ack `{ok,data,error}`. Se exigirán sesión, perfil y membresía según historia; no se aceptarán roles o uid impuestos por el navegador. Login y Google pertenecen a Firebase Auth, sin emisor paralelo.

Errores previstos: 400 validación, 401 sesión, 403 permisos, 404 recurso, 409 conflicto y 503 dependencia. Estos contratos futuros no forman parte del OpenAPI implementado.
