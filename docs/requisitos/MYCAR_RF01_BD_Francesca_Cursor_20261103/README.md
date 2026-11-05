# MyCar — entrega de persistencia RF01 para Francesca / Cursor

Fecha: 2026-11-03 (America/Bogota). Fuente local: rama `codex/elsa-continuacion`,
HEAD `84641c7e041984b287cea1ed90445ecb770ec75b`. Los archivos nuevos de esta etapa NO están en ese commit:
son archivos locales sin seguimiento, inventariados en ESTADO.json.

## Qué entregar a Cursor

- `sql/V7__rf01_verificacion_aislada.sql`: copia byte a byte de la migración implementada; no una reconstrucción.
- `DICCIONARIO.md`: columnas/defaults/nulabilidad, PK/FK/UNIQUE/CHECK/índices extraídos del SQL exacto, más semántica.
- `RELACIONES_Y_REGLAS.md` y `relaciones.mmd`: cardinalidades, reglas realmente implementadas y diferencias/pendientes.
- `DEPENDENCIAS_BACKEND.md`: archivos y contratos que deben mantenerse compatibles.
- `INSTRUCCIONES_CURSOR.md`: procedimiento de revisión/integración y devolución a la autora.
- `referencia_backend/`: seis archivos productivos NUEVOS exactos, únicamente para contrastar nombres/SQL;
  este paquete NO solicita integrar ni activar el backend. No contiene fixtures ni claves reales.
- `ESTADO.json`, `FUENTES_SHA256.json`, `SHA256SUMS.txt`: inventario, procedencia, pruebas previas y hashes.

## Estado y alcance

Solo RF01 está en implementación interna aislada. RF03, RF07 y RF53 no están habilitados: todas las
finalidades tienen CHECK REGISTRO y usuario_contexto debe ser NULL. No asumir políticas idénticas futuras.
No existe registro_pendiente ni usuario provisional. No hay conexión a Gmail/SMTP ni otro proveedor real,
ni Turnstile real, endpoints nuevos o ejecución periódica de limpieza. El registro público anterior continúa;
la prevención de omitir la verificación queda para integrar el recorrido completo. RF01 no está terminado.
El bloqueo global por correo está implementado como política provisional aislada, pendiente de aprobación.

La migración fuente está en `backend/src/main/resources/db/rf01-verificacion/`.
La configuración existente solo carga `classpath:db/mvp`; por tanto este archivo aislado NO se activa por
su presencia. V7 se ejecutó antes únicamente en bases PostgreSQL desechables, luego retiradas.
La comprobación de aplicación realizada al preparar esta entrega figura en ESTADO.json; no se consultó
el main remoto ni ninguna base compartida. No interpretar el número V7 como disponible en el remoto actual.

Git conserva una modificación previa en `android/gradle.properties`; no se incluye su contenido.
No se incluyen .env, credenciales, volcados, datos de usuarios, fixtures ni logs completos.

## Pasos de integración (detalle en INSTRUCCIONES_CURSOR.md)

1. Inspeccionar main actualizado, historial Flyway y cualquier implementación equivalente.
2. Acordar versión y UNA ruta canónica. No crear tablas manualmente ni duplicar migraciones.
3. Revisar las diferencias señaladas sin corregir silenciosamente la copia recibida.
4. Preservar contratos del backend o entregar un mapa explícito de cambios.
5. Probar migración y compatibilidad en PostgreSQL nuevo desechable antes de una base de la app.
6. Devolver commit, ruta/versión canónica, pruebas y qué archivo aislado local se conserva o sustituye.

**Actualizar Git descarga archivos; no aplica automáticamente migraciones a PostgreSQL.** La aplicación
depende de la configuración efectiva de Flyway, el destino de conexión y una ejecución autorizada.

## Evidencia previa

La etapa anterior pasó 36 casos nuevos PostgreSQL y 44 existentes, sin fallos/errores/omitidos. Bases y rol
de pruebas se retiraron. Es evidencia de la copia local de esa etapa, no del main que Cursor vaya a revisar.
Al empaquetar no se reejecutaron migraciones ni suites. Las limitaciones de SQL/backend se documentan;
los 80 casos no demuestran todas las combinaciones posibles de escritura SQL directa.
