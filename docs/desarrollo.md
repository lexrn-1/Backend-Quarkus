# Desarrollo

Para construir un módulo: `./mvnw -pl backend-api clean verify` o `./mvnw -pl backend-realtime clean verify`. No se requiere instalar el padre en el repositorio local.

Al añadir funcionalidad:

1. Definir dominio puro y un puerto de entrada por capacidad.
2. Declarar necesidades externas como puertos de salida e inyectarlas por constructor en el caso de uso.
3. Implementar adaptadores en infraestructura, con entidades de persistencia separadas.
4. Conectar implementaciones mediante productores CDI y exponer REST/eventos usando puertos.
5. Añadir pruebas de reglas, permisos, errores y contratos reales; ampliar ArchUnit sobre clases existentes.
6. Actualizar documentación y distinguir implementado de propuesto.

Eliminar `.gitkeep` cuando una carpeta ya tenga archivos reales. La duplicación de configuración Firebase entre servicios mantiene independencia; no compartir dominio ni añadir dependencia entre módulos para evitarla.

Trabajar por rama y PR cuando se conecte el repositorio. El trabajo actual se entrega en develop; master se conserva. No se ha creado un PR ni se declara revisión o despliegue remoto.
