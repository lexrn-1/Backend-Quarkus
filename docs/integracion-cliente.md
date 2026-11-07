# Contratos para el frontend

Frontend publicado: `https://frontln.vercel.app`. Orígenes CORS predeterminados en REST y Socket.IO:

- `https://frontln.vercel.app`
- `http://localhost:4200`
- `http://127.0.0.1:4200`

Los orígenes incluyen esquema y puerto, sin ruta ni barra final. Si el frontend local usa otro puerto, añadir su origen exacto a `APP_ALLOWED_ORIGINS`. Una variable existente reemplaza la lista completa. Compose admite también `FRONTEND_ORIGIN` como alternativa; `APP_ALLOWED_ORIGINS` tiene prioridad.

## Direcciones

| Uso | Desarrollo local | Producción |
|---|---|---|
| API base | `http://localhost:8080/api/v1` | URL HTTPS del backend por publicar |
| Socket.IO | `http://localhost:9092` | URL HTTPS/WSS de realtime por publicar |
| Swagger API | `http://localhost:8080/q/swagger-ui/` | URL pública del backend + `/q/swagger-ui/` |
| REST técnico realtime | `http://localhost:8081` | URL pública HTTP de realtime por publicar |

La URL Vercel corresponde al frontend. Hace falta publicar los backends y configurar sus URLs HTTPS en el cliente para comunicar la aplicación pública con ellos.

## Autenticación

El registro Email/Password, login y Google se hacen con Firebase SDK del cliente. Después se obtiene `user.getIdToken()` y se envía:

```http
Authorization: Bearer <Firebase ID token>
```

Para cuerpos JSON añadir `Content-Type: application/json`. No enviar contraseñas a Quarkus. La API deriva uid y email del token; no incluirlos en el formulario de perfil.

| Método | Ruta relativa a API base | Acceso | Contrato |
|---|---|---|---|
| GET | `/demo` | Público | 200 `{message}`; 404 si no existe el documento demo |
| GET | `/session` | Sesión válida | 200: estado y siguiente paso del usuario |
| POST | `/me/complete` | Sesión válida | 200: perfil completo; reserva username en transacción |
| GET | `/me` | Perfil completo | 200: perfil propio; 403 si está pendiente |
| POST | `/session/logout` | Sesión válida | 204: invalida el token actual; cliente ejecuta también Firebase signOut |

`GET /session` devuelve:

```typescript
type SessionResponse = {
  uid: string;
  email: string;
  displayName: string | null;
  photoUrl: string | null;
  provider: 'password' | 'google.com';
  status: 'PENDING' | 'COMPLETE';
  profileComplete: boolean;
  nextAction: 'COMPLETE_PROFILE' | 'ENTER';
};
```

Si `nextAction` es `COMPLETE_PROFILE`, mostrar el formulario obligatorio y no entrar al dashboard. Si es `ENTER`, continuar a la ruta solicitada.

Entrada de `POST /me/complete`:

```json
{
  "firstName": "Ana",
  "lastName": "Pérez",
  "username": "ana_perez",
  "avatar": "avatar-1"
}
```

Respuesta de finalización y de `GET /me`: `uid`, `email`, `firstName`, `lastName`, `username`, `avatar`, `status`, `createdAt`, `updatedAt`. Las fechas son ISO-8601 y status es COMPLETE. Reintentar la misma finalización es idempotente. Si el username está ocupado, conservar la cuenta Firebase y reenviar el formulario con otro nombre.

## Ejemplo de llamada

```typescript
const API_BASE = 'http://localhost:8080/api/v1'; // Cambiar por backend HTTPS al publicar.
const token = await auth.currentUser.getIdToken();
const response = await fetch(`${API_BASE}/session`, {
  headers: { Authorization: `Bearer ${token}` },
  cache: 'no-store'
});
if (!response.ok) throw await response.json();
const session = await response.json();
```

Los detalles de registro/login/Google/logout y validaciones están en [identidad](identidad.md). Los errores de API se documentan en [contratos](contratos.md): 400 datos, 401 sesión, 403 acceso/perfil pendiente, 409 username/conflicto y 503 dependencia. Las respuestas propias usan `{code,message}` y pueden incluir campos de validación; un JSON malformado puede devolver el contrato estándar de Quarkus.

## Socket.IO

Usar `socket.io-client` 4.x con `path: '/socket.io/'` y `auth: {token: await user.getIdToken()}`. El backend exige perfil COMPLETE, token válido y un origen permitido. Actualizar el token antes de reconectar.

Solo en laboratorio local, añadir `proofSession` autorizado por `APP_PROOF_SESSION`. Los eventos `proof:ping` y `proof:signal` son pruebas técnicas; no están habilitados en producción. Todavía no existen contratos implementados de salas/chat/audio/video; consultar [contratos](contratos.md) para distinguir los eventos futuros.
