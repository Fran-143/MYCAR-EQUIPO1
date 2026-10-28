# RF50: Inicio y navegación común de MyCar

Entrega local de Francesca; base main RF18 6fc5ad4 y bloque de sesión RF08/RF09 previamente integrado.
Fuentes: prompt explícito de Inicio/navegación y hoja RF050-EF1 del archivo RF - FINALES.xlsx.
Backend real del repositorio: Spring Boot con Java 21. No se convierte a Kotlin por la descripción del prompt.
No se modifica la BD, migraciones, documentos funcionales congelados ni contratos REST existentes.

## Pantallas y rutas

MainActivity aloja una única navegación Compose, siguiendo el patrón de estado existente.

| Acceso | Ruta/componentes reutilizados | Comportamiento |
|---|---|---|
| Inicio | Resumen RF50 | Abre al iniciar app y después de login; real o estado sin sesión |
| Vehículos | VehiculosRoute, ListadoVehiculosRoute, RegistroVehiculoRoute | Sesión compartida, lista y registro RF08/RF09 existentes |
| Mantenimientos | FlujoHistorialMantenimiento, EntradaHistorialMantenimiento, EntradaRegistroMantenimiento | Consulta RF18 y registro RF17 existentes con la misma sesión |
| Alertas | Pantalla temporal | Próximamente; sin programación, sincronización ni avisos |
| Perfil | Pantalla temporal secundaria | Próximamente; Atrás vuelve al origen |
| Notificaciones | Pantalla temporal secundaria | Próximamente; sin contador inventado ni gestión |
| Ver vehículo | Reserva de navegación secundaria | RF10 no existe en la base; Próximamente, sin duplicar un detalle |
| Actualizar kilometraje | Reserva de navegación secundaria | RF14 no existe en la base; Próximamente, sin duplicar validaciones |

La barra inferior indica Inicio / Vehículos / Mantenimientos / Alertas y permanece en secciones principales.
Se oculta en pantallas secundarias y formularios de registro para conservar sus confirmaciones al salir.
Atrás de Android conserva los manejadores de formulario existentes y retorna a Inicio desde las pestañas.
No se apilan copias de una pestaña. La selección y el resumen se conservan en un VM privado por ciclo de cuenta.
El formulario RF17 conserva sus validaciones y el mecanismo idempotente de Nayu.

## Datos del resumen

- RF50 consulta GET /api/v1/vehiculos?filtro=ACTIVO. No incluye inactivos en el selector.
- Al cargar exige exactamente un principal activo cuando hay activos. Si hay cero o varios muestra
  inconsistencia; nunca elige ni corrige uno arbitrariamente. Sin activos muestra un estado vacío.
- Seleccionar otro vehículo cambia solo el resumen local. No cambia es_principal ni llama RF13.
- La lectura actual y fecha provienen del contexto RF17 existente, cuando está disponible; el km de
  RF09 sirve como dato real parcial si no se puede consultar la fecha. Nunca se inventa una fecha.
- El último mantenimiento se obtiene del historial RF18 del mismo id, excluye ANULADO y ordena por
  fecha de servicio y luego ID descendente. Su km histórico no sustituye el kilometraje actual.
- Cambiar selección invalida respuestas anteriores; un resultado tardío no mezcla vehículos.
- Fallo de red y lista vacía son estados distintos. Historial no disponible no se muestra como cero servicios.
- Marca/modelo/placa/km de la app vienen del backend. Las fixtures Carlos/Toyota/58 240 del Preview
  viven exclusivamente en src/debug y en pruebas; no son datos de producción ni se insertan en BD.

El contrato actual de auth/identidad no proporciona nombre ni foto del usuario. Se muestra «Hola» y un icono
hasta contar con esos datos, sin cambiar las respuestas de Elsa ni obtener un nombre del correo.
No hay contrato de fotos de vehículo en el MVP: se reutiliza la ilustración predeterminada existente.
La campana está visible pero no muestra badge, cero ni un total sin una consulta real.

## Límite de RF50 frente al alcance pedido

La hoja RF050-EF1 incluye reconciliación defensiva de alertas/notificaciones, registro de trazabilidad y
contador global, además del resumen visual. El prompt del usuario excluye expresamente implementar ahora
la lógica completa de alertas y notificaciones. Por eso esas secciones son temporales y NO se declara RF50
completo: están implementados el resumen disponible, selección y navegación; los bloques 4/6 y datos de
perfil/imagen faltantes siguen pendientes de sus módulos. No se crea otro motor de alertas ni se calculan
pendientes a partir de mantenimientos REALIZADOS. No se muestra «sin pendientes» como un hecho no consultado.

## Integración mínima de la sesión común

