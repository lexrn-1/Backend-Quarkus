# Preparación Dokploy

No se ha accedido a un VPS ni publicado imágenes. Los nombres siguientes son ilustrativos.

1. Ejecutar CI y habilitar GHCR en el repositorio. El workflow construye imágenes después de verificar Maven e integración; solo publica al ejecutar manualmente `publish=true` desde main con permisos de paquetes. Usar etiqueta SHA inmutable y conservar la anterior.
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

## Publicar esta extensión: salas/chat

No se ha desplegado esta entrega. Construir y desplegar nuevas imágenes de ambos servicios y actualizar frontend de forma coordinada. Antes, crear los índices de `firestore.indexes.json` y configurar credenciales e IAM externos. Una sola réplica realtime. Para el dominio compartido puede combinarse `compose.dokploy.yaml` con `compose.gateway.yaml`; ajustar nombre de red y certresolver/entrypoint a la instalación real de Traefik. Quitar las dos rutas `/` duplicadas de Dokploy UI si se usan las labels del overlay.

| Prioridad | Regla del mismo host | Destino |
|---|---|---|
| 300 | `/api/v1/rooms/{uuid}/messages` exacta | realtime HTTP 8080 |
| 200 | prefijo `/socket.io/` | realtime 9092, polling y upgrade WebSocket |
| 100 | resto | API HTTP 8080 |

No dirigir todo `/api/v1/rooms/` a realtime: creación/unión/edición pertenecen a API. No usar readiness como URL base. Angular usa `https://api.5-253-232-179.sslip.io/api/v1` y Socket.IO el mismo origen sin `/api/v1`.

TURN_USERNAME/TURN_PASSWORD deben configurarse externamente; la aplicación omite marcadores xxxxxxxx. Después de publicar comprobar CORS desde Vercel, Auth real, history 401 sin token, polling/upgrade y seis conexiones. La entrega local no sustituye esas pruebas ni la llamada entre redes con TURN real. Health de realtime verifica Firestore/listener, no conectividad de medios P2P.
