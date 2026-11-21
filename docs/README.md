# Documentación de identidad y colaboración

| Documento | Contenido |
|---|---|
| [Aceptación de colaboración](salas-chat-aceptacion.md) | Criterios verificados y pendientes externos |
| [Salas y chat](salas-chat.md) | Contratos implementados, DDD, persistencia y WebRTC |
| [README general](../README.md) | Arranque y comandos verificables |
| [Arquitectura](arquitectura.md) | Capas y flujo implementado |
| [Microservicios](microservicios.md) | Configuración, límites y versiones |
| [Firebase](firebase.md) | Credenciales, emuladores, modelo y reglas |
| [Contratos](contratos.md) | REST/eventos actuales y futuros |
| [Pruebas](pruebas.md) | QA local y pendientes de integración pública |
| [Latencia y recuperación de medios](latencia-media.md) | Historial por socket, lecturas batch, recuperación WebRTC y diagnóstico de TURN |
| [Dokploy](dokploy.md) | Imágenes, secretos, proxy y rollback |
| [Desarrollo](desarrollo.md) | Extensión de las capas |
| [Integración cliente](integracion-cliente.md) | URLs, CORS y contratos para Angular |
| [Identidad](identidad.md) | Registro, login, Google y acceso protegido |
| [Estado Sprint 1](sprint-1.md) | Implementación US-01/02/03 y pendientes externos |
| [Estado Sprint 0](sprint-0.md) | Evidencias y límites de aceptación |

El plan Quarkus adapta el stack original a Java/Dokploy. Las historias de usuario conservan los criterios funcionales; Sprint 0/1 entregaron backend; la extensión de salas/chat integra también Angular. La documentación de origen no constituye evidencia de ejecución ni despliegue.

[Corrección de chat y micrófono](chat-audio.md): diagnóstico, fallback de historial, sincronización de dispositivos y validación.