Se reutilizan SessionBearerFilter, SessionAuthentication.Principal, CredencialesSesion y el Retrofit autenticado
existentes. Un adaptador pequeño conecta el puerto IdentidadMantenimiento y su cadena de seguridad RF17,
solo cuando mycar.rf17.api-habilitada=true. RF18 mantiene su cadena de main y su propiedad de habilitación.
No cambia ningún DTO, URL, parámetro, validación, transacción ni regla de mantenimiento.

Las tres pestañas usan un único SesionVehiculosViewModel de MainActivity. El Activity antiguo de acceso
redirige a MainActivity para evitar dos contextos de cuenta. Un 401 en cualquier cliente compartido invalida
solo ese ciclo y pide login; callbacks de un ciclo anterior no cierran una sesión nueva.

Al compartir RF17, su archivo pendiente se inyecta como una instancia por ciclo. Se conserva privado, fuera
de backup, por servidor/cuenta. Un archivo heredado se copia y verifica antes de retirar la copia antigua.
Cerrar invalida el almacén antes de eliminarlo para impedir que una respuesta tardía lo recree. Una expiración
conserva la solicitud para reautenticar la misma cuenta; el cierre explícito borra RF08 y RF17.
Los SavedStateHandle de formularios de este host viven solo en memoria y se limpian al terminar el ciclo.
Las firmas opcionales añadidas a las entradas de Nayu conservan sus valores anteriores por defecto.

## Ejecutar localmente

Tras aplicar, detener el backend anterior con Ctrl+C y arrancarlo otra vez con el auxiliar actualizado:

    Set-Location 'C:\Proyectos\MyCarEquipo1'
    powershell -NoProfile -ExecutionPolicy Bypass -File '.\scripts\iniciar_backend_integracion_local.ps1'

Ese auxiliar conserva el puerto 8081 y la clave externa; ahora habilita RF17 y RF18 únicamente en el proceso
local de integración. No publica claves, no altera .env ni define una configuración productiva de despliegue.
Con backend viejo sin reiniciar los endpoints pueden seguir deshabilitados y no aparecerá el historial real.
La creación de una nueva cuenta sigue requiriendo la versión legal aprobada por Elsa; no se inventa una.

En Android Studio ejecutar app con la actividad predeterminada y el emulador operativo. Si estaba instalada
una APK anterior, Run app instala la nueva; abrir un icono viejo no prueba que se haya actualizado.

## Pruebas repetibles

Desde backend, Docker Desktop operativo:

    py -3 ..\scripts\test_rf08_api.py --incluir-rf09 --incluir-sesion

Incluye ahora dos casos adicionales en la integración existente: misma sesión registra vehículo y servicio
RF17 y lo lee mediante RF18; logout revoca también el acceso a los dos módulos. BD desechable.

Desde android:

    .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
    .\gradlew.bat :app:connectedDebugAndroidTest

Las nuevas unitarias cubren principal, activos, selección, vacíos, errores, último servicio no anulado,
consulta de lectura real, fechas, navegación y respuestas fuera de orden. Las instrumentadas cubren la
navegación de Inicio, perfil/alertas, entrada real de vehículos y limpieza del archivo RF17.
El Preview InicioPreview muestra el diseño y fuente grande usando fixtures exclusivamente DEBUG.

## Recorrido manual

1. Abrir app: Inicio sin datos inventados y barra con las cuatro secciones.
2. Perfil/campana/Alertas: Próximamente. Atrás vuelve a Inicio o al origen sin duplicados.
3. Vehículos: login real existente, tras autenticarse vuelve al resumen de Inicio.
4. Sin activos: ir a Vehículos y registrar con RF08; volver a Inicio y cargar su placa/km reales.
5. Elegir otro activo, si existe: cambiar resumen sin modificar principal. Inactivos solo en Vehículos.
6. Mantenimientos: historial real del vehículo, registro existente RF17 y regreso a lista/Inicio.
7. Último servicio realizado: cotejar fecha, km histórico e ID con RF18; km actual permanece independiente.
8. Cortar red: mensaje de error o bloque no disponible, sin datos inventados ni falso vacío.
9. Cerrar sesión desde Vehículos: diálogo, cancelar conserva sesión, confirmar limpia ambos módulos.
10. Login de otra cuenta: no ver datos/borradores anteriores. Rotación no pierde el VM autenticado.

Los botones RF10/RF14 son reservas de navegación mientras no haya una implementación existente.
No se agregan perfil editable, notificaciones reales, RF22/RF23, fotos, Premium, SOAT ni otras funciones.
No hay commit, push ni merge automático en la rama de aplicación; el paquete se revisa localmente.
