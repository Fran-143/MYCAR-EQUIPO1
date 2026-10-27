# Ver RF08 al abrir la app desde Android Studio

Base: main e65c90d, que integra el PR #7. Fecha: 27/10/2026.

## Comportamiento

En la variante DEBUG, el icono MyCar abre directamente el formulario Agregar vehículo.
Se puede escribir, desplegar secciones, elegir opciones y pulsar Guardar para comprobar
las validaciones locales existentes. La franja «Modo de prueba · no guarda vehículos en la BD»
permanece visible. Una validación correcta no anuncia que se haya creado un vehículo.

Volver, Cancelar o Atrás desde el formulario llevan a Mis vehículos de prueba. Desde allí, Volver lleva al menú MyCar · Pruebas. Desde allí se puede
abrir otra vez el formulario o la pantalla existente de Comprobar conexión. Atrás desde el menú
sale de la actividad. Comprobar conexión utiliza la pantalla de health real, no una respuesta simulada.

El formulario no requiere servidor, Docker ni sesión para esta prueba de interfaz.
La comprobación del servidor sí requiere que backend y conexión estén disponibles.

## Separación de la ejecución real

El manifiesto DEBUG sustituye únicamente el punto de entrada del launcher. Registra
RegistroVehiculoDemoActivity y elimina el intent-filter de launcher de MainActivity en esa variante,
para que haya un único icono. El manifiesto principal no se modifica.

La variante RELEASE conserva MainActivity y no incluye la actividad, los catálogos de ejemplo
ni el formulario de demostración DEBUG. Esta entrega no convierte release en una versión lista
para clientes: el login, la navegación autenticada y el servidor de entrega siguen pendientes.

Los datos de ejemplo se comparten entre Preview y emulador. No se crea un repositorio de
vehículos, ni un usuario, ni un token ficticio. No se llama al POST RF08 ni se escribe en PostgreSQL.
El formulario real RegistroVehiculoScreen y sus validaciones se reutilizan sin modificarlos.
Los valores escritos se conservan al recrear la actividad; al salir del formulario al menú,
esta demostración comienza de nuevo cuando se vuelve a abrir.

## Ejecutar en Android Studio

1. Sincroniza el proyecto android si el IDE lo pide.
2. Selecciona la variante debug y la configuración de ejecución app.
3. Enciende el emulador y espera a que termine de iniciar.
4. Pulsa Run (triángulo verde) para instalar el código actualizado.
5. Debe abrirse Agregar vehículo con la franja Modo de prueba.
6. Guardar vacío debe estar deshabilitado. Escribe valores inválidos y comprueba errores, secciones, Otro y Eléctrico.
7. Pulsa Volver para abrir Mis vehículos. Prueba el listado vacío, Mostrar ejemplo, filtros y búsqueda. Vuelve al menú para comprobar la conexión.

Si sigue abriendo la bienvenida anterior: Run > Edit Configurations > app > Launch Options.
Selecciona Default Activity, en lugar de una actividad específica MainActivity, y vuelve a ejecutar.
No basta con abrir una instalación vieja desde el icono: primero hay que ejecutar Run para actualizarla.

## Comandos de verificación en Windows

Desde C:\Proyectos\MyCarEquipo1\android:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:processReleaseMainManifest
```

La revisión de launcher debug/release y los límites de comprobación se documentan en el informe del paquete.
La revisión visual, el teclado y la navegación deben comprobarse también en el emulador del equipo.

## Integración futura

Cuando esté disponible la rama de Elsa, conectar RegistroVehiculoRoute con el repositorio
autenticado, almacenamiento por cuenta y navegación reales. Retirar el launcher de demostración
como parte de esa integración. No sustituir esta demostración por un idUsuario fijo.
La entrada DEBUG no requiere migraciones ni cambios de Gradle o dependencias. El bloque RF09 añade consultas de backend por separado.
