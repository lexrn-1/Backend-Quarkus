# app_meet — Backend de sprints 0 y 1

Dos microservicios Java 21 / Quarkus 3.40.1, con arquitectura hexagonal y Maven Wrapper 3.9.16:

| Servicio | Paquete | HTTP local | Responsabilidad implementada |
|---|---|---|---|
| backend-api | com.example.api | 8080 | Sesiones Firebase, perfiles, username único y demo Firestore |
| backend-realtime | com.example.realtime | 8081 | Socket.IO 9092 con sesión válida y perfil completo |

La prueba técnica de señalización está habilitada únicamente en el perfil local con emuladores. El sprint 1 incluye el soporte backend para US-01/02/03. El registro, Google y login se realizan con Firebase Auth; la API valida sus ID tokens, completa perfiles, reserva usernames y protege el acceso. No hay edición/borrado de perfiles, salas, chat, Angular ni WebRTC funcional. La publicación y el cierre end-to-end siguen pendientes.

## Autenticación y acceso (Sprint 1)

1. Registrar o iniciar sesión en Firebase Auth mediante Email/Password o Google.
2. Enviar su ID token como `Authorization: Bearer <token>` a `GET /api/v1/session`.
3. Si devuelve `COMPLETE_PROFILE`, enviar nombres, apellidos, username y avatar a `POST /api/v1/me/complete`.
4. Un perfil completo puede acceder a `GET /api/v1/me` y a Socket.IO. Un perfil pendiente recibe 403 en rutas privadas.
5. Para salir: `POST /api/v1/session/logout` y `signOut()` del SDK Firebase en el cliente.

[Contratos para el frontend](docs/integracion-cliente.md), [guía de identidad con ejemplos](docs/identidad.md) y [contratos HTTP](docs/contratos.md). Swagger permite pegar un ID token mediante **Authorize**. El backend no recibe contraseñas ni emite tokens paralelos.

La lista de dominios institucionales quedó pendiente por decisión del usuario. `APP_ALLOWED_EMAIL_DOMAINS` permite configurarla; vacía admite cualquier correo válido de Firebase. Activar Email/Password y Google en Firebase Console y definir allí la política de contraseñas antes de integración pública.

## Requisitos y compilación

JDK 21, acceso a Maven Central. Para la integración: Node 22 o superior compatible con Firebase CLI y acceso a Internet para descargar emuladores. Para contenedores: Docker Engine y Compose v2 con permisos sobre el daemon.

```bash
./mvnw clean verify
```

En Windows usar `.\mvnw.cmd`. Las pruebas Maven no necesitan credenciales ni emuladores.

## Prueba automática local con emuladores

```bash
cd tests/integration
npm ci
npm run verify
```

El comando inicia emuladores Auth/Firestore, ejecuta los JAR construidos y cierra todos los procesos. Usa los puertos 9099, 8085, 18080–18085 y 19092; los emuladores usan también 4400, 4500 y 9150. Crea datos efímeros en el proyecto `demo-app-meet`. No apuntar esta prueba a recursos reales.

## Desarrollo manual

En una terminal:

```bash
cd tests/integration
npm ci
npm run emulators
```

En otras dos terminales, desde la raíz:

```bash
QUARKUS_PROFILE=local GOOGLE_CLOUD_PROJECT=demo-app-meet FIRESTORE_EMULATOR_HOST=127.0.0.1:8085 FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 ./mvnw -pl backend-api quarkus:dev
```

```bash
QUARKUS_PROFILE=local GOOGLE_CLOUD_PROJECT=demo-app-meet FIRESTORE_EMULATOR_HOST=127.0.0.1:8085 FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 APP_PROOF_SESSION=local-proof ./mvnw -pl backend-realtime quarkus:dev -Ddebug=5006
```

Crear el documento público de prueba (solo en emulador):

```bash
curl --fail -X PATCH 'http://127.0.0.1:8085/v1/projects/demo-app-meet/databases/(default)/documents/demo/sprint0' -H 'Authorization: Bearer owner' -H 'Content-Type: application/json' --data '{"fields":{"message":{"stringValue":"Hola desde Firestore"}}}'
curl --fail http://localhost:8080/api/v1/demo
```

Para ejecutar los JAR usar las mismas variables con `java -jar backend-api/target/quarkus-app/quarkus-run.jar` o `java -jar backend-realtime/target/quarkus-app/quarkus-run.jar`.

| Endpoint | API | Realtime |
|---|---|---|
| Liveness | http://localhost:8080/q/health/live | http://localhost:8081/q/health/live |
| Readiness | http://localhost:8080/q/health/ready | http://localhost:8081/q/health/ready |
| Swagger | http://localhost:8080/q/swagger-ui/ | http://localhost:8081/q/swagger-ui/ |
| OpenAPI | http://localhost:8080/q/openapi | http://localhost:8081/q/openapi |

Readiness requiere Firestore accesible; realtime requiere además listener activo. Liveness no consulta Firebase.

## Contenedores

```bash
docker compose --profile emulators up --build
# Al terminar:
docker compose --profile emulators down
```

Los emuladores son efímeros. Los backends pueden iniciar antes que Firestore; readiness permanece DOWN hasta que esté disponible. Los puertos locales se enlazan únicamente a loopback.

Producción usa [compose.dokploy.yaml](compose.dokploy.yaml), sin publicar puertos del host. Leer [Dokploy](docs/dokploy.md) antes de configurar recursos reales. No incluye credenciales y no se ha desplegado remotamente.

## Documentación

[Índice](docs/README.md), [arquitectura](docs/arquitectura.md), [Firebase](docs/firebase.md), [contratos](docs/contratos.md), [pruebas](docs/pruebas.md) y [estado del sprint 0](docs/sprint-0.md) y [estado del sprint 1](docs/sprint-1.md).
