# Salas, presencia, chat y medios

Implementación local en `develop`. No publica servicios ni modifica Firebase real. Las historias pegadas por el usuario especifican comportamiento; no son evidencias de aceptación. La entrega extiende identidad de Sprint 1 y conserva sus límites institucionales pendientes.

## Límites y responsabilidades

`backend-api` posee el agregado Sala: ID UUID v4, nombre recortado de 1–100 caracteres, propietario inmutable y estado ACTIVE/DELETING. Crea la membresía OWNER junto con la sala en una transacción. Unirse por ID crea MEMBER de forma idempotente. Conocer un UUID permite solicitar membresía con perfil completo; no hay catálogo público ni aprobación privada. Solo OWNER renombra/elimina; la API valida los permisos, aunque el frontend oculte controles.

`backend-realtime` posee Mensaje y la presencia efímera LiveRoom. Cada pestaña cuenta como conexión diferente, incluso con el mismo uid. Admite como máximo seis conexiones simultáneas incluyendo al anfitrión; no limita membresías persistidas. Un monitor serializa admisión y eventos por proceso. Esta versión requiere **una réplica de realtime**; varias réplicas necesitarían coordinación de capacidad, presencia y difusión antes de escalar.

No existen horarios, materias, catálogo, adjuntos, tablero, contadores de no leídos ni salas privadas. Salir retira la presencia y detiene medios; conserva membresía/historial. No existe REST `/leave` ni `/chats`.

## REST implementado

Todas las rutas de esta tabla requieren Firebase ID token y perfil COMPLETE. Prefijo `/api/v1`.

| Servicio | Método y ruta | Resultado |
|---|---|---|
| API | POST `/rooms` con `{name}` | 201 Sala |
| API | GET `/rooms?scope=owned&limit=20&cursor=…` | `{items,nextCursor}`; scope owned o member; límite 1–50 |
| API | GET `/rooms/{id}` | Sala; membresía requerida |
| API | POST `/rooms/{id}/join` | Sala; unión idempotente |
| API | PATCH `/rooms/{id}` con `{name}` | Sala; solo OWNER |
| API | DELETE `/rooms/{id}` | 202 `{roomId,status:"DELETING"}`; solo OWNER |
| API | GET `/rtc/config` | `{iceServers,turnConfigured,maxConnections:6}`; no-store |
| Realtime HTTP | GET `/rooms/{id}/messages?limit=30&before=…` | `{items,nextCursor}`; miembros; límite 1–100 |

Sala: `{id,name,ownerUid,role,status,createdAt,updatedAt}`. `role` es OWNER/MEMBER para quien consulta. Las fechas se generan en servidor. Los campos desconocidos se rechazan en JSON; no se aceptan propietario/rol/uid enviados por el cliente.

El listado owned ordena creación descendente e ID ascendente. member ordena incorporación descendente y ruta ascendente. El cursor es opaco y está vinculado al uid/scope. El frontend sigue todas las páginas para sus listas. La vista del panel representa salas propias; Chats muestra membresías, sin inventar actividad o no leídos persistidos.

Mensaje: `{id,roomId,clientMessageId,sender:{uid,displayName,avatar},text,createdAt,cursor}`. `createdAt` conserva nueve dígitos fraccionarios. Historial en orden ASC por timestamp e ID, incluyendo desempate estable. Sin cursor devuelve los últimos N; `before` devuelve N anteriores; `after` devuelve N posteriores para recuperación. Son exclusivos y mutuamente excluyentes. `nextCursor` sigue la dirección solicitada, null al agotarse; para `after` iniciar con el cursor del último mensaje conocido. Los cursores se vinculan a la sala y se validan; no sustituyen autorización.

Errores REST `{code,message}`: 400 validación/cursor, 401 sesión inválida, 403 perfil/permisos/membresía, 404 sala inexistente, 409 sala cerrada/conflicto y 503 dependencia. Tras finalizar limpieza, una sala eliminada responde 404; durante DELETING devuelve conflicto. JSON malformado puede usar la respuesta estándar de Quarkus.

## Eventos Socket.IO v1

Origen autorizado, handshake `auth:{token:FirebaseIdToken}`, path `/socket.io/`. Perfil completo obligatorio. Los eventos de producto no requieren proofSession; el perfil local conserva pruebas `proof:*` y exige proofSession si se habilita esa prueba. Para Angular local arrancar realtime con `-Dapp.socket.proof-enabled=false`, o usar el fixture de navegador incluido. Producción no admite emuladores ni `proof:*`.

Solicitud: `{v:1,roomId,payload}`. Ack: `{ok:true,data}` o `{ok:false,error:{code,message}}`. Eventos del servidor: `{v:1,eventId,roomId,payload}`. No usar `socket.id` como identidad de presencia: usar `connectionId` del snapshot.

| Evento cliente | payload / respuesta |
|---|---|
| room:join | `{}`; ack/snapshot `{roomId,connectionId,name,ownerUid,participants,maxConnections}` |
| room:leave | `{}`; retira presencia, conserva membresía |
| chat:history | `{limit?,before?,after?}`; ack página de historial, mismas autorizaciones que REST |
| chat:send | `{clientMessageId:UUIDv4,text}`; ack Mensaje persistido |
| media:update | `{micOn:boolean,camOn:boolean,screenSharing:boolean}` |
| rtc:signal | `{toConnectionId,signal}`; SDP/ICE al destinatario presente en la misma sala |

