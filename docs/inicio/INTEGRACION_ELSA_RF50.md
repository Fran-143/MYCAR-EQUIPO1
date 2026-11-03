# Integración de autenticación de Elsa con RF08/RF09 y navegación RF50

Base entrante: main 529927a (PR #11, incluye corrección RETRY 24e0c08).
Rama de Francesca: feat/integracion-sesion-vehiculos, con su RF50 guardado previamente.
Este cambio combina las dos partes; no añade migraciones, tablas ni reglas de negocio.

## Comportamiento

- Un solo launcher MainActivity tanto en Debug como Release. La demo conserva acceso explícito sin ser launcher.
- Elsa controla bienvenida, crear cuenta, login, validación/restauración cifrada, renovación y cierre.
- Después de autenticar aparece el Inicio RF50 y su barra Inicio / Vehículos / Mantenimientos / Alertas.
- Los módulos no vuelven a pedir credenciales: usan AuthRepository.authorization con un contexto de sesión
  validado, sin recibir refresh tokens ni crear otro propietario de las credenciales.
- El dueño real se obtiene del endpoint protegido GET /api/v1/vehiculos/identidad; no se inventa idUsuario
  ni se usa una identidad enviada por Android como autorización del backend.
- Vehículos y mantenimiento/historial reciben repositorios autenticados, clave estable servidor/cuenta,
  almacenamiento pendiente privado y un ViewModelStore por ciclo. RF50 usa esos mismos repositorios.
- Las renovaciones concurrentes se serializan con el Mutex de Elsa y persisten en su almacén cifrado.
  Nunca se reenvía automáticamente un POST de vehículo/mantenimiento tras un 401 o fallo de red.
- Una indisponibilidad temporal produce RETRY, no se convierte en una invalidación 401 artificial.
  El cierre local desde RETRY corregido por Elsa se conserva.
- Volver del segundo plano revalida sin publicar CHECKING innecesariamente y sin retirar una cuenta
  que aún está autenticada. Los ciclos retirados no autorizan operaciones de una cuenta posterior.
- FLAG_SECURE se mantiene después de salir de login/registro para proteger las pantallas privadas.

## Cierre RF06 y pendientes

Al confirmar cierre se retiran los clientes/escritores de RF08 y RF17 y se eliminan los pendientes locales
actuales y de ciclos/procesos anteriores, incluidos archivos temporales .bak/.new. Se limpian únicamente
los nombres privados de RF08/RF17; otros archivos y registros PostgreSQL no se borran.
Después se retiran las credenciales en el almacén de Elsa y se intenta una sola revocación remota.
Un fallo de limpieza produce CLEAR_FAILED e impide autenticar otra cuenta hasta completar la limpieza.
Sin conexión se confirma sólo cierre local, nunca una revocación remota no comprobada.

La expiración o invalidación natural retira clientes y ViewModels pero conserva las solicitudes pendientes
para la misma cuenta. El cierre explícito confirmado sí las elimina. Tras un resultado incierto, consultar
el listado/historial antes de crear otra operación; la idempotencia del backend no se modifica.

## Configuración y prueba real

DEBUG usa http://10.0.2.2:8081/ por defecto, compatible con el auxiliar local existente.
Se puede cambiar con -Pmycar.apiBaseUrl=... y recompilar. Release conserva la URL pendiente de despliegue.
Detener el backend viejo con Ctrl+C y volver a iniciarlo para cargar configuración actual.

Para probar también el registro RF01 con el documento académico local incluido por Elsa (versión 1.0):

```powershell
Set-Location 'C:\Proyectos\MyCarEquipo1'
docker compose up -d --wait postgres
powershell -NoProfile -ExecutionPolicy Bypass -File '.\scripts\iniciar_backend_integracion_local.ps1'
```

El script ya usa **1.0** por defecto (alineado con Debug). La versión 1.0 corresponde al documento local DEBUG del PR #11. No configura ni acredita una política pública de Release; éste exige su documento/versionado externo. No se modifica .env ni se imprime la clave JWT. Solo login con cuentas existentes: `-VersionPrivacidad ''`.

Android Studio: abrir android, seleccionar app y un emulador completamente iniciado, Run app.
Debe abrir Bienvenida de Elsa; al autenticar debe abrir RF50, sin un segundo login para módulos.
Verificar: crear cuenta real si corresponde, consultar/aceptar política, login, registrar vehículo válido,
consultar listado y revisar PostgreSQL, navegar a mantenimientos/historial y volver, cerrar sesión,
volver a entrar con la misma cuenta y verificar que sus registros guardados siguen presentes.

Consulta de sólo lectura desde la raíz del repositorio:

```powershell
docker compose exec -T postgres psql -U mycar -d mycar_mvp -c "SELECT id_vehiculo, placa, marca, modelo, estado_vehiculo FROM mycar.vehiculo ORDER BY id_vehiculo DESC LIMIT 10;"
```

## Límites

RF10 (detalle) y RF14 (actualizar kilometraje) siguen pendientes; Perfil/Notificaciones/Alertas conservan
Próximamente y no se añaden reglas ajenas. RF50 es el resumen disponible, no el RF completo de alertas.
No se inventan nombre, fotos, contadores, kilometraje ni mantenimientos como datos productivos.
Las clases de credenciales/API temporales anteriores se conservan sólo por compatibilidad de pruebas;
el host real ya no las usa para iniciar sesión, renovar ni cerrar.

Las pruebas de dispositivo requieren un emulador de pruebas sin sesión activa para los casos de bienvenida.
La compilación del APK de pruebas no equivale a ejecutar los tests conectados.
