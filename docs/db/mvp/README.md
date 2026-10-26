# PostgreSQL del MVP de MyCar

Integración sobre `main` (`90d821c0b132efcd2e575472dcee6d3f4c855bff`), Java 21,
Spring Boot 3.5.16, PostgreSQL 17 y Flyway. Alcance: RF01, RF02, RF06, RF08,
RF09, RF10, RF14, RF17, RF18, RF22 y RF23. No incluye nuevos endpoints REST.

## Fuente y trazabilidad

La fuente es el paquete `MyCar_CODEX_GUIA_PASO_A_PASO_NUEVA_DESCARGA.zip`, con
diccionario y documentación conciliada de Elsa, Francesca y Nayu del 26/10/2026.
Se cotejaron sus 21 entradas del manifiesto SHA-256, sin discrepancias.

- [Diccionario aprobado original, sin modificar](DICCIONARIO_MVP_APROBADO.xlsx).
- [Extracto del diccionario con referencias de celdas](diccionario_columnas.csv).
- [Diagrama lógico](modelo_logico.mmd).
- [Huellas de las fuentes consultadas](fuentes_sha256.json).
- [Correcciones y decisiones técnicas](CORRECCIONES.md).
- [Pruebas, resultados y límites](RESULTADOS_PRUEBAS.md).

Los diagramas funcionales EF/EA congelados permanecen sin cambios. El SQL de
referencia no se declara probado por el hecho de estar adjunto: las migraciones
adaptadas se ejecutaron en PostgreSQL real y tienen pruebas de regresión.

## Migraciones oficiales

| Versión | Archivo | Contenido |
|---|---|---|
| V1 | `V1__esquema_mvp.sql` | Archivo existente, intacto. Solo crea el esquema. |
| V2 | `V2__tablas_mvp.sql` | 21 tablas, PK/FK, CHECK e índices. Rechaza un esquema con relaciones de negocio sin tocar sus datos. Admite la tabla técnica de Flyway. |
| V3 | `V3__catalogos_mvp.sql` | Free, Particular/Taxi, siete tipos de mantenimiento y opciones vehiculares iniciales. |
| V4 | `V4__funciones_y_triggers_mvp.sql` | Cálculo de estados, integridad entre tablas, historial, login y sincronización RF23. |

Ruta: `backend/src/main/resources/db/mvp`. No hay `CREATE DATABASE`, `DROP`,
limpieza de tablas, baseline automático ni instalador todo-en-uno. Solo V1 crea
el esquema; los demás archivos se ejecutan dentro de las transacciones de Flyway.
V2–V4 no deben ejecutarse también manualmente desde pgAdmin.

V1 en Git conserva SHA-256:
`65e5a7bef83ce744310d3540fb4055b382819c8e3ae70ce8ec6b5535142e319b`.
La prueba tolera únicamente la representación CRLF/LF de un checkout Windows.

## Las 21 tablas

1. usuario
2. plan
3. suscripcion
4. preferencia_notificacion
5. config_recordatorio_km_usuario
6. sesion
7. intento_login_fallido
8. uso_vehiculo
9. tipo_motor
10. combustible
11. tipo_aceite
12. tipo_mantenimiento
13. vehiculo
14. operacion_idempotente
15. lectura_kilometraje
16. config_recordatorio_km
17. mantenimiento
18. repuesto
19. mantenimiento_repuesto
20. alerta
21. historial_alerta

`flyway_schema_history` no cuenta como tabla del negocio. `vehiculo.km_actual`
e `id_lectura_km_actual` son derivados opcionales del diccionario; se conserva el
modelo normalizado consultando la última lectura, sin una caché duplicada.

No hay notificaciones internas, dispositivos push, tablas de taller, TIVE,
licencia, SOAT, imágenes, pagos ni Premium operativo. Las preferencias iniciales
de RF01 se conservan como datos de soporte sin habilitar esos módulos.

## Actualizar una instalación que solo tiene V1

1. Revisar el destino en `.env`: BD `mycar_mvp`, usuario/puerto y contraseña local.
2. Conservar un respaldo antes de actualizar una instalación compartida.
3. Actualizar la rama acordada e iniciar Docker Desktop.
4. Desde la raíz: `docker compose up -d --wait postgres`.
5. Desde `backend`: `./mvnw spring-boot:run` (Windows: `.\mvnw.cmd spring-boot:run`).
6. Revisar que Flyway aplica V2, V3 y V4, y que health devuelve `UP`.

