# MyCar: sesión real de Elsa conectada con vehículos

Base: main 032aef221e796fad68cc2772fe98b7a6b9333ba9 (PR #9 integrado).
Rama de Francesca: feat/integracion-sesion-vehiculos. No se publican cambios de aplicación ni se integra main.
Este paquete se entrega en una rama de descargas: no hagas un pull request desde la rama docs/descarga-rf08-android.

## 1. Aplicar el bloque completo

Descarga MyCar_Integracion_Sesion_Vehiculos.zip, pulsa clic derecho → Extraer todo.
Abre la carpeta extraída que contiene APLICAR.ps1, MANIFIESTO.json y ARCHIVOS.
No copies el ZIP dentro del repositorio ni reapliques paquetes RF08/RF09 antiguos.

En PowerShell, usando la ruta real de esa carpeta:

```powershell
$paqueteSesionMyCar = "$env:USERPROFILE\Downloads\MyCar_Integracion_Sesion_Vehiculos"
powershell -NoProfile -ExecutionPolicy Bypass -File "$paqueteSesionMyCar\APLICAR.ps1"
```

Si Windows añadió otra carpeta al extraer, ajusta únicamente $paqueteSesionMyCar.
El auxiliar exige la rama indicada, HEAD base y árbol limpio; comprueba todos los archivos antes de copiar y
respalda los existentes fuera del repositorio. Si detecta otra base o cambios locales, se detiene sin reemplazos.
No modifica .env, credenciales, BD ni migraciones. Tampoco hace git add, commit, push ni merge.

## 2. Verificar en tu laptop

Docker Desktop encendido. Desde backend:

```powershell
Set-Location "C:\Proyectos\MyCarEquipo1\backend"
py -3 ..\scripts\test_rf08_api.py --incluir-rf09 --incluir-sesion
```

Este ejecutor usa un PostgreSQL desechable: no altera tu BD mycar_mvp.
Desde android:

```powershell
Set-Location "C:\Proyectos\MyCarEquipo1\android"
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

La última orden necesita el emulador encendido. Añade una prueba de aislamiento y limpieza de archivos Android.
Cualquier fallo se revisa antes de subir. No publiques logs completos que pudieran incluir datos sensibles.

## 3. Probar contra tu BD local

Consulta docs/vehiculos/INTEGRACION_SESION_VEHICULOS.md para el recorrido completo y sus límites.
En una terminal dedicada, desde raíz:

```powershell
Set-Location "C:\Proyectos\MyCarEquipo1"
docker compose up -d --wait postgres
.\scripts\iniciar_backend_integracion_local.ps1
```

Deja esa terminal abierta. Usa 8081 para coincidir con http://10.0.2.2:8081/ que YA tiene tu build DEBUG.
El auxiliar genera una clave exclusivamente local fuera de Git y la reutiliza. No la envíes al chat ni al equipo.
No hace falta adb reverse con 10.0.2.2. Comprueba http://127.0.0.1:8081/actuator/health en el PC.

En Android Studio ejecuta app. Sal del formulario de demostración hasta MyCar · Pruebas y pulsa
**Vehículos · sesión real**. Inicia sesión con una cuenta existente, abre Mis vehículos y registra si tiene cupo.
La demostración anterior continúa sin guardar en BD; no la confundas con la nueva entrada real.
Si no hay cuentas todavía, RF01 necesita una versión legal aprobada que debe aportar Elsa. No inventes ese valor.
El documento explica cómo registrar una cuenta por el endpoint real sin escribir contraseñas en archivos.

Prueba cerrar sesión/cancelar, volver con otra cuenta, recuperar operación tras una interrupción y comprobar
que no reaparecen borradores después de un cierre explícito. Una cuenta Free solo admite un vehículo.

## 4. Preparar tu commit y pull request cuando las pruebas pasen

El manifiesto contiene SOLO los archivos de esta entrega. Para agregarlos desde cualquier carpeta:

```powershell
$manifiestoSesionMyCar = Get-Content "$paqueteSesionMyCar\MANIFIESTO.json" -Raw | ConvertFrom-Json
$rutasSesionMyCar = @($manifiestoSesionMyCar.archivos | ForEach-Object { $_.ruta })
git -C "C:\Proyectos\MyCarEquipo1" add -- $rutasSesionMyCar
git -C "C:\Proyectos\MyCarEquipo1" diff --cached --check
git --no-pager -C "C:\Proyectos\MyCarEquipo1" diff --cached --stat
git -C "C:\Proyectos\MyCarEquipo1" commit -m "feat: conectar sesion real con registro y listado de vehiculos"
git -C "C:\Proyectos\MyCarEquipo1" push -u origin feat/integracion-sesion-vehiculos
```

No uses git add . ni subas archivos .env, claves, respaldos, APK, target o build.
Crear PR hacia main y dejarlo para revisión; no hacer merge automático.
Título propuesto: «Conectar sesión real RF02/RF06 con vehículos RF08/RF09».
Descripción propuesta:

> Conecta las rutas de vehículos al filtro de sesión de Elsa y añade una consulta protegida de identidad
> para separar almacenamiento local por cuenta. Android incorpora un host de acceso real, renovación en
> memoria y cierre local con limpieza RF08 antes del intento de revocación remota.
>
> Conserva los contratos de auth y las migraciones V1–V5. No completa Perfil/RF06, registro visual RF01,
> integración de mantenimiento ni despliegue público. Ver resultados adjuntos y pruebas del emulador.

## Mensaje para Elsa

«Ya integré tu backend de main con las rutas RF08/RF09 en mi rama feat/integracion-sesion-vehiculos.
Reutilizo SessionBearerFilter y Principal.userId(); no cambié tus endpoints ni tus respuestas de auth.
Propongo GET /api/v1/vehiculos/identidad, protegido, para obtener la cuenta validada y separar archivos locales.
El host Android de integración mantiene tokens solo en memoria; ante 401 conserva la operación por cuenta;
en cierre explícito limpia RF08 antes de intentar logout, sin renovar ni persistir la revocación.
Falta acoplar tu UI definitiva y los almacenes sensibles de los otros módulos. ¿Qué versión de privacidad
aprobada debo configurar para poder registrar una cuenta local por RF01? No me envíes claves ni contraseñas.»
