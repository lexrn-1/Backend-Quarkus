# app_meet — Sprint 0 backend

Dos microservicios Java 21 / Quarkus 3.40.1, con arquitectura hexagonal y Maven Wrapper 3.9.16:

| Servicio | Paquete | HTTP local | Responsabilidad implementada |
|---|---|---|---|
| backend-api | com.example.api | 8080 | Lectura pública de demostración de Firestore |
| backend-realtime | com.example.realtime | 8081 | Listener Socket.IO 9092 autenticado con Firebase |

La prueba técnica de señalización está habilitada únicamente en el perfil local con emuladores. No hay CRUD de perfiles/salas, chat, Angular ni WebRTC funcional. La publicación y el cierre end-to-end del sprint siguen pendientes.

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

El comando inicia emuladores Auth/Firestore, ejecuta los JAR construidos y cierra todos los procesos. Usa los puertos 9099, 8085, 18080–18082 y 19092; los emuladores usan también 4400, 4500 y 9150. Crea datos efímeros en el proyecto `demo-app-meet`. No apuntar esta prueba a recursos reales.

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

[Índice](docs/README.md), [arquitectura](docs/arquitectura.md), [Firebase](docs/firebase.md), [contratos](docs/contratos.md), [pruebas](docs/pruebas.md) y [estado del sprint](docs/sprint-0.md).