El backend mantiene `ddl-auto=validate`, esquema `mycar` y la ubicación Flyway
existente. `MYCAR_ENV_FILE` permite elegir un archivo de configuración externo;
sin esa variable se conserva exactamente el comportamiento local `../.env`.
El ejecutor de pruebas utiliza esta opción para no leer las credenciales locales.

Si Flyway rechaza V2 por relaciones de negocio existentes, detener la actualización
y revisar la BD seleccionada. No borrar tablas, reparar checksums ni marcar V2
como aplicada para saltar la protección. Este cambio no transforma un modelo
anterior de 52 tablas ni adopta tablas creadas manualmente.

Cambiar POSTGRES_DB no crea una BD nueva en un volumen Docker ya inicializado.
La creación de una base de desarrollo es una tarea administrativa separada, no
parte de las migraciones.

## Comprobaciones de solo lectura

En Query Tool conectado a `mycar_mvp`:

```sql
SELECT current_database();

SELECT version, description, success
FROM mycar.flyway_schema_history
WHERE version IS NOT NULL
ORDER BY installed_rank;

SELECT count(*) AS tablas_negocio
FROM information_schema.tables
WHERE table_schema = 'mycar' AND table_type = 'BASE TABLE'
  AND table_name <> 'flyway_schema_history';

SELECT indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'mycar'
  AND indexname IN ('uq_suscripcion_activa_usuario', 'uq_sesion_vigente_instalacion',
                   'uq_vehiculo_principal_usuario', 'uq_alerta_equivalente_activa');
```

Esperado: V1–V4 correctas, 21 tablas y los cuatro índices parciales.

## Contrato que deben respetar los servicios Java

Estas son responsabilidades de implementación, no endpoints ya disponibles:

- **Identidad:** resolver idUsuario mediante sesión válida, verificar estado de
  cuenta y propiedad. Una FK o función SQL no autentica al cliente.
- **RF01:** una transacción para usuario, Free ACTIVA sin fin, preferencias y
  configuración global PAUSADO. El usuario nace USUARIO, sin sesión automática.
  El hash seguro y las validaciones del nombre/contraseña son del backend.
- **RF02:** bloquear usuario, verificar cuenta y restricción antes del password;
  una contraseña errónea llama `registrar_fallo_login`. Tras credenciales
  correctas, llamar `reiniciar_fallos_login` en la transacción del login. Los
  fallos se conservan como historial; el marcador separa el contador vigente.
- **Sesiones:** expirar/revocar la anterior de la misma instalación antes de
  insertar su reemplazo. Otras instalaciones siguen válidas. Autorizar solo
  VIGENTE, no revocada y con expiración futura. RF06 revoca únicamente la
  sesión presentada; no afirmar revocación remota si no se confirmó.
- **Idempotencia:** autenticar, reservar/bloquear idOperacion, comprobar usuario,
  tipo y hash canónico; si está CONFIRMADA devolver respuesta_json original
  antes de verificar cupo o versión. Un hash diferente produce conflicto.
- **Cupo:** solo en operación NUEVA bloquear usuario **FOR NO KEY UPDATE**,
  revalidar Free ACTIVA y contar todos los vehículos, incluso INACTIVOS. Es
  obligatorio serializar todos los caminos de alta por esa misma fila. Un
  CHECK de una fila no asegura un cupo entre filas.
- **RF08:** vehículo, principal inicial, lectura INICIAL, recordatorio
  INTERDIARIO/PAUSADO y resultado idempotente en una sola transacción.
- **RF14:** preview sin escritura. Al confirmar, bloquear vehículo, revalidar
  versión y exigir incremento; lectura, versión y resultado idempotente atómicos.
  No modificar alertas ni historial de alertas desde RF14.
- **RF17:** verificar fecha/km histórico, tipo ACTIVO y propiedad; guardar
  mantenimiento, repuestos y asociaciones en una transacción. No crear alertas
  ni marcar otras REALIZADA. La instantánea km_servicio no cambia con RF14.
- **RF22:** bloquear vehículo antes de revalidar referencia/lectura; objetivos
  desde fecha actual/última lectura, no desde el servicio histórico. Insertar
  alerta + historial inicial v0 + resultado idempotente, sin aviso interno.
- **RF23:** ejecutar `sincronizar_alertas_vehiculo(idVehiculo,idUsuarioDeSesion)`
  como comando explícito antes del GET de lista; actualiza estado/historial
  únicamente ante cambios reales. No se añadió motor autónomo ni push.

