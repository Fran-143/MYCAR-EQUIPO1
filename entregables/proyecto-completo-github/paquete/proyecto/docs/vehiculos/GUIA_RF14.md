# RF14 — Actualizar kilometraje (guía de ejecución)

Rama: `feat/actualizar-kilometraje-rf14`. Backend **8081**; Android Debug **`http://10.0.2.2:8081/`**.

## 1. Entorno

- Docker Desktop + PostgreSQL del proyecto (`compose.yml`).
- JDK 21 (Android Studio JBR sirve para Gradle).
- Emulador **API 37**: conceder red local (`adb shell pm grant com.mycar.app android.permission.ACCESS_LOCAL_NETWORK`).

## 2. PostgreSQL

```powershell
cd C:\Proyectos\MyCarEquipo1
docker compose up -d --wait postgres
```

## 3. Backend (dejar terminal abierta)

```powershell
cd C:\Proyectos\MyCarEquipo1
.\scripts\iniciar_backend_integracion_local.ps1
```

Por defecto el script habilita registro académico (`privacidad` **1.0**, igual que la app Debug). Más adelante en producción se usará la versión legal aprobada (`-VersionPrivacidad` explícita).

Comprobar:

```powershell
Invoke-RestMethod http://localhost:8081/actuator/health
```

## 4. Android

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
cd C:\Proyectos\MyCarEquipo1\android
.\gradlew.bat :app:installDebug
```

Run ▶ en emulador. Permiso red local + opcional:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" reverse tcp:8081 tcp:8081
```

## 5. Recorrido manual RF14

1. Inicia sesión.
2. **Inicio** → tarjeta vehículo → **Actualizar** (km).  
   O **Detalle de vehículo** → **Actualizar kilometraje**.
3. Lee el km vigente, escribe uno **mayor** (entero).
4. **Continuar** → vista previa → **Confirmar y guardar**.
5. **Continuar** en éxito → Inicio debe mostrar el km nuevo.

## 6. Comprobar en PostgreSQL (solo lectura)

Sustituye `:placa` por tu placa (ej. `A1B456`):

```sql
SELECT v.placa, l.valor_km, l.origen, l.fecha_lectura_utc, v.version_km
FROM mycar.vehiculo v
JOIN mycar.lectura_kilometraje l ON l.id_vehiculo = v.id_vehiculo
WHERE v.placa = 'A1B456'
ORDER BY l.fecha_lectura_utc DESC, l.id_lectura_km DESC
LIMIT 5;
```

Debe aparecer una fila `ACTUALIZACION` con el km nuevo arriba.

## 7. Errores habituales

| Síntoma | Causa |
|---------|--------|
| No se pudo conectar | Backend apagado, puerto 8081, permiso red local API 37 |
| Debe ser mayor al vigente | Regla RF14 / trigger PostgreSQL |
| Kilometraje cambió… | Otro dispositivo actualizó; pulsa Reintentar tras recargar |
| Vehículo inactivo | Solo ACTIVO permite escritura |

## 8. Pruebas automáticas

```powershell
cd C:\Proyectos\MyCarEquipo1\backend
.\mvnw.cmd test "-Dtest=ActualizarKilometrajeControllerTest"

$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
cd C:\Proyectos\MyCarEquipo1\android
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

PostgreSQL desechable (RF14 servicio): propiedad `-Dmycar.rf08.tests=true` con variables `MYCAR_TEST_JDBC_URL` (ver `scripts/test_rf08_backend.py`).

## Pendientes

- Fecha de lectura: la fija el servidor (`fecha_lectura_utc`), no el usuario.
- RF17 retorno con borrador: integración parcial vía refresco de km al volver a mantenimientos.
