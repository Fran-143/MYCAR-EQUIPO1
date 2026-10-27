# Verificación — Bloque RF08/RF09

Fecha: 27/10/2026. Base: main e65c90de70461774ad8ffc82093f7d6dad50a80a.

## Backend (Java 21 + PostgreSQL 17 real y desechable)

Ejecutor: scripts/test_rf08_api.py --incluir-rf09.
- MvpDatabaseTest: 42 pruebas, 0 fallos/errores/omitidas.
- Validadores, servicios, consultas y MVC RF08/RF09: 180 pruebas, 0 fallos/errores/omitidas.
- De esas 180, 16 son nuevas de RF09: 11 con PostgreSQL y 5 de contrato MVC.
- Las nuevas pruebas PG cubren propiedad, tarjetas, vacío, activo/inactivo, lectura vigente,
  búsqueda, contadores, literalidad de comodines/SQL, filtro inválido, cuenta suspendida,
  lectura sin plan, nulo sin cero inventado, principal primero y ausencia de escrituras.
- BackendApplicationTests: PASÓ.
- HTTP /actuator/health: UP.
- Cinco rutas de vehículos rechazan peticiones anónimas (401/403), incluido el nuevo GET del listado.
- Contenedor aleatorio de pruebas y datos retirados por su ejecutor. No usa .env ni Compose local.

## Android

- :app:assembleDebug: PASÓ.
- :app:testDebugUnitTest: 56 pruebas, 0 fallos/errores/omitidas.
  18 de validadores, 24 de coordinador RF08, 6 de repositorio RF08, 7 de repositorio RF09 y 1 existente.
- :app:compileDebugAndroidTestKotlin: PASÓ. Compila 3 pruebas instrumentadas nuevas, NO ejecutadas.
- :app:lintDebug: PASÓ. 0 errores/fatales; 13 advertencias no bloqueantes de manifiesto,
  recursos existentes y dependencias. Se corrigieron includeSubdomains y la declaración DEBUG de exported.
- :app:processReleaseMainManifest: PASÓ.
- Manifiestos inspeccionados: DEBUG solo tiene RegistroVehiculoDemoActivity como launcher;
  RELEASE solo tiene MainActivity y no declara la actividad de demostración.
- git diff --check: PASÓ.

## Límites y pendientes

No se ejecutó emulador ni connectedDebugAndroidTest. La interacción visual y el flujo completo
con sesión real siguen pendientes. Las pruebas MVC usan una identidad simulada solo en test;
no prueban JWT, instalación, renovación, revocación o cierre de sesión de Elsa.
No se integró su backend porque todavía no está disponible su rama.
El host autenticado debe suministrar dependencias a VehiculosRoute y acordar/conectar la limpieza RF06.
RF10, RF14, fotos, SOAT y TIVE no se implementan en este bloque.
Los ejemplos se limitan a DEBUG y no insertan vehículos ni lecturas.
No se modifican tablas, datos, migraciones V1–V5, pom, dependencias ni Gradle del proyecto.

## Entorno

Verificado con Temurin JDK 21.0.8, Maven 3.10.0, PostgreSQL 17, AGP 9.4.1,
Gradle 9.6.0 y SDK Android 37.0.
En la copia temporal se apartó la selección automática de JDK 25 solo para ejecutar Gradle
con el JDK disponible. Se restauró idéntica al original; el paquete no contiene ese ajuste.
La configuración de proxy/cachés de herramientas tampoco se distribuye.
