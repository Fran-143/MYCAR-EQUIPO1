# Corrección rápida del PR #5

Este paquete corrige los dos errores reproducidos en la revisión. Mantiene la rama feat/vehiculos-mvp, el PR #5, la BD y las migraciones. No toca Android ni la autenticación pendiente.

## 1. Copiar cinco archivos

Descomprime Correccion_PR5_MyCar.zip. Abre ARCHIVOS y copia las carpetas backend y docs dentro de:

`C:\Proyectos\MyCarEquipo1`

Esta vez acepta **Reemplazar los archivos en el destino** para los cuatro archivos indicados abajo. Los anteriores ya están guardados en tu último commit. Si editaste alguno personalmente después del commit, conserva esa edición antes de reemplazar.

Reemplazos:

- backend/src/main/java/backend/vehiculos/dto/DatosRegistroVehiculo.java
- backend/src/main/java/backend/vehiculos/controller/ErroresVehiculosController.java
- backend/src/test/java/backend/vehiculos/controller/VehiculosControllerTest.java
- docs/vehiculos/API_RF08_PROPUESTA_20261027.md

Archivo nuevo:

- backend/src/main/java/backend/vehiculos/dto/NumerosEnterosRegistroJson.java

No copies el ZIP ni los informes a la raíz del repositorio: solo el contenido de ARCHIVOS. No cambies de rama ni copies en la rama de descarga.

## 2. Probar todo el backend en un solo comando

Con Docker Desktop encendido:

```powershell
cd C:\Proyectos\MyCarEquipo1\backend
py -3 ..\scripts\test_rf08_api.py
```

Esperamos:

- 42 pruebas de BD sin fallos.
- 164 pruebas de validación/servicios/consultas/controlador sin fallos.
- BackendApplicationTests PASÓ, health UP y las cuatro peticiones anónimas rechazadas.
- Contenedor temporal retirado.

No necesitas ejecutar Gradle ni modificar la BD real. No se agregó otra migración.

## 3. Actualizar el mismo PR

Solo después de que las pruebas terminen correctamente:

```powershell
cd C:\Proyectos\MyCarEquipo1
git branch --show-current
```

Debe mostrar feat/vehiculos-mvp. En esa rama:

```powershell
git add -- backend/src/main/java/backend/vehiculos/dto/DatosRegistroVehiculo.java backend/src/main/java/backend/vehiculos/dto/NumerosEnterosRegistroJson.java backend/src/main/java/backend/vehiculos/controller/ErroresVehiculosController.java backend/src/test/java/backend/vehiculos/controller/VehiculosControllerTest.java docs/vehiculos/API_RF08_PROPUESTA_20261027.md

git diff --cached --check
git diff --cached --stat
```

Si --check no muestra errores:

```powershell
git commit -m "fix: rechazar decimales en RF08 y responder HTTP 415"
git push origin feat/vehiculos-mvp
git status --short --branch
```

El push actualiza automáticamente el PR #5. No crees otro PR ni hagas merge a main todavía: primero revisaremos el commit corregido. Publicar la corrección en la rama permite revisarla, sin incorporarla a main.

## Qué cambia

Año, kilometraje e IDs del DTO RF08 aceptan números enteros JSON dentro de rango. Rechazan decimales (también 2020.0), cadenas numéricas y desbordamientos antes de ejecutar el servicio. Los null opcionales siguen permitidos. La configuración JSON de otros módulos y la serialización/hash de solicitudes válidas no se modifican.

Un Content-Type no soportado devuelve HTTP 415 con FORMATO_NO_ADMITIDO, en lugar de 500. Los 26 casos MVC originales se conservan y se añaden 21 casos. ORIGINALES_SHA256.json identifica los archivos anteriores normalizando finales de línea; SHA256.json permite comprobar los archivos entregados.
