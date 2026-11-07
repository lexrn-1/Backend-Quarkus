# QA Sprint 0

## Maven

`./mvnw clean verify`: pruebas unitarias de lectura demo (éxito, ausencia, dependencia fallida) y ArchUnit sobre código real. No mide cobertura artificial ni prueba negocio futuro.

## Integración reproducible

Después de compilar: `cd tests/integration && npm ci && npm run verify`.

El cliente Node es una herramienta de pruebas, no un tercer servicio de producto. Inicia los JAR en puertos de prueba, usa emuladores locales y termina los procesos incluso si falla una aserción. Diagnósticos en `backend-*/target/integration-*.log`.

Casos: demo 404/200/actualización/503, CORS permitido y denegado, liveness/readiness/Swagger/OpenAPI, handshake válido, sin auth, auth vacío, token inválido, sesión técnica no permitida y origen denegado; polling y WebSocket; ack, señal dirigida, aislamiento de sesiones, payload inválido y reconexión tras caída del transporte. Prueba la caída de Firestore con otro proceso API apuntando a un puerto inactivo.

No registrar tokens ni cuerpos de auth. El logger de errores crudos InPacketHandler de la librería está deshabilitado para evitar que un frame malformado exponga credenciales. Mantener logs de aplicación y estado de los servicios.

## Pendientes externos

- Firma de tokens reales, credenciales e IAM de Firebase; publicación de reglas e índices cuando corresponda.
- Build y arranque de imágenes/Compose en un daemon accesible.
- HTTPS/WSS y polling a través de Traefik; reconexión publicada.
- Angular → API → Firestore con estados de carga/error/éxito y sin errores de consola.
- simple-peer/ICE en dos dispositivos/redes, incluido TURN; no validado con señales sintéticas.
- Accesibilidad TS-04, Lighthouse y evidencias públicas/PR exigidas por las historias originales.
