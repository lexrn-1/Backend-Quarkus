# Firebase y Firestore

## Local

El perfil `local` y las pruebas usan el proyecto ficticio `demo-app-meet`, sin credenciales privadas. Firebase CLI inicia Auth 9099 y Firestore 8085. Las variables de emulador se indican sin `http://`. Los tokens del emulador no prueban firmas de producción.

La prueba REST escribe y modifica `demo/sprint0` usando el acceso administrativo del emulador. El backend hace una lectura nueva en cada petición, con espera máxima de tres segundos. Documento ausente: 404; dato inválido o dependencia fallida: 503. No almacenar datos sensibles en esta colección pública.

## Producción preparada

Montar una cuenta de servicio distinta por backend en `/run/secrets/firebase.json`, solo lectura, legible por UID 10001. Indicar su ruta con `GOOGLE_APPLICATION_CREDENTIALS` y el proyecto con `GOOGLE_CLOUD_PROJECT`. Se admite Application Default Credentials; no se cargan secretos desde el classpath ni se incorporan JSON a imágenes.

Sin credenciales, readiness queda DOWN y demo devuelve 503. Para autenticación real, el SDK verifica emisor/audiencia, firma y expiración del ID token. La revocación y las políticas de perfiles se implementarán en los sprints de identidad; esta prueba técnica no las sustituye. Los callbacks realtime comprueban expiración antes de aceptar eventos técnicos.

La configuración rechaza emuladores fuera de local/test. No trasladar las variables locales a Dokploy. Firestore Admin omite las reglas de cliente: la autorización futura debe implementarse en los servicios. Aplicar IAM de mínimo privilegio viable por servicio; las restricciones IAM no equivalen a las reglas por documento.

`firestore.rules` niega toda lectura/escritura directa del cliente. Su publicación en Firebase real queda pendiente y requiere verificar el proyecto de destino. El archivo de índices está vacío porque la lectura demo no necesita índices compuestos.

## Modelo acordado para sprints siguientes (no implementado)

| Colección | Propietario / propósito |
|---|---|
| users/{uid} | API: perfil y estado, nunca contraseñas/tokens |
| usernames/{normalized} | API: reserva única transaccional |
| rooms/{roomId} | API: propietario, nombre y estado |
| rooms/{roomId}/members/{uid} | API escribe membresías; realtime consulta |
| rooms/{roomId}/messages/{messageId} | Realtime escribe; API podrá borrar por privacidad |
| deletionJobs/{uid} | API: limpieza recuperable |

Consultas previstas: salas por ownerUid/createdAt y mensajes por createdAt/messageId. Añadir y probar índices al implementar esas consultas; no declarar índices desplegados ahora. Las subcolecciones no se borran automáticamente al eliminar una sala. Normalización de usernames, política de borrado y límites se cerrarán antes de las historias correspondientes.

## Conexión local al proyecto real

La configuración local de proyecto y ruta administrativa está en `.env`, excluido de Git y de las imágenes. El proveedor CDI consulta `GOOGLE_APPLICATION_CREDENTIALS` desde la configuración Quarkus, de modo que reconoce tanto `.env` como variables del proceso, y abre el JSON externo sin copiar su contenido. Sin ruta explícita conserva el fallback a Application Default Credentials.

Desde la raíz del backend, con `.env` configurado y sin variables de emuladores:

```bash
./mvnw -pl backend-api quarkus:dev
./mvnw -pl backend-realtime quarkus:dev -Ddebug=5006
```

Ejecutar en terminales separadas. No activar el perfil `local`, reservado para emuladores. En este entorno se comprobó acceso real de ambos servicios mediante readiness UP. La lectura de demo devolvió 404 porque `demo/sprint0` no existe; no se crearon documentos ni se publicaron reglas. El handshake con un ID token de Firebase real sigue pendiente de verificación.

Para probar el flujo demo, crear posteriormente `demo/sprint0` con un campo string `message` de contenido público. Esa escritura no forma parte de la comprobación de conexión. El despliegue con contenedores requiere montar la credencial en su ruta interna según la guía Dokploy; la ruta del equipo local no existe dentro de las imágenes.
