# backend-realtime

Aplicación independiente dentro del agregador Maven del repositorio. Consultar [arranque general](../README.md), [configuración](../docs/microservicios.md) y [contratos implementados](../docs/contratos.md).

Desde la raíz: `./mvnw -pl backend-realtime clean verify`. JAR: `java -jar backend-realtime/target/quarkus-app/quarkus-run.jar`; configurar Firebase según la [guía](../docs/firebase.md). Sin acceso a Firestore, readiness queda DOWN.

Dockerfile requiere contexto raíz: `docker build -f backend-realtime/Dockerfile -t backend-realtime:local .`. La imagen usa HTTP 8080; ver [Dokploy](../docs/dokploy.md) para configuración productiva.
