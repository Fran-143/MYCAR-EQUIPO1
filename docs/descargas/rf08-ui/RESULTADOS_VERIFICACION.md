# Verificación — Frontend RF08

Fecha: 27/10/2026. Base: main f4fd10758cfbb970798141a550e9715114b6018a.

## Resultados ejecutados

- :app:assembleDebug: PASÓ; compila código principal, recursos y Previews DEBUG.
- :app:testDebugUnitTest: PASÓ, 47 pruebas, 0 fallos y 0 errores.
- com.mycar.app.ExampleUnitTest: 1 pruebas, 0 fallos, 0 errores.
- com.mycar.app.features.vehiculos.CoordinadorRegistroVehiculoTest: 22 pruebas, 0 fallos, 0 errores.
- com.mycar.app.features.vehiculos.RemoteRegistroVehiculoRepositoryTest: 6 pruebas, 0 fallos, 0 errores.
- com.mycar.app.features.vehiculos.ValidadorFormularioRegistroTest: 18 pruebas, 0 fallos, 0 errores.
- git diff --check: PASÓ.
- :app:lintDebug: NO PASÓ por 2 errores preexistentes en
  android/app/src/debug/res/xml/debug_network_security_config.xml, líneas 6 y 7:
  falta includeSubdomains en los dominios 127.0.0.1 y 10.0.2.2.
  Además informa 13 advertencias en archivos preexistentes (dependencias, recursos y manifiesto).
  Ninguno de los diagnósticos apunta a los archivos de este paquete.
  La configuración de red tiene el mismo contenido que main; no se suprimió lint ni se cambió una baseline.

## Entorno y límites

Compilado con AGP 9.4.1, Gradle 9.6.0, Android SDK 37.0 y JDK 21 en una copia temporal.
Para la comprobación se apartó temporalmente el archivo de selección automática de JDK 25,
ya que su descarga no estaba disponible en este entorno. Se restauró idéntico al original.
El paquete NO incluye cambios de Gradle, de SDK ni de JDK.

No se ejecutó emulador, render visual de los Previews ni pruebas instrumentadas de interfaz.
Las pruebas de dominio/repositorio/coordinador verifican su comportamiento existente, no el aspecto visual.
La revisión visual e interacción de tamaños, teclado y temas se hará en Android Studio/dispositivo del equipo.
La navegación y una sesión autenticada real siguen dependiendo de la integración de Elsa.
No se probaron subida de fotos ni endpoints nuevos: no se implementan en esta entrega.
No se ejecutaron ni cambiaron BD, migraciones o backend.
