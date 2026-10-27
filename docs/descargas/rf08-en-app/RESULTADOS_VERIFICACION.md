# Resultados — RF08 al abrir la app

Fecha: 27/10/2026. Base main: e65c90de70461774ad8ffc82093f7d6dad50a80a (PR #7 integrada).

## Comprobaciones ejecutadas

- :app:assembleDebug: PASÓ. Generó APK con los nuevos archivos DEBUG y los Previews.
- :app:testDebugUnitTest: PASÓ. 47 pruebas, 0 fallos, 0 errores y 0 omitidas.
  - 18 de validación de formulario, 6 de repositorio, 22 de coordinador y 1 de ejemplo existente.
- :app:processReleaseMainManifest: PASÓ.
- Manifiestos fusionados: PASÓ. DEBUG tiene un único launcher,
  com.mycar.app.debug.RegistroVehiculoDemoActivity. RELEASE conserva un único launcher,
  com.mycar.app.MainActivity, sin declarar la actividad de demostración.
- Inspección del APK con aapt2 dump badging: PASÓ. La actividad de launcher es
  com.mycar.app.debug.RegistroVehiculoDemoActivity; paquete com.mycar.app, target SDK 37.
- git diff --check: PASÓ.
- Cambios de producción, MainActivity principal, Gradle, backend y BD: ninguno.

## Entorno y límites

Compilación en Linux con AGP 9.4.1, Gradle 9.6.0, SDK 37.0 y Temurin JDK 21.0.8.
Solo en la copia de verificación se apartó temporalmente la selección automática de JDK 25;
se restauró idéntica al original. El paquete no incluye ajustes de herramientas o dependencias.

No se ejecutó emulador ni se probó la interacción visual/instrumentada. Esas comprobaciones
se harán en el Android Studio del equipo siguiendo la guía.
Las 47 pruebas son las existentes de lógica y repositorio: no se presentan como pruebas de navegación UI.
No se ejecutó lint en este bloque; siguen pendientes los 2 errores preexistentes documentados
sobre includeSubdomains en la configuración de red DEBUG.
No se ejecutó backend, autenticación, health real ni registros PostgreSQL.
Este formulario de demostración no realiza peticiones de vehículos ni anuncia registros confirmados.
