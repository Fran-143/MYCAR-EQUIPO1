# Verificación del bloque sesión → vehículos

Base main: 032aef221e796fad68cc2772fe98b7a6b9333ba9. Fecha: 27/10/2026.

| Comprobación | Resultado |
|---|---|
| PostgreSQL 17, restricciones/migraciones V1–V5 | 42 pruebas, 0 fallos/errores/omitidas |
| Servicios, validadores, MVC RF08/RF09 e integración nueva RF02–vehículos | 215 pruebas, 0 fallos/errores/omitidas |
| Regresión RF01 de Elsa | 45 pruebas aprobadas |
| Regresión RF02 de Elsa | 71 pruebas aprobadas |
| Regresión RF06 de Elsa | 31 pruebas aprobadas |
| Contexto Spring y arranque HTTP | PASÓ; health UP; cinco rutas existentes rechazan anónimos |
| Pruebas unitarias Android | 66 pruebas, 0 fallos/errores/omitidas |
| APK DEBUG y APK de pruebas | Compilan |
| APK RELEASE con R8 | Compila; no equivale a desplegar/configurar para clientes |
| Lint DEBUG | 0 errores; 15 advertencias previas, ninguna del módulo nuevo de sesión |
| Sintaxis PowerShell de los tres auxiliares | Parser PowerShell 7.4.6: válida |
| Instalador APLICAR.ps1 | Ejecutado en clon aislado: 30 archivos con hashes iguales |
| Arranque/registro PowerShell | Ejecutados con Maven/HTTP simulados: clave estable, entorno restaurado y JSON UTF-8 correcto |
| git diff --check | Sin errores |

La integración nueva usa MockMvc con TODA la cadena Spring Security y PostgreSQL reales, sin inyectar un
principal simulado para el login. Comprueba: identidad obtenida del servidor, catálogo/cupo, registro real,
replay idempotente, consulta de operación, Free, listado por propietario, cuenta ajena, pruebas ausentes y
falsificadas, revocación individual, instalación independiente, sesión expirada, cuenta suspendida y refresh rotado.
La prueba de adaptador adicional rechaza Authentication no autenticado, texto y principal ausente.

Las pruebas Android nuevas comprueban renovación previa al envío, no renovación para cerrar, respuesta tardía
que no resucita credenciales, respuesta perdida de refresh que no reintenta una credencial consumida, sesión
inconsistente, toString sin secretos y guardián que impide recrear un pendiente tras el cierre.

Se añadió y compiló una prueba instrumentada de almacenamiento real por cuenta y limpieza. NO se ejecutó
connectedDebugAndroidTest aquí: no hay emulador disponible. Debe correr en la laptop de Francesca, junto con
las tres pruebas instrumentadas anteriores. Las tres pasaron en su laptop para PR #9; ese resultado no certifica
la prueba nueva ni el recorrido visual real de este paquete.

Todos los contenedores de prueba fueron propios y desechables. No se usaron .env ni bases del equipo.
No se modificaron migraciones, código productivo de autenticación/registro/cierre de Elsa ni mantenimiento
productivo de Nayu. MainActivity y HomeScreen solo reciben un acceso opcional al nuevo Activity.
No hay claves ni contraseñas reales en el paquete; las contraseñas de tests son fixtures explícitas.

Durante el desarrollo, una prueba de expiración usó un nombre de columna inexistente: se corrigió contra
V2 antes de aprobar resultados. Una primera ejecución de lint falló internamente al analizar fuentes que se
estaban actualizando; se repitió con fuentes estables y terminó con 0 errores. No se deshabilitó ninguna regla.

Los auxiliares de arranque/registro se ejecutaron con PowerShell 7.4.6 en Linux y dependencias Maven/HTTP
simuladas para no crear datos reales. No se certifica aquí una ejecución de Windows/Android Studio. La política legal vigente, el recorrido con cuenta
local, offline/rotación en el emulador y la revisión del contrato auxiliar de identidad siguen pendientes del equipo.
El host mínimo no sustituye Perfil/RF01/RF02 completos ni habilita la API de mantenimiento.
