# Seguimiento de aceptación: salas y colaboración

Las historias suministradas definen criterios, no evidencias de ejecución. Se implementaron sobre identidad de Sprint 1, sin adelantar catálogo, archivos, tablero o estadísticas. [Contratos y límites](salas-chat.md), [evidencia local](evidencias/salas-chat-local.txt).

| Criterio | Estado | Evidencia / límite |
|---|---|---|
| Crear sala con nombre 1–100 e ID único | Verificado localmente | API y navegador; persistencia Firestore emulada |
| Dashboard vacío/salas propias y copiar ID | Verificado localmente | UI reutilizada; copia y confirmación accesible en E2E |
| Renombrar/eliminar solo OWNER | Verificado localmente | API 403 para invitado; controles ocultos; rename recibido en navegador |
| Eliminar expulsa y limpia historial/membresías | Verificado localmente | room:deleted, navegación al panel, consulta administrativa de subcolecciones vacías |
| Recuperar borrado al reiniciar | Verificado localmente | Proceso nuevo retoma estado PENDING simulado con API detenida |
| Unión por UUID/trim y enlace protegido | Verificado localmente | Validación, inexistente, unión idempotente y vuelta al enlace tras login |
| Grid/presencia/estados | Verificado localmente | Seis contextos, cambios por connectionId, avisos accesibles |
| Chat instantáneo aislado, vacío rechazado | Verificado localmente | Socket.IO autenticado, pruebas entre salas y navegadores |
| Persistencia, orden y páginas anteriores | Verificado localmente | Firestore emulado, timestamp/ID, before/after, UI compartida |
| Scroll durante lectura anterior | Implementado; cobertura local existente | Se conserva MessageList y su indicador; no se fuerza scroll al leer anteriores |
| Reintentos/reconexión sin duplicados | Verificado localmente | ID estable por intento, fusión por ID y recuperación after |
| Seis conexiones, rechazo de séptima | Verificado localmente | Admisión concurrente en backend y cinco vídeos remotos por navegador |
| Audio/vídeo y pantalla | Verificado con medios ficticios | WebRTC local; pista de pantalla/restauración y ended; sin validar calidad física/red real |
| Denegar permisos conserva chat; salir libera medios | Verificado localmente | E2E y pruebas de captura tardía tras salir |
| DDD/hexagonal y Atomic Design | Implementado y comprobado localmente | ArchUnit sobre clases reales; adaptadores/stores y componentes reutilizados |
| Compilación y pruebas | Verificado localmente | Maven 24; Angular 97; Playwright 2 escenarios |
| Compose/Dokploy y rutas gateway | Configuración validada | compose config OK; Docker daemon sin permisos, sin imágenes/arranque |
| Índices, IAM y firma Firebase real | Pendiente externo | No se modificó el proyecto real |
| Vercel, HTTPS/WSS, Traefik y reconexión publicados | Pendiente externo | Requiere publicación coordinada |
| TURN y dispositivos en redes distintas | Pendiente externo | Faltan credenciales TURN reales y recursos de prueba |

No se declara aceptación pública end-to-end ni cierre de los criterios que dependen de infraestructura externa.
