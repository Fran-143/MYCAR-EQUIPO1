# Resolución PR #9 — comprobaciones con Nayu y Elsa

Base main final: e39c0c253511b288286c70f51179d3fa471313e8 (PR #6 y PR #8 integrados).
Base PR #9: 90f5a3edf0f3a67261a999b015704e3647e25202.
Fecha: 27/10/2026.

Se reprodujo el merge en un worktree temporal. Solo generó los conflictos anunciados:
AndroidManifest.xml DEBUG y libs.versions.toml.
Se combinó ACCESS_LOCAL_NETWORK de Nayu con el launcher DEBUG de Francesca.
Se conservaron Espresso 3.7.0, JUnit 1.3.0 y runner 1.7.0.
Se añadió en el menú DEBUG Inicio y mantenimientos, que abre MainActivity del mismo paquete.
Los módulos productivos de Elsa y Nayu permanecen iguales a main; no se modifican sus reglas ni datos.

Durante la revisión main avanzó de ce609ae a e39c0c2 incorporando el backend de Elsa.
La primera ejecución de los tests de servicios de vehículos falló al cargar HttpSecurity en
WebEnvironment.NONE. Se adaptaron 4 clases de tests propias a WebEnvironment.MOCK.
Se conserva la seguridad real; no se añade un adaptador ficticio ni se apagan filtros.

Resultados finales:
- Backend sobre main e39c0c2: 42 pruebas BD y 208 validadores/servicios/consultas/MVC,
  sin fallos/errores/omitidas. Incluye el nuevo contexto web de los tests de servicios.
- BackendApplicationTests, health HTTP UP y rechazo anónimo de cinco rutas: PASARON.
- Cuatro clases de servicios de Nayu: 48 pruebas adicionales, sin fallos/errores/omitidas.
- Los contenedores PostgreSQL 17 aleatorios y sus datos se retiraron por los ejecutores.
- Android: assembleDebug y assembleDebugAndroidTest PASARON; 56 pruebas JVM correctas.
- lintDebug: 0 errores/fatales, 15 advertencias no bloqueantes.
- processReleaseMainManifest: PASÓ.
- DEBUG tiene un solo launcher RegistroVehiculoDemoActivity y el permiso de red local.
- RELEASE conserva un solo launcher MainActivity, sin añadir el permiso DEBUG.
- Se ejecutó Android en la combinación con ce609ae y se verificó por comparación binaria que
  todos los archivos Android resultantes son idénticos en la combinación final e39c0c2.
  No se repitió la misma ejecución Android al no cambiar esos archivos.
- No quedan conflictos sin resolver en la copia temporal. git diff --cached --check correcto.

Límites:
No se ejecutó emulador ni connectedDebugAndroidTest aquí. Falta repetir las pruebas de interfaz
y comprobar la navegación en el dispositivo del equipo.
El código de Elsa ya está disponible en main, pero el adaptador y la cadena específica RF02–RF08
siguen pendientes; esta resolución no certifica una integración autenticada completa.
La prueba CatalogosVehiculoDaoTest se adaptó y compiló; su ejecución optativa independiente
no forma parte de las 208 pruebas seleccionadas por el ejecutor.
No se cambian BD, migraciones, credenciales, reglas funcionales ni autenticación productiva.
No se hizo commit/push en la rama de Francesca ni merge del PR #9 a main.
JDK 21 de verificación; selección automática de JDK 25 apartada temporalmente y restaurada.
SDK 37.0, AGP 9.4.1, Gradle 9.6.0; Maven 3.10.0 y PostgreSQL 17.
