# Resultado de la entrega Inicio RF50 / navegación

Revisión: 27/10/2026, America/Lima. Base de contenido: sesión RF08/RF09 + main RF18 6fc5ad4,
con la resolución de Inicio de cinco archivos aplicada. Paquete nuevo: 34 archivos (13 modificados, 21 nuevos).

| Verificación | Resultado |
|---|---|
| BD PostgreSQL 17, V1–V5 y restricciones existentes | 42 pruebas aprobadas |
| Servicios/validaciones/MVC/sesión real RF08/RF09, más RF17/RF18 compartidos | 217 pruebas aprobadas |
| Historial RF18: servicios, controlador, PostgreSQL y JWT real | 30 pruebas aprobadas |
| Regresión RF01/RF02/RF06 de Elsa | 45 + 71 + 31 = 147 pruebas aprobadas |
| Total backend | 436 pruebas, 0 fallos/errores/omitidas |
| Contexto Spring / HTTP | PASÓ, health UP y rutas de vehículos rechazan anónimos |
| Android unitarias, incluidas 17 nuevas de resumen/navegación | 103 pruebas, 0 fallos/errores/omitidas |
| APK DEBUG y APK de instrumentación | Compilan |
| Kotlin RELEASE / APK RELEASE con R8 | Compilan; sin desplegar ni cambiar la URL de marcador previa |
| Lint DEBUG final | 0 errores y 15 advertencias existentes |
| Instalador PowerShell | Ejecutado en copia aislada; 34 archivos iguales a las fuentes verificadas |
| Auxiliar de backend local | Probado con PowerShell y Maven/HTTP simulados; conserva clave externa y restaura entorno |
| git diff --check | Sin errores |

La integración real comprueba que un mismo login registra vehículo, abre contexto RF17, registra mantenimiento
y consulta RF18. Después de logout el mismo JWT no abre vehículos, RF17 ni RF18. No se inyecta identidad de
usuario en el controlador para saltar el filtro: se utilizan JWT y sesiones persistidas de Elsa.
Las pruebas de BD usan contenedores PostgreSQL 17 propios, aleatorios y desechables. Se retiraron al terminar;
no se utiliza mycar_mvp personal ni .env del equipo. No hay migraciones nuevas ni cambios de contratos REST.

Las unitarias nuevas comprueban:
- Principal inicial único, conservación de selección, rechazo de inactivos/duplicados/principal inconsistente.
- Vaciado real distinto de error; una cuenta sin vehículos no recibe un ejemplo de Carlos/Toyota.
- Último mantenimiento REALIZADO con desempate por ID, excluyendo anulados.
- Km actual y fecha de lectura independientes del km histórico de mantenimiento.
- Historial no disponible distinto de lista vacía confirmada.
- Respuesta tardía que no cambia el vehículo elegido.
- Cuatro pestañas y retorno de secundarias al origen sin apilar secciones.

Hay cuatro pruebas instrumentadas nuevas (tres de Inicio/navegación y una de almacenamiento RF17).
Se compilaron, pero NO se ejecutó connectedDebugAndroidTest aquí porque no hay emulador disponible.
Deben correrse en la laptop de Francesca. También quedan para su recorrido manual: selección visual,
registro desde la app, rotación, fuente grande, offline y limpieza al cambiar de cuenta.
No se sustituye esa verificación por un BUILD SUCCESS de compilación.

Las fixtures de diseño están solo en src/debug y pruebas; ninguna se usa en el arranque de producción.
No se generaron fotografías, nombres, contadores ni pendientes ficticios como si fueran datos del servidor.
El saludo es neutro porque los contratos disponibles no exponen nombre/foto; no se modificaron para simularlo.

RF50 permanece PARCIAL en su alcance documental completo: no se implementó reconciliación RF23, contador
global de notificaciones, fotos/perfil ni RF10/RF14. Son dependencias excluidas o todavía ausentes, presentadas
como pendientes en la UI y en docs/inicio/RF50_INICIO_Y_NAVEGACION.md. Alertas, Perfil y Notificaciones se
mantienen explícitamente en Próximamente conforme al prompt de esta entrega.

Solo se publican el ZIP y sus documentos en la rama de descargas autorizada. No se publica código en main,
no se hace merge automático de ningún PR y no se modifica la rama de aplicación de la laptop de Francesca.
