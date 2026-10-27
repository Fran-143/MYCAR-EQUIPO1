# Integración Android RF17

Estado: pantalla, estado, borrador y repositorio IMPLEMENTADOS. La compilación es una verificación
de código, no una prueba del flujo en dispositivo ni una conexión real con la sesión.

## Archivos y acceso

El código nuevo vive en com.mycar.app.features.mantenimiento. Se conserva namespace com.mycar.app.
La entrada es RegistrarMantenimientoRoute; no se modifica MainActivity, HomeScreen ni la navegación
de Francesca. Por ello el inicio actual del APK sigue mostrando la pantalla compartida original:
el acceso a RF17 desde Historial debe insertarlo el host con su contrato de navegación.

La ruta recibe un MantenimientoRepository, una clave opaca de cuenta para aislar la solicitud
pendiente, idVehiculoInicial opcional, onCerrar y callbacks opcionales de RF14/inicio de sesión.
Debe alojarse en la entrada de navegación propia de RF17 para mantener su ViewModel al ir a RF14.
El host debe retirar ese ámbito al cerrar sesión; no reutilizarlo entre cuentas.

## Cliente HTTP compartido

No se crea Retrofit.Builder, otro ApiClient, otro login ni un almacén de tokens.
MantenimientoApiAdapter.desde(retrofitCompartidoAutenticado) crea el repositorio sobre la MISMA
instancia Retrofit configurada por el equipo. La factoría existente de main solo expone healthApi;
el equipo debe proporcionar/exponer su instancia autenticada o inyectar MantenimientoApi.
Ese cambio compartido y la sesión de Elsa no se inventan ni se aplican unilateralmente.

Ejemplo de conexión que el host debe adaptar a sus objetos reales:

```kotlin
val repository = MantenimientoApiAdapter.desde(retrofitCompartidoAutenticado)
RegistrarMantenimientoRoute(
    repository = repository,
    claveCuentaBorrador = claveOpacaDeLaCuentaActual,
    idVehiculoInicial = vehiculoSeleccionado,
    onCerrar = volverAlFlujoDeOrigen,
    onActualizarKm = abrirRf14ConVehiculo,
    onSesionRequerida = abrirLoginCompartido
)
```

Los nombres del ejemplo representan dependencias pendientes; no son servicios simulados.
No se introducen rutas de navegación compartidas por conjetura. Si onActualizarKm no se suministra,
la acción se muestra deshabilitada. Al regresar al ciclo ON_RESUME se recargan las referencias,
sin sustituir el kilometraje histórico del borrador por el nuevo odómetro.

## Flujo implementado

- Carga vehículos propios activos y tipos desde la API; principal inicial cuando no se recibe otro.
- Muestra kilometraje/aceite de referencia y Sin taller, en solo lectura. Fechas de lectura en Lima.
- Selección de fecha y lectura o kilometraje manual, OTRO condicional, observaciones y costo opcional.
- Editor de repuestos manuales: agregar/editar/quitar, duplicados permitidos y lista final al guardar.
- Próxima fecha y km opcionales; creación de alerta visible como Próximamente y deshabilitada.
- Validación de campos e importes sin convertir dinero a Float/Double. Coma decimal de entrada se
  convierte a número JSON; no se admiten separadores de miles.
- Confirmación solo ante respuesta consistente con el UUID y los valores de la solicitud.
- Cancelar antes del envío descarta el borrador local y no llama una API de guardado/cancelación.
- Si cambia el vehículo con datos ya ingresados se solicita confirmación para empezar otro borrador.

El borrador temporal usa SavedStateHandle. Antes de enviar se conserva una copia exacta con su UUID
en un AtomicFile privado de Android, separado por clave opaca de cuenta, sin tokens/contraseñas.
Un resultado incierto bloquea la edición y la cancelación destructiva. Permite salir conservando
la solicitud, consultar su estado y, SOLO con el registro idempotente disponible según servidor,
reintentar manualmente el MISMO payload/UUID. No hay reintentos automáticos ni creación de otro UUID
para encubrir un COMMIT incierto. Una respuesta NO_ENCONTRADA no libera por sí sola la solicitud.

Las operaciones de edición previas al envío pueden renovar el UUID local. Tras enviar queda congelado.
Repuestos retirados del borrador no llegan al POST. Confirmar RF14 es independiente y RF17 no lo revierte.

## Contratos pendientes

Sesión/token/cuenta de Elsa y Retrofit autenticado; ruta y retorno real de RF14; acceso desde Historial;
contrato REST/idempotencia confirmado. No se implementan RF18, RF22, RF23 ni pantallas falsas de login/RF14.
La pantalla admite preparar borrador, pero no presenta como disponible un guardado bloqueado en servidor.

## Compilar en el proyecto existente

Mantener Gradle wrapper, versiones y configuración existentes. Se requiere el SDK declarado en el
proyecto (compileSdk 37) y JDK 25 para el daemon, conforme a la configuración ya existente.
En Windows, desde android: `.\gradlew.bat :app:compileDebugKotlin`.
No se proporciona local.properties: Android Studio debe resolver el SDK de tu PC.
Se conservó API_BASE_URL. El entorno de desarrollo actual usa 127.0.0.1:8080; la configuración de
conexión/reverse que ya use el equipo debe mantenerse. Android nunca accede a PostgreSQL directamente.
