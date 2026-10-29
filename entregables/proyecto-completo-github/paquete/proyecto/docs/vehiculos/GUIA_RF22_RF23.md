# RF22 / RF23 — Guía para el equipo (ejecutar en local)

Rama: **`feat/mantenimientos-rf22-rf23`**

Esta guía sirve para que cualquiera del equipo pueda clonar/actualizar la rama, levantar backend + Postgres y probar **programar alertas (RF22)** y **Mis alertas (RF23)** en el emulador.

## Qué incluye esta rama

| Pieza | Qué hace |
|-------|----------|
| **RF22** | Programar alerta por tiempo, kilometraje o **ambos** (una sola alerta) |
| **RF23** | Listar Mis alertas con 6 filtros (sin chip Anuladas) y sincronizar estados |
| **RF14** | Actualizar km desde detalle o mantenimiento (sin botón de km dentro de RF22) |
| **V6 Flyway** | POR_TIEMPO puede guardarse sin lectura de km; sync RF23 solo exige km si hay alertas KM/AMBOS |
| **G89** | Si cambian fecha o km entre revisión y guardar → reconfirmación (no guardado silencioso) |
| **Idempotencia** | Mismo `idOperacion` en reintento; cliente recupera operación si la respuesta se pierde |

Regla de negocio clave (Excel consolidado RF22!G61):  
`fecha_objetivo = fecha_actual + intervalo` · `km_objetivo = último_km_RF08/RF14 + intervalo_km` · el último mantenimiento **no** es base de cálculo · Ambos = **una** alerta y manda lo que ocurra primero.

## 1. Traer la rama

```powershell
cd C:\Proyectos\MyCarEquipo1
git fetch origin
git checkout feat/mantenimientos-rf22-rf23
git pull origin feat/mantenimientos-rf22-rf23
```

Si aún no existe localmente:

```powershell
git fetch origin
git checkout -b feat/mantenimientos-rf22-rf23 origin/feat/mantenimientos-rf22-rf23
```

## 2. Requisitos

- Docker Desktop encendido  
- JDK / Android Studio (JBR)  
- Emulador Android (recomendado API 37, p. ej. PHONEBETA)  
- Archivo `.env` en la raíz del repo (no se sube a Git; pedirlo al equipo si no lo tienes)

## 3. Base de datos + backend

```powershell
cd C:\Proyectos\MyCarEquipo1
docker compose up -d --wait postgres
.\scripts\iniciar_backend_integracion_local.ps1
```

- El script arranca el backend en el puerto **8081** con JWT local y RF17/RF18/**RF22** activos.  
- Flyway aplicará migraciones hasta **V6** (`V6__rf22_tiempo_sin_lectura.sql`).  
- Health: `http://localhost:8081/actuator/health` → `{"status":"UP"}`.

Tras cambios Java: Ctrl+C y vuelve a ejecutar el script.

> La app Android en **debug** usa por defecto `http://10.0.2.2:8081/` (emulador → PC). No confundir con Postgres (5432) ni con un backend arrancado a mano en 8080 sin el script.

## 4. Android

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
cd C:\Proyectos\MyCarEquipo1\android
.\gradlew.bat :app:installDebug
```

O abre el módulo `android` en Android Studio, elige el emulador y pulsa **Run**.

Permiso de red local (API 37):

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell pm grant com.mycar.app android.permission.ACCESS_LOCAL_NETWORK
```

## 5. Dónde probar en la app

| Qué | Dónde |
|-----|--------|
| **RF23** | Barra inferior → **Alertas** |
| **RF22** | Alertas → **Programar alerta** |
| **RF14 (detalle)** | Vehículo → detalle → **Actualizar kilometraje** |
| **RF14 (mantenimiento)** | Mantenimientos → al registrar → actualizar km (conserva borrador) |

### Checklist manual corto

1. **Por tiempo:** 6 meses → fecha objetivo = hoy (Lima) + 6 meses; **no** exige km.  
2. **Por km:** intervalo 5000 desde el **último km registrado** (no desde el km del último mantenimiento).  
3. **Ambos:** aparecen tiempo + km; se crea **una** alerta; atención con el primer criterio.  
4. Pantalla de **revisión/resumen** antes de confirmar (exigida por RF22 G68–G71).  
5. Si cambias km (RF14) entre revisar y confirmar → debe pedir **reconfirmación** (G89).  
6. Segunda alerta equivalente activa → conflicto; tras REALIZADA/ANULADA se puede programar otro ciclo.  
7. Abrir RF14 no guarda solo; cancelar no escribe lectura.

## 6. Pruebas automáticas

```powershell
cd C:\Proyectos\MyCarEquipo1\backend
.\mvnw.cmd test "-Dtest=ProgramarAlertaServiceReglasTest,HuellaProgramarAlertaTest,AlertasControllerTest"

cd C:\Proyectos\MyCarEquipo1\android
.\gradlew.bat :app:testDebugUnitTest --tests "com.mycar.app.features.alertas.*" --tests "com.mycar.app.features.vehiculos.ActualizarKilometrajeReaperturaTest"
```

Pruebas de BD desechables (opcional, no usar la BD personal compartida):

```powershell
# Solo si el equipo tiene configurado mycar.db.tests + JDBC mycar_mvp_test_*
.\mvnw.cmd test "-Dtest=MvpDatabaseTest" "-Dmycar.db.tests=true"
```

## 7. SQL de solo lectura (comprobar alertas)

```sql
SELECT a.id_alerta, tm.codigo, a.criterio, a.estado,
       a.fecha_base, a.fecha_objetivo, a.km_base, a.intervalo_km, a.km_objetivo
FROM mycar.alerta a
JOIN mycar.tipo_mantenimiento tm ON tm.id_tipo_mantenimiento = a.id_tipo_mantenimiento
WHERE a.id_vehiculo = :idVehiculo
ORDER BY a.creada_utc DESC;
```

## 8. Documentación de requisitos

| Recurso | Uso |
|---------|-----|
| `docs/requisitos/MyCar_RF01_RF65_CONSOLIDADO_AUDITADO_20261028_PLMERU.xlsx` | Hojas **RF22**, **RF23**, **RF24** (reglas G…) |
| `docs/requisitos/02_29_Alternativos_Nayu_MVP_Conciliados.xlsx` | Escenarios alternativos |
| `docs/db/mvp/diccionario_columnas.csv` | Meses calendario, umbrales de estado |
| `docs/vehiculos/CORRECCION_RF22_RF14_REAPERTURA.md` | Notas de reapertura formulario / RF14 |

## Problemas frecuentes

| Síntoma | Qué revisar |
|---------|-------------|
| «Autenticación no disponible» / login 503 | Backend sin script de integración (falta JWT). Usa `iniciar_backend_integracion_local.ps1`. |
| App no conecta | Puerto **8081** + `10.0.2.2` + permiso `ACCESS_LOCAL_NETWORK`. |
| «No se pudo validar la sesión» | Docker/Postgres y Spring Boot UP. |
| Alertas vacías / sin vehículos | Entrar antes a Inicio o Mantenimientos para cargar contexto; vehículo **ACTIVO**. |
| Flyway / V6 | Reiniciar backend tras pull para aplicar la migración. |

## Autoría

Implementación asistida en Cursor sobre el trabajo del equipo (sesión, RF10, RF14, BD MVP). Revisión humana del PR recomendada antes de merge a `main`.
