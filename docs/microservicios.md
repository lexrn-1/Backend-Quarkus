# Microservicios y configuración

Ambos hijos heredan únicamente versiones/build del padre. Cada uno declara sus extensiones, tiene Dockerfile propio y genera `target/quarkus-app/`. Para distribuirlo se necesita toda esa carpeta.

| Configuración | Valor inicial |
|---|---|
| `HTTP_PORT` | API 8080; realtime 8081; imágenes 8080 |
| `GOOGLE_CLOUD_PROJECT` | demo-app-meet; configurar proyecto real en producción |
| `GOOGLE_APPLICATION_CREDENTIALS` | Archivo externo de cuenta de servicio, producción |
| `APP_ALLOWED_ORIGINS` | http://localhost:4200; lista separada por comas |
| `APP_DEMO_DOCUMENT` | demo/sprint0, documento con campo string message |
| `APP_SOCKET_PORT` | 9092 |
| `APP_ALLOWED_EMAIL_DOMAINS` | Vacía; restricción institucional pendiente; lista separada por comas |
| `APP_PROOF_SESSION` | Lista de identificadores de sesiones técnicas locales; vacía por defecto |
| `QUARKUS_PROFILE` | prod por defecto; local para emuladores |
| `FIRESTORE_EMULATOR_HOST` | host:puerto sin esquema; solo local/test |
| `FIREBASE_AUTH_EMULATOR_HOST` | host:puerto sin esquema; solo local/test |

El modo local exige ambos emuladores. Producción rechaza sus variables y el modo de prueba realtime. En producción el listener solo autentica conexiones: no registra eventos `proof:*` ni funcionalidades futuras. Los orígenes se comprueban tanto en REST como en el handshake Socket.IO; los clientes técnicos deben enviar Origin explícito.

## Matriz fijada

| Componente | Versión |
|---|---|
| Java | 21 |
| Quarkus BOM | 3.40.1 |
| Maven Wrapper / Maven | 3.3.4 / 3.9.16 |
| Firebase Admin Java | 9.11.0 |
| netty-socketio | 2.0.13 |
| Netty (resuelto por BOM) | 4.1.138.Final |
| socket.io-client | 4.8.4 |
| Firebase CLI | 15.35.0 |
| ArchUnit | 1.4.1 |

Las dependencias Netty quedan mediadas por el BOM Quarkus. Usar `./mvnw -pl backend-realtime dependency:tree -Dincludes=io.netty` para consultar la resolución exacta. La compatibilidad local no demuestra funcionamiento por Traefik/WSS ni transmisión WebRTC.

Referencias: [Firebase Admin](https://firebase.google.com/docs/admin/setup), [netty-socketio](https://github.com/mrniko/netty-socketio), [protocolo Socket.IO](https://socket.io/docs/v4/socket-io-protocol/).

Sprint 1: los ID tokens password/Google se verifican con Firebase Admin y revocación habilitada. API consulta perfil y logout; realtime exige COMPLETE y revisa estado/revocación antes de eventos. Ver [identidad](identidad.md).
