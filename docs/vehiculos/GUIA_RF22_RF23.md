# RF22 / RF23 — Guía local (Windows)

Rama de trabajo: **`feat/mantenimientos-rf22-rf23`** (solo local hasta revisión).

## Dependencias de ramas

| Rama | Contenido necesario |
|------|---------------------|
| `feat/integracion-sesion-vehiculos` | Sesión compartida Elsa + RF06 |
| `feat/detalle-vehiculo-rf10` | Detalle vehículo |
| `feat/actualizar-kilometraje-rf14` | RF14 real |
| **Esta rama** | RF22 programar alerta, RF23 Mis alertas, RF14 desde mantenimiento/detalle |

## 1. Entorno

- Docker + PostgreSQL (`docker compose up -d --wait postgres`)
- Backend puerto **8081** con RF17, RF18 y **RF22** habilitados:

```powershell
cd C:\Proyectos\MyCarEquipo1
.\scripts\iniciar_backend_integracion_local.ps1
```

Reinicia el backend (Ctrl+C y volver a ejecutar el script) tras cambios Java.

## 2. Android

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
cd C:\Proyectos\MyCarEquipo1\android
.\gradlew.bat :app:installDebug
```

Emulador API 37:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell pm grant com.mycar.app android.permission.ACCESS_LOCAL_NETWORK
```

## 3. Dónde probar

| RF | Pantalla |
|----|----------|
| **RF23** | Barra inferior → **Alertas** (sincroniza y lista) |
| **RF22** | Alertas → **Programar alerta** |
| **RF14 desde RF10** | Detalle vehículo → **Actualizar kilometraje** (formulario real) |
| **RF14 desde RF17** | Mantenimientos → registrar → **Actualizar kilometraje vigente** (conserva borrador) |

## 4. Comprobaciones manuales

1. **Detalle → RF14**: abrir no envía nada; cancelar vuelve al detalle; guardar actualiza km en detalle.
2. **Mantenimiento → RF14**: borrador intacto al cancelar; lecturas/km vigente se refrescan al confirmar.
3. **Cambio de aceite**: con aceite en vehículo, botón **Usar aceite registrado** propone repuesto (editable).
4. **RF22**: programar alerta por km; segunda equivalente → conflicto.
5. **RF23**: entrar a Alertas ejecuta sincronización (contador `alertasSincronizadas`).

## 5. SQL solo lectura

```sql
SELECT a.id_alerta, tm.codigo, a.estado, a.km_objetivo, a.fecha_objetivo, a.firma_equivalencia
FROM mycar.alerta a
JOIN mycar.tipo_mantenimiento tm ON tm.id_tipo_mantenimiento = a.id_tipo_mantenimiento
WHERE a.id_vehiculo = :idVehiculo
ORDER BY a.creada_utc DESC;
```

## 6. Pruebas automáticas

```powershell
cd C:\Proyectos\MyCarEquipo1\backend
.\mvnw.cmd test "-Dtest=AlertasControllerTest"

cd C:\Proyectos\MyCarEquipo1\android
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

## Fuentes de reglas (Excel / docs)

| RF | Nombre (docs Nayu) | Fuentes leídas |
|----|-------------------|----------------|
| **RF22** | Programar alerta manual (11 EA) | `docs/requisitos/06_TRANSACCIONES_SPRING_BOOT.md`, `05_MODELO_LOGICO.md`, `docs/db/mvp/diccionario_columnas.csv`, `04_Reglas_Negocio_Nayu_Conciliadas.docx` (síntesis), triggers V4 |
| **RF23** | Sincronizar Mis Alertas (6 EA) | Misma carpeta + función `mycar.sincronizar_alertas_vehiculo` |

Hojas Excel `.xlsx`: no se pudo ejecutar Python en este equipo; los escenarios EA se tomaron de la síntesis Nayu y SQL de referencia en `docs/requisitos/`.

## IA y autoría

Implementación asistida por IA en Cursor; reutiliza backend Nayu/Fran (RF17, BD RF22/RF23 en Flyway) y RF14/RF10 del equipo. Revisión humana pendiente antes de publicar.
