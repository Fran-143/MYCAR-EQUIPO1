# Instrucciones para Cursor — revisión e integración de persistencia RF01

Trabaja primero sobre el esquema y las migraciones del main actualizado de MYCAR-EQUIPO1.
La entrega procede de una copia local anterior y contiene archivos NO incorporados al commit de origen.
No asumir que main todavía coincide con él. Este paquete solicita revisar la migración, no activar RF01.

1. Inventariar migraciones de todas las ubicaciones Flyway efectivas, esquemas/tablas/columnas y
   versión/historial de cada base prevista. Buscar códigos/verificaciones/idempotencia equivalentes.
   Comparar contenido y contratos, no solo nombres o fechas; documentar equivalencias y diferencias.
2. Leer README, DICCIONARIO, RELACIONES_Y_REGLAS y los seis archivos de referencia backend. No suponer
   que estos archivos locales ya existen en main ni integrar el backend por incluirse como referencia.
3. No crear tablas manualmente que Flyway vaya a duplicar. Mantener un único responsable/versionado
   del DDL y una sola migración canónica para cada cambio. Si ya existe trabajo equivalente, proponer
   cómo reconciliarlo sin otro CREATE TABLE o ADD COLUMN repetido.
4. Coordinar la versión libre con Francesca/equipo. **V7 es el nombre local entregado, no una reserva
   en main.** Revisar también trabajo paralelo del equipo y ubicaciones alternativas del classpath.
5. No editar migraciones ya aplicadas ni reparar checksums para ocultar cambios. Si una versión ya se
   aplicó a una base persistente, cualquier ajuste exige una migración posterior y análisis del estado.
   Una ejecución previa en bases de pruebas retiradas no equivale a aplicación en todas las bases.
6. Leer las brechas SQL/backend documentadas. Presentar cualquier endurecimiento como cambio explícito,
   separado del archivo exacto recibido. Mantener nombres de esquema/tablas/columnas, catálogos, FK,
   unicidades y semántica compatibles con las consultas Java; si hay cambios, devolver mapa antes/después
   y archivos/backend/pruebas afectados. Revisar especialmente uq_usuario_correo_normalizado, nombre
   preexistente que JdbcRegistrationStore utiliza para clasificar conflictos.
7. Elegir la ruta/configuración de integración de forma consciente. La ruta entregada no está activa:
   application.properties solo carga db/mvp. Mover/configurar una migración puede hacer que se aplique
   al siguiente arranque autorizado del backend; no hacerlo sin identificar previamente la conexión.
8. Probar en PostgreSQL NUEVO desechable: cadena completa desde vacío, cuenta anterior con fecha NULL,
   PK/FK/CHECK/índices, ejecución de migración única, validate/rearranque sin repetir DDL, compatibilidad
   de los métodos Java y pruebas transaccionales/concurrentes. Si main difiere, adaptar y ejecutar los
   escenarios de las 36 pruebas nuevas y las existentes; no dar por válidas las 80 de la copia local.
   No usar correo real ni fixtures que contengan datos personales. Retirar base/rol de prueba creados.
9. No aplicar a una base de la app durante esta revisión. Antes de una aplicación futura: identificar
   inequívocamente host/puerto/base/rol, historial Flyway, objetos equivalentes y plan de respaldo/cambio.
   No incluir credenciales en commits, entregas o logs. No activar RF03/RF07/RF53 ni alterar cuentas antiguas.
10. Entregar una nota de retorno con: commit integrado, ruta y nombre canónicos, hash SQL, esquema final,
    diferencias respecto del ZIP, resultados de pruebas, bases verificadas y estado de aplicación real.

## Qué debe indicar la nota para la copia local de la autora

- Archivo local actual: backend/src/main/resources/db/rf01-verificacion/V7__rf01_verificacion_aislada.sql.
- Indicar expresamente si se conserva en esa ruta, se sustituye por un archivo de main o se retira una
  vez respaldado/verificada la equivalencia. Nombrar el archivo canónico exacto; no decir solo 'usar V7'.
- Si main integra el mismo DDL con otra ruta/versión, NO dejar cargables ambas copias. Con mismo número
  habrá duplicidad de versión; con números distintos puede repetirse CREATE/ADD COLUMN.
- VerificationPostgresTest incluye explícitamente db/mvp y db/rf01-verificacion, y asume siete migraciones
  exitosas. Debe revisarse junto con cualquier traslado/nueva versión/otra migración agregada a main.
- Documentar qué ubicaciones Flyway y referencias de pruebas/documentación debe ajustar la autora
  después de actualizar su rama, preservando configuración local, lanzador, backend interno y datos.
- Si no hay equivalencia exacta, no indicar borrar la copia local sin explicar incompatibilidades.

**Git y PostgreSQL son estados distintos.** Actualizar la rama no demuestra aplicación del DDL.
La nota debe separar: archivo integrado, migración detectada por Flyway y migración aplicada en cada base.