Cuando hagan falta ambos bloqueos, conservar orden usuario → vehículo. Se usa
NO KEY UPDATE sobre usuario porque las PK son inmutables y las FK de operaciones
ya pueden haber adquirido KEY SHARE. El FOR UPDATE del patrón candidato produjo
un interbloqueo real entre dos altas concurrentes; no copiar ese patrón.

`MvpDatabaseTest` tiene helpers que ensayan estos contratos SQL; no reemplazan
los servicios Java ni prueban autorización HTTP, DTO, passwords reales o UI.

## Normalización y equivalencia

`normalizar_servicio_otro` implementa NFKC, full casefold Unicode 15.0.0,
colapso de blancos y trim sin alterar el texto visible almacenado. La tabla de
conversión constante se obtuvo de `str.casefold()` de Python con Unicode 15.0.0;
no requiere extensión ni una tabla adicional. Usa la normalización NFKC de
PostgreSQL 17. Los vectores incluyen ancho completo, ß/ss, sigma final,
combinaciones de tildes y espacios.

El backend sigue calculando SHA-256 de payload/firma canónica: versión del
formato, tipo, criterio, objetivos efectivos o NULL y servicio OTRO normalizado
o NULL. No incluir estado, idAlerta o referencia histórica. Usar una serialización
inequívoca y probar los mismos vectores. El índice no puede detectar firmas
distintas inventadas para una misma solicitud: la API debe construirlas.

Los estados usan 10 % exacto con BIGINT, sin redondear; meses desde la fecha base
original; primera evidencia verificable de VENCIDA y 30 días calendario locales
America/Lima. AMBOS escoge la evidencia más antigua y, en empate, la temporal.

## Pruebas reproducibles

Requisitos: Docker, JDK 21 completo, Python 3 y acceso a Maven Central.

Desde la raíz:

```powershell
py -3 scripts/test_mvp_db.py
```

Linux/macOS:

```bash
python3 scripts/test_mvp_db.py
```

Se crea un contenedor PostgreSQL 17 único con contraseña aleatoria y puerto de
loopback dinámico. Se aplica V1 y después V2–V4, se ejecuta la suite JDBC/Flyway,
la prueba Spring existente y el backend empaquetado con health HTTP. Se comprueba
que no quedan fixtures en la BD principal de pruebas. Las carreras necesitan
commits reales y utilizan bases adicionales del mismo contenedor; el ejecutor
retira todas junto con su propio contenedor incluso si falla una prueba.

No usa `.env` ni el servicio/volumen Compose existente. Nunca ejecutar el perfil
de pruebas contra una BD de usuarios. El guard exige nombres
`mycar_mvp_test_*`, PostgreSQL 17 y un esquema inicialmente vacío.

El perfil Maven `db-mvp-tests` habilita `MvpDatabaseTest`; sin él esa clase se
omite. Su ejecución directa exige variables MYCAR_TEST_JDBC_URL,
MYCAR_TEST_USER y MYCAR_TEST_PASSWORD de un servidor desechable; el ejecutor
las configura sin publicar valores. Las opciones `--maven-settings` y
`--maven-repo` sirven para un proxy/caché externos sin cambiar archivos del repo.

`backend/src/test/resources/db/mvp/smoke_rollback.sql` es el diagnóstico candidato
adaptado, fuera de migraciones productivas. La suite ejecuta su DO dentro de una
transacción reversible. No usarlo como migración ni como instalador. Aunque sus
filas se revierten, PostgreSQL puede avanzar secuencias durante un diagnóstico.

Las fixtures históricas usan `SET LOCAL session_replication_role=replica`
exclusivamente en el contenedor aislado para representar altas válidas hechas
meses antes sin alterar el reloj. Las aserciones negativas y la sincronización
se ejecutan con triggers activos. No copiar esa preparación a la aplicación.

## Pendientes fuera de esta integración

Endpoints Java, autenticación real, generación/verificación de tokens,
hash canónico en servicios, transacciones de negocio, validación cronológica
completa del km manual de un servicio, UI y navegación Android. Las opciones
técnicas de motor/combustible/aceite tienen las etiquetas candidatas del paquete;
el equipo debe confirmarlas antes de publicar. No se define un catálogo de
palabras restringidas ni duraciones de sesiones que no estén aprobados.

Para cambios posteriores añadir V5 o siguientes, coordinados. No modificar
V1–V4 después de aplicarlas. No hacer merge sin revisión del equipo.
