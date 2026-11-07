# Estado Sprint 0 — frente backend

Verificación local: 7 de noviembre de 2026. Alcance acordado: backend, preparación de publicación, sin Angular ni acceso remoto. El sprint completo de los documentos sigue abierto hasta reunir sus evidencias end-to-end.

| Criterio | Estado | Evidencia / límite |
|---|---|---|
| Dos microservicios, Java 21, BOM fijado, Wrapper | Verificado local | backend-api y backend-realtime; compilación Maven |
| Capas hexagonales y núcleo puro | Verificado local | ArchUnit sobre clases reales de ambos módulos |
| REST/JSON/validación/OpenAPI/health | Verificado local | Extensiones, endpoint demo y endpoints técnicos |
| Lectura demo Firestore por puertos | Verificado con emulador | 404; 200; cambio reflejado en siguiente lectura; 503 por fallo |
| Firestore sin acceso directo del cliente | Verificado con emulador | Reglas deny-all; petición con token cliente rechazada con 403 |
| Firebase Admin con credenciales externas | Acceso real verificado | Ambos servicios: readiness UP; demo 404 por documento ausente; IAM mínimo y tokens reales pendientes |
| Auth Socket.IO en Quarkus | Verificado con emulador | Cliente 4.8.4 y servidor 2.0.13; token válido, ausente, vacío e inválido |
| Orígenes explícitos | Verificado local | CORS REST y rechazo de Origin Socket.IO no permitido |
| Ack, señal e aislamiento técnico | Verificado local | Dos conexiones en misma sesión; tercera aislada; destino inválido |
| Polling, WebSocket y reconexión | Verificado local | Conexión directa, sin Traefik ni TLS |
| Inicio/cierre del listener | Verificado local | Lifecycle CDI; procesos terminados y puerto cerrado |
| Readiness real y liveness independiente | Verificado local | Caída de Firestore: readiness DOWN, liveness UP; listener en realtime |
| Emuladores prohibidos en producción | Verificado local | Ambos JAR rechazan sus variables al iniciar con perfil prod |
| Dockerfile por servicio y Compose | Archivos preparados | Sintaxis Compose validada; build/arranque pendientes por acceso al daemon |
| CI y construcción/publicación de imágenes | Preparado | Workflow GitHub; aún no ejecutado en un repositorio remoto |
| Contratos, modelo, seguridad e índices | Documentado | Distingue implementado de propuesto; solo demo requiere almacenamiento actual |
| README de arranque y Dokploy | Entregado | Guías y variables; sin credenciales ni publicación |
| Dato visible en Angular publicado | Pendiente externo | No hay frontend en el alcance acordado |
| HTTPS/WSS y Traefik | Pendiente externo | Requiere repositorio, dominios y VPS; conexión local a Firestore real ya comprobada |
| simple-peer y conectividad ICE/TURN | Pendiente externo | Señales sintéticas no demuestran WebRTC |
| TS-04 y evidencias públicas/PR/QA | Pendiente externo | Requiere aplicación Angular publicada y trabajo del equipo |

[Resumen de ejecución local](evidencias/sprint-0-local.txt). No contiene tokens ni logs de credenciales.

## Comandos de evidencia

```bash
./mvnw clean verify
cd tests/integration
npm ci
npm run verify
```

Entorno local comprobado: OpenJDK 21.0.13.0.3, Node 26.13.0 y Netty 4.1.138.Final resuelto por el BOM. CI usa Java 21 y Node 22; su ejecución remota queda pendiente.

Maven: siete comprobaciones aprobadas (tres unitarias de demo y cuatro reglas ArchUnit), sin fallos. La integración ejecuta los servicios reales con Auth/Firestore emulados; no reemplaza validación de firma Firebase en producción.

También se validaron ambos Compose con `docker compose config --quiet` (producción con valores ilustrativos). Docker Engine responde `permission denied` al acceder a `/var/run/docker.sock` con el usuario actual: no se construyeron ni iniciaron imágenes. La CI incluye esas comprobaciones para un runner con daemon disponible, pero su resultado remoto aún no existe.

## Decisiones técnicas comprobadas

netty-socketio 2.0.13 omite AuthTokenListener si CONNECT no trae datos; un guard rechaza ese formato antes del procesamiento. Su callback ConnectListener ocurre durante la conexión Engine.IO previa al token, por lo que no se usa como evidencia de autenticación. Los eventos técnicos verifican identidad y expiración.

Los callbacks bloqueantes se ejecutan en workers. El adaptador retiene el buffer de transporte hasta terminar de procesarlo y libera los ejecutores al cerrar. Se conserva Socket.IO; no se sustituyó el protocolo por WebSocket nativo.

La versión exacta de Netty es la resuelta por el BOM Quarkus, consultable con `./mvnw -pl backend-realtime dependency:tree -Dincludes=io.netty`. No se promete compatibilidad nativa, escalado horizontal ni entrega fiable de mensajes de negocio.

## Comprobación posterior de Firebase real

Se configuró la ruta externa de cuenta de servicio en `.env`, sin incorporar claves al código. `./mvnw verify` pasó nuevamente. Ambos JAR iniciados con perfil prod y sin exportar credenciales (cargadas por Quarkus desde `.env`) respondieron readiness UP; API demo devolvió 404 por documento inexistente. Comprobación de solo lectura, sin publicación ni escrituras. La autenticación con ID tokens reales sigue pendiente.
