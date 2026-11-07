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

## Propuesto para siguientes sprints: no disponible

REST de API: completar perfil, GET/PATCH/DELETE `/api/v1/me`, GET/POST `/api/v1/rooms`, PATCH/DELETE `/api/v1/rooms/{id}`, POST `/api/v1/rooms/{id}/join` y GET `/api/v1/rtc/config`. Realtime REST: GET `/api/v1/rooms/{id}/messages` con cursor estable.

Eventos futuros: `room:join`, `room:leave`, `room:snapshot`, `room:deleted`, `participant:joined`, `participant:left`, `chat:send`, `chat:message`, `media:update` y `rtc:signal`. Envoltura propuesta `{v,eventId,roomId,payload}` y ack `{ok,data,error}`. Se exigirán sesión, perfil y membresía según historia; no se aceptarán roles o uid impuestos por el navegador. Login y Google pertenecen a Firebase Auth, sin emisor paralelo.

Errores previstos: 400 validación, 401 sesión, 403 permisos, 404 recurso, 409 conflicto y 503 dependencia. Estos contratos futuros no forman parte del OpenAPI implementado.
