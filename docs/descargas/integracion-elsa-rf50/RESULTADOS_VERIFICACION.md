# Verificación — integración Elsa / RF50

Fecha: 28/10/2026, America/Lima.
Base entrante main 529927a; autenticación 24e0c08. Base local de contenido: resolución RF18 + RF50 de Francesca.
Paquete: 19 archivos del proyecto (14 reemplazos y 5 nuevos), con manifiesto y respaldo previo.

| Comprobación | Resultado |
|---|---|
| Android :app:testDebugUnitTest | 200 pruebas, 0 fallos/errores/omitidas |
| Regresión anterior RF50 y módulos | 103 pruebas incluidas en el total |
| Autenticación de Elsa, incluida corrección RETRY | 80 pruebas incluidas en el total |
| Nuevas pruebas de integración de sesión | 17 pruebas incluidas en el total |
| DEBUG APK y APK de pruebas | Compilan |
| Kotlin RELEASE | Compila |
| Lint DEBUG | 0 errores, 17 advertencias |
| Launcher del manifiesto DEBUG combinado | Sólo com.mycar.app.MainActivity |
| Instalador PowerShell | Ejecutado en copia aislada con los mismos dos conflictos |
| Fuentes instaladas | 19 archivos con hashes iguales; árbol Git completo coincide con las fuentes verificadas |
| Guardias del instalador | Rechaza cambios no guardados, base preparada distinta y segunda aplicación, sin reemplazos |
| git diff --cached --check / conflictos restantes | Sin errores / ninguno |

Los 17 casos nuevos cubren: autorización con contexto validado, renovación concurrente única y persistida,
rechazo del contexto anterior tras cambio de cuenta, limpieza antes de retirar credenciales y revocar,
fallo/reintento de limpieza, indisponibilidad temporal sin 401 falso, refresh revocado, regreso del segundo plano,
limpieza real de archivos temporales propios y conservación de archivos ajenos, cierre de escritores,
HTTP POST único con Bearer, rechazo 401 sin reenvío, fallo temporal sin envío, contexto retirado y cliente cancelado.
Los cinco casos HTTP usan un servidor local efímero en JVM, sin cuentas reales ni PostgreSQL personal.

No se repitieron aquí las pruebas de backend: este paquete no modifica código del servidor, scripts SQL ni
migraciones. La base RF50 ya había comprobado el JWT compartido entre RF08/RF17/RF18 con PostgreSQL desechable.
La guía incluye el ejecutor de regresión backend para la laptop de Francesca.

No hay emulador en el entorno de entrega. Las pruebas instrumentadas compilan pero no se ejecutaron aquí;
tampoco se confirmó el recorrido real de Francesca ni la persistencia de un vehículo personal.
El instalador se probó con PowerShell 7 en Linux, usando sintaxis compatible con Windows PowerShell 5.1;
la ejecución final en Windows sigue pendiente de la usuaria.

El ajuste final de una línea en blanco al final de SesionVehiculosActivity.kt se verificó con diff --check;
no cambia la compilación ni el comportamiento probado. Todos los archivos del ZIP coinciden con la fuente final.

No se publican commits de aplicación, no se hace merge hacia main y no se incluyen .env ni credenciales.
Sólo el ZIP y su documentación se entregan en docs/descarga-rf08-android, rama de descargas sin PR hacia main.
