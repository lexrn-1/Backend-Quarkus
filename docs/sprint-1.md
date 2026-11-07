# Sprint 1 — Identidad y acceso backend

Alcance confirmado: solo backend para US-01, US-02 y US-03. Se conserva el sprint 0 y la arquitectura hexagonal. Angular, QA publicado y despliegue no forman parte de esta entrega.

## Funcionalidad entregada

| Historia | Backend implementado | Responsabilidad del cliente / pendiente |
|---|---|---|
| US-01 registro manual | Validar identidad Firebase, completar nombres/apellidos/avatar, reservar username único con transacción, rechazar datos inválidos e identidad impuesta | Firebase SDK crea cuenta; mostrar formulario, política de contraseña, errores y conservar datos |
| US-02 Google | Distinguir primer acceso PENDING de recurrente COMPLETE; datos de prellenado; username obligatorio; bloqueo de acceso incompleto y colisiones | SDK maneja popup/redirect, cancelación y errores de Google; formulario obligatorio y navegación |
| US-03 login/logout | Validar Firebase ID token, cuenta habilitada, revocación y perfil en cada solicitud; perfil privado; invalidar token actual al salir | SDK hace login/signOut; mensaje genérico de credenciales; retorno a ruta solicitada y protección al volver atrás |

Endpoints: `GET /api/v1/session`, `POST /api/v1/me/complete`, `GET /api/v1/me` y `POST /api/v1/session/logout`. Swagger tiene esquema Firebase Bearer. Consultar [contratos](contratos.md) y [guía de identidad](identidad.md).

La ausencia de documento de perfil equivale a PENDING recuperable, sin escrituras en GET. La finalización crea `users/{uid}` y `usernames/{usernameNormalized}` en una sola transacción. Auth y Firestore no tienen atomicidad global; los reintentos conservan el uid y no vuelven a crear la cuenta.

## Verificación

```bash
./mvnw clean verify
npm --prefix tests/integration ci
npm --prefix tests/integration run verify
```

Verificación local del 7 de noviembre de 2026: **17 comprobaciones Maven aprobadas y suite de integración final con salida 0**.

Las pruebas Maven cubren reglas de nombres/username/avatar/correo, casos de uso y arquitectura. La integración con emuladores ejercita los JAR reales y transacciones Firestore: alta/login manual, Google inicial/recurrente, colisiones concurrentes con un ganador, finalización idempotente, errores 400/401/403/409/503, perfiles pendientes, expiración/logout y recuperación tras fallo de Firestore.

Las verificaciones adicionales comprueban CORS con Authorization, Swagger Bearer, cuenta Firebase deshabilitada/eliminada, revocación administrativa, denegación de acceso directo a Firestore, sesión realtime rechazada con perfil pendiente/token revocado, reconexión y rechazo de eventos tras logout de una conexión ya abierta. Se ejecuta un proceso API con lista institucional restringida para probar la configuración sin imponer esa lista al proyecto real.

Los resultados de ejecución se registran en [evidencias locales](evidencias/sprint-1-local.txt). No son evidencia de autenticación Google real ni de QA en producción.

## Decisiones y pendientes

- El usuario confirmó mantener solo backend y dejar los dominios institucionales pendientes. `APP_ALLOWED_EMAIL_DOMAINS` vacía acepta cualquier correo válido autenticado; configurarla antes de afirmar restricción institucional.
- Username: NFKC/trim, 3–20 caracteres ASCII alfanuméricos o guion bajo, unicidad sin distinguir mayúsculas. Avatar seleccionado o referencia HTTPS validada; el backend no descarga imágenes.
- Firebase gestiona contraseñas y proveedores. Activar Email/Password/Google, dominios autorizados y política visible de contraseña para la integración real. No se modificaron estos ajustes remotos ni se crearon usuarios de prueba en Firebase real.
- Logout invalida el ID token actual; el SDK debe eliminar su sesión local. No se cierran otras sesiones ni se emiten tokens propios. TTL de `revokedTokens.expiresAt` es una configuración operativa opcional pendiente.
- Editar/eliminar perfiles y todas las historias de salas/chat/media corresponden a los sprints siguientes. No se agregaron en esta entrega.
- Permanecen los pendientes de publicación, contenedores, WSS, Angular, accesibilidad, evidencias/PR y revisión del equipo descritos en Sprint 0.
