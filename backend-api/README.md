# backend-api

Aplicación independiente dentro del agregador Maven del repositorio. Consultar [arranque general](../README.md), [configuración](../docs/microservicios.md) y [contratos implementados](../docs/contratos.md).

Desde la raíz: `./mvnw -pl backend-api clean verify`. JAR: `java -jar backend-api/target/quarkus-app/quarkus-run.jar`; configurar Firebase según la [guía](../docs/firebase.md). Sin acceso a Firestore, readiness queda DOWN.

Dockerfile requiere contexto raíz: `docker build -f backend-api/Dockerfile -t backend-api:local .`. La imagen usa HTTP 8080; ver [Dokploy](../docs/dokploy.md) para configuración productiva.
