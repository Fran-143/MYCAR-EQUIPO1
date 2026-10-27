# Resolución PR #9 — comprobaciones

Base main: ce609aed68637c05522667f44aaa1269b4c45576.
Base PR #9: 90f5a3edf0f3a67261a999b015704e3647e25202.
Fecha: 27/10/2026.

Se reprodujo el merge en un worktree temporal. Solo generó los conflictos anunciados:
AndroidManifest.xml DEBUG y libs.versions.toml.
Se combinó el permiso ACCESS_LOCAL_NETWORK de Nayu con el launcher DEBUG de Francesca.
Se conservaron Espresso 3.7.0, JUnit 1.3.0 y runner 1.7.0.
Se añadió en el menú DEBUG un acceso Inicio y mantenimientos, que abre MainActivity del mismo paquete.
MainActivity, HomeScreen y los módulos de mantenimiento Android/backend se verificaron idénticos a main.
No se modifican módulos de Nayu, BD, migraciones, datos o autenticación.

Resultados de la combinación resuelta:
- assembleDebug y assembleDebugAndroidTest: PASARON.
- testDebugUnitTest: 56 pruebas, 0 fallos/errores/omitidas.
- lintDebug: 0 errores/fatales, 15 advertencias no bloqueantes.
- processReleaseMainManifest: PASÓ.
- Manifiesto DEBUG: un solo launcher RegistroVehiculoDemoActivity y permiso de red local presente.
- Manifiesto RELEASE: un solo launcher MainActivity; permiso DEBUG no añadido a release.
- Ejecutor backend: 42 pruebas BD y 208 validaciones/servicios/consultas/MVC, sin fallos/errores/omitidas.
- BackendApplicationTests y health HTTP UP: PASARON.
- Las cinco rutas de vehículos rechazan anónimos (401/403).
- Cuatro clases de servicios de Nayu: 48 pruebas adicionales, sin fallos/errores/omitidas.
- Contenedor PostgreSQL 17 y datos desechables retirados por el ejecutor.
- No quedan conflictos sin resolver en la copia temporal y git diff --cached --check no informa errores.

No se ejecutó emulador ni connectedDebugAndroidTest aquí. Falta ejecutar las pruebas de interfaz
con la combinación resuelta y comprobar la navegación en el dispositivo del equipo.
Las pruebas anteriores no certifican la futura autenticación de Elsa ni una aceptación completa RF17.
No se hizo commit/push en la rama de Francesca ni merge del PR #9 a main.
La selección automática de JDK 25 se apartó temporalmente para verificar con JDK 21 y se restauró.
SDK 37.0, AGP 9.4.1, Gradle 9.6.0; Maven 3.10.0 y PostgreSQL 17.
