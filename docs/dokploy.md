# Preparación Dokploy

No se ha accedido a un VPS ni publicado imágenes. Los nombres siguientes son ilustrativos.

1. Ejecutar CI y habilitar GHCR en el repositorio. El workflow publica automáticamente ambas imágenes en GHCR después de verificar Maven, integración y contenedores, en cada push a `master` o ejecución manual sobre `master`. Publica `latest` y el SHA del commit, usando `GITHUB_TOKEN` con `packages: write`. Los pull requests y ejecuciones manuales en otras ramas solo construyen, sin publicar. Se cancelan ejecuciones anteriores de la misma rama para evitar que una versión antigua sobrescriba `latest`.
2. Crear recurso Compose en Dokploy usando `compose.dokploy.yaml`. Configurar `API_IMAGE`, `REALTIME_IMAGE`, `FIREBASE_PROJECT_ID`, `APP_ALLOWED_ORIGINS` (lista de orígenes autorizados; `FRONTEND_ORIGIN` es alternativa compatible) y `DOKPLOY_NETWORK` con valores reales.
3. Crear archivos administrativos externos al repo y configurar `API_FIREBASE_CREDENTIALS_FILE` y `REALTIME_FIREBASE_CREDENTIALS_FILE` como rutas absolutas del VPS. Montajes de solo lectura; permitir lectura a UID 10001. No incluir secretos en variables del frontend, imágenes ni capturas.
4. Configurar dominios Dokploy: `api.ejemplo.com` → backend-api:8080; `rt.ejemplo.com` → backend-realtime:9092; `rt-api.ejemplo.com` → backend-realtime:8080. Adjuntar la red externa del proxy. Compose no publica puertos al host.
5. Activar certificados HTTPS. Probar polling y Upgrade WebSocket en `/socket.io/`, con Origin Angular autorizado e ID token Firebase real. El modo técnico `proof:*` queda desactivado en producción.
6. Comprobar readiness, demo Firestore y Swagger. Los Dockerfile incluyen curl, usuario no root y healthcheck de readiness. Medir CPU/RAM antes de fijar límites; mantener una réplica realtime hasta coordinar presencia/broadcasts.

La CI aún no se ha ejecutado en GitHub. La sintaxis de Compose puede verificarse sin daemon con `docker compose --profile emulators config --quiet`; el archivo de producción requiere sus variables definidas para validarse.

## Recuperación

Antes de actualizar, registrar SHA/digest de ambas imágenes y estado del dato demo. Si fallan smoke tests, volver a las etiquetas anteriores, redesplegar y comprobar health/REST/handshake. Sprint 0 no migra datos. No confundir rollback de imágenes con restauración de Firestore; configurar respaldo y ensayar restauración antes de introducir datos reales.

Pendientes de aceptación pública: Firebase real, imagen construida en Docker, dominios/certificados, proxy WSS/reconexión, Angular publicado y prueba simple-peer con ICE/STUN/TURN en dos redes. HTTPS/WSS no transporta medios ni sustituye TURN.

Referencias: [dominios Compose Dokploy](https://docs.dokploy.com/docs/core/docker-compose/domains).

## Fallo al descargar Maven durante el build

Si el log muestra `wget: Failed to fetch ... apache-maven-3.9.16-bin.tar.gz`, el fallo ocurre al descargar Maven, antes de compilar Java. Ambos Dockerfile usan ahora la imagen oficial `maven:3.9.16-eclipse-temurin-21`, que ya incluye Maven y Java 21. El Wrapper se mantiene para desarrollo y CI fuera del contenedor. No es necesario cambiar versiones ni desactivar la validación TLS.

La construcción sigue necesitando acceso a Maven Central para resolver dependencias. Si después falla una descarga de dependencias, revisar el error HTTP, DNS, certificados o proxy del servidor; este cambio elimina únicamente la descarga inicial de la distribución por el Wrapper.

Construir desde la raíz de **backend**, con contexto `.`. En Dokploy comprobar que el código desplegado contiene los Dockerfile actualizados y volver a desplegar. Para comprobar manualmente con Buildx:

```bash
docker buildx build --load --progress=plain -f backend-api/Dockerfile -t backend-api:local .
docker buildx build --load --progress=plain -f backend-realtime/Dockerfile -t backend-realtime:local .
```

`WARNING: No output specified with docker-container driver` es independiente del fallo de descarga: `--load` conserva la imagen en el Docker Engine para uso local; `--push` publica en un registro cuando se haya configurado/autorizado esa publicación. La CI carga las imágenes cuando no se ha solicitado publicación. No se ha publicado ni desplegado este ajuste desde el entorno de trabajo.

Referencias: [imagen oficial Maven](https://hub.docker.com/_/maven), [salidas de Buildx](https://docs.docker.com/build/exporters/).

## Publicar esta extensión: salas/chat

No se ha desplegado esta entrega. Construir y desplegar nuevas imágenes de ambos servicios y actualizar frontend de forma coordinada. Antes, crear los índices de `firestore.indexes.json` y configurar credenciales e IAM externos. Una sola réplica realtime. Para el dominio compartido puede combinarse `compose.dokploy.yaml` con `compose.gateway.yaml`; ajustar nombre de red y certresolver/entrypoint a la instalación real de Traefik. Quitar las dos rutas `/` duplicadas de Dokploy UI si se usan las labels del overlay.

| Prioridad | Regla del mismo host | Destino |
|---|---|---|
| 300 | `/api/v1/rooms/{uuid}/messages` exacta | realtime HTTP 8080 |
| 200 | prefijo `/socket.io/` | realtime 9092, polling y upgrade WebSocket |
| 100 | resto | API HTTP 8080 |

No dirigir todo `/api/v1/rooms/` a realtime: creación/unión/edición pertenecen a API. No usar readiness como URL base. Angular usa `https://api.5-253-232-179.sslip.io/api/v1` y Socket.IO el mismo origen sin `/api/v1`.

TURN_USERNAME/TURN_PASSWORD deben configurarse externamente; la aplicación omite marcadores xxxxxxxx. Después de publicar comprobar CORS desde Vercel, Auth real, history 401 sin token, polling/upgrade y seis conexiones. La entrega local no sustituye esas pruebas ni la llamada entre redes con TURN real. Health de realtime verifica Firestore/listener, no conectividad de medios P2P.

## Usar latest en el entorno de Dokploy

```dotenv
API_IMAGE=ghcr.io/lexrn-1/backend-quarkus-backend-api:latest
REALTIME_IMAGE=ghcr.io/lexrn-1/backend-quarkus-backend-realtime:latest
```

Compose tiene `pull_policy: always` en ambos servicios, para consultar el registro al desplegar. Esperar a que finalice correctamente el workflow de `master` y volver a desplegar en Dokploy; publicar una etiqueta no actualiza por sí solo los contenedores que ya están ejecutándose. Si los paquetes son privados, configurar acceso de lectura a GHCR en Dokploy. Para rollback, sustituir latest por el SHA anterior conservado en GHCR.

La publicación automática empieza cuando estos cambios estén en GitHub. No se ha ejecutado una publicación ni un despliegue remoto desde esta edición.