| Evento servidor | payload |
|---|---|
| room:snapshot | Snapshot completo; precede a deltas de presencia |
| participant:joined / participant:left | `{participant}` |
| media:update | `{participant}` |
| chat:message | `{message}` |
| rtc:signal | `{fromConnectionId,toConnectionId,signal}` |
| room:updated | `{name}` |
| room:deleted | `{roomId}`; cliente libera recursos y navega al panel |
| room:unavailable | `{code:"DEPENDENCY_UNAVAILABLE"}`; no significa eliminación |
| session:expired | `{code:"UNAUTHORIZED"}` sin envoltura; cerrar sesión local |

Participante: `{connectionId,uid,displayName,avatar,micOn,camOn,screenSharing}`. Estos flags describen estado del cliente; no prueban entrega de audio/vídeo. El servidor deriva remitente y perfil desde la sesión. Rechaza suplantación, señal fuera de sala, texto vacío/mayor de 2000, versión incorrecta y flags inválidos. `ROOM_FULL` rechaza la séptima conexión sin desplazar a las existentes. Un socket mantiene una sala activa.

Máximo 60 solicitudes por segundo/conexión; frame HTTP/WebSocket 128 KiB; señal acotada a 64 KiB de representación validada. Validación Firebase y acceso Firestore fuera del event loop, en ejecutores ordenados por canal. Revalidación de conexiones cada cinco segundos y antes de cada operación. Si una dependencia falla no se inventa un borrado: las operaciones fallan y el cliente puede reintentar. El scheduler conservador puede desconectar y exigir reingreso si no logra validar la sesión.

## Persistencia e idempotencia

Firestore: `rooms/{id}`, `rooms/{id}/members/{uid}`, `rooms/{id}/messages/{hash}` y `roomDeletionJobs/{id}`. `hash=SHA256(uid + "|" + clientMessageId)`. La transacción comprueba sala activa, membresía y perfil, resuelve el remitente y persiste el mensaje antes de ack/difusión. Reintentar mismo UUID/texto devuelve el mismo ID/fecha; cambiar el texto con igual clave devuelve MESSAGE_ID_CONFLICT. Puede repetirse la difusión tras un reintento: Angular mezcla por ID canónico y orden timestamp/ID.

API es la única escritora de salas/membresías; realtime de mensajes. La eliminación es la excepción explícita: API marca DELETING y guarda un job en la misma transacción. Un worker reclama jobs con lease, borra mensajes y miembros por lotes y finalmente borra sala/marca DONE. Un fallo deja trabajo reintentable; reiniciar no depende de la memoria del proceso. No hay promesa de eliminación física instantánea ni purga automática de jobs DONE.

Los índices necesarios están en `firestore.indexes.json`: rooms(ownerUid,status,createdAt DESC,name ASC) y collection group members(uid,joinedAt DESC,name ASC). `name` aquí es `__name__` (ID/ruta del documento). Historial usa el índice sencillo createdAt/ID ascendente. El emulador no prueba que los índices estén creados en producción. Aplicarlos antes de publicar, con un proyecto explícito, después de revisar los recursos del usuario. Las reglas del navegador siguen denegando todo; Firebase Admin requiere IAM en servidor y omite esas reglas.

## Angular y Atomic Design

`RoomsService`, `ChatService`, `RealtimeService` y `PeerConnectionsService` son adaptadores consumidos por stores; componentes no invocan Firebase Admin, Firestore ni Socket.IO directamente. La identidad sigue pasando por Firebase SDK. Se reutilizan átomos/moléculas/organismos de tarjetas, diálogos, compositor y lista; `MediaStreamComponent` encapsula vídeo/audio y recuperación de autoplay.

Chat integrado y Chats comparten ChatStore: snapshot antes del historial, mezcla de mensajes que llegan durante HTTP, claves estables para reintentos, paginación anterior y recuperación `after` al reconectar/enfocar. El scroll existente conserva lectura de mensajes anteriores y muestra «Mensajes nuevos». La sesión cerrada limpia caches. Se deshabilita el bypass de desarrollo. Acciones fuera de alcance se ocultan o deshabilitan.

WebRTC usa simple-peer 9.11.1 (bundle de navegador, sin polyfills Node globales) y socket.io-client 4.8.4. El iniciador se decide por orden lexicográfico del connectionId; señalización autorizada por servidor. Medios por WebRTC, nunca por Socket.IO/Firestore. Cada conexión mantiene hasta cinco pares. Reingresar/reconectar destruye pares antiguos. Denegar dispositivos conserva chat. Compartir pantalla reemplaza la pista de vídeo/restaura cámara, incluso al detener desde navegador. Salir/cerrar sesión destruye pares, streams y captura.

STUN `stun:stun.l.google.com:19302`. TURN opcional `free.expressturn.com:3478` UDP/TCP, variables TURN_USERNAME/TURN_PASSWORD externas. Vacíos o xxxxxxxx deshabilitan TURN: no son credenciales válidas. La configuración ICE se entrega a clientes autenticados y esas credenciales son visibles en el navegador; usar cuenta TURN dedicada, cuotas/rotación y valorar credenciales temporales antes de una publicación amplia. No equivalen a secretos Firebase. Pruebas en redes distintas, dispositivos físicos y relay real quedan pendientes hasta disponer de valores reales.

La captura de vídeo solicita 640×360 a 15 fps (máximo solicitado 24), para moderar consumo en la malla; el navegador negocia según dispositivo. Cada handshake solicita un ID token actualizado. Compose local desactiva proofSession para conectar clientes de producto.

La corrección posterior de historial y micrófono se detalla en [chat/audio](chat-audio.md). Los eventos de producto incluyen también chat:history; el cliente lo utiliza como alternativa si REST está mal enrutado (404/405).
