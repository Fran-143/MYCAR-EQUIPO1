# MyCar MVP · PostgreSQL completo + modelo lógico

**Fecha:** 26 de octubre de 2026. **Alcance:** RF01, RF02, RF06, RF08, RF09, RF10, RF14, RF17, RF18, RF22 y RF23. **Base de partida:** sin tablas de negocio (confirmado por el equipo). **Total:** 21 tablas, un esquema `mycar`.

## Archivos

| Archivo | Propósito |
|---|---|
| `09_INSTALACION_TODO_EN_UNO.sql` | Opción recomendada: ejecutar **UNA SOLA VEZ** todo el esquema + semillas + funciones, una transacción global |
| `00_CREAR_BASE_OPCIONAL.sql` | Crear base `mycar_mvp` SOLO si no existe; ejecutar conectado a `postgres` |
| `01_ESQUEMA_MYCAR.sql` | Crear las 21 tablas, índices, FK, CHECK, triggers y funciones puras de estados |
| `02_CATALOGOS_INICIALES.sql` | Cargar plan FREE, usos Particular/Taxi, 7 tipos mantenimiento y opciones técnicas vehiculares |
| `03_LOGICA_BD.sql` | Ventana móvil RF02 y sincronización transaccional RF23 |
| `04_PRUEBAS_SMOKE_ROLLBACK.sql` | Comprobar tablas, catálogos, restricciones y cálculos; termina con ROLLBACK |
| `05_MODELO_LOGICO.md` | Modelo relacional de PK/FK y diagrama Mermaid |
| `06_TRANSACCIONES_SPRING_BOOT.md` | Recetas de transacciones, autenticación, idempotencia y responsabilidades del backend |

## Ejecución en pgAdmin / PostgreSQL

1. Conéctate a la BD administrativa `postgres`. Si **no existe** `mycar_mvp`, ejecuta el script `00_CREAR_BASE_OPCIONAL.sql` (una sola vez). Si ya existe, **sáltalo**.
2. Cambia la conexión de pgAdmin a **`mycar_mvp`**, antes de ejecutar los demás scripts.
3. **Opción recomendada:** ejecuta solo **`09_INSTALACION_TODO_EN_UNO.sql`** (esquema + catálogos + lógica en **una única transacción**). Si prefieres pasos separados, ejecuta `01_ESQUEMA_MYCAR.sql`, luego `02_CATALOGOS_INICIALES.sql` y `03_LOGICA_BD.sql`. **No uses ambas opciones**.
4. Ejecuta **`04_PRUEBAS_SMOKE_ROLLBACK.sql`**. Esperar mensaje «PRUEBAS SMOKE OK». El final `ROLLBACK` evita conservar los datos de prueba.
5. Verifica:

```sql
SELECT table_name FROM information_schema.tables
WHERE table_schema='mycar' AND table_type='BASE TABLE'
ORDER BY table_name;
```

Deberían aparecer **21 tablas**.

## Advertencias de implementación

- **No hemos ejecutado estos scripts en un servidor PostgreSQL en esta sesión**: las verificaciones practicadas son estáticas, no certifican la ejecución real. Si pgAdmin muestra un error concreto, conservar el mensaje exacto y la línea para corregirlo.
- La `CREATE DATABASE` opcional NO es idempotente. No ejecutarla si la base ya existe. La creación de tablas es solo para instalación nueva, no sobre una BD con datos.
- Si se usa Flyway, convertir los scripts en migraciones versionadas y ejecutarlos **por Flyway**, no simultáneamente a mano.
- El núcleo de PostgreSQL garantiza FKs/índices/validaciones; **Spring Boot debe implementar el alta transaccional, autenticación, firma de equivalencia SHA-256, idempotencia y cupo Free bajo bloqueo**. Leer `06_TRANSACCIONES_SPRING_BOOT.md`.
- El script usa `BIGINT` para identificadores; `UUID` para idOperacion/instalación; fechas `TIMESTAMPTZ` y fechas de negocio locales `DATE` America/Lima; sin PostGIS porque estos 11 RF no almacenan geodatos.
- `ANULADA`, `REALIZADA` en alertas y `PREMIUM` en el tipo de plan son valores de compatibilidad; **no se activan los módulos futuros**.
- Los códigos `COMBUSTION`, `GASOLINA`, `DIESEL`, `MINERAL`, `SEMISINTETICO`, `SINTETICO` son opciones semilla propuestas para desarrollo; los de `FREE`, `PARTICULAR`, `TAXI` y siete tipos de mantenimiento sí están respaldados por los acuerdos. La lista final de opciones vehiculares puede ajustarse sin alterar el modelo.
- El modelo lógico no incluye `vehiculo.km_actual` ni `vehiculo.id_lectura_km_actual`: ambos eran caché/puntero opcional y se omitieron para evitar duplicar la fuente canónica de lecturas. `vehiculo.version_km` sí se conserva. 

## Estado para congelamiento

Este paquete es una **versión inicial de DDL candidata**, no un SQL «ya validado y congelado». La prueba smoke verifica algunos invariantes; aún faltan pruebas transaccionales de concurrencia real (sesiones por instalación, POST simultáneos Free, reintentos idempotentes, RF17/RF22 y primera VENCIDA/ANULADA RF23) ejecutadas en PostgreSQL y desde el backend.
