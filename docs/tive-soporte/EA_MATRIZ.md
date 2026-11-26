# Matriz EA — TIVE + Soporte VIN

Fuente: Excel BCE separados TIVE/SOPORTE. Estado inicial PENDIENTE hasta ejecución real.

Total escenarios indexados: **74** (esperado 74).

Notas de ejecución local (2026-11-17):
- Flyway V32/V33 aplicadas en PostgreSQL local (`mycar.tive`, `mycar.solicitud_soporte`).
- Backend local `http://127.0.0.1:8081/` health UP.
- `ValidadorTiveTest` PASS.
- APK USB: `dist/MyCar-TIVE-Soporte-VIN-USB-debug.apk` (apiBaseUrl `http://127.0.0.1:8081/`).
- ADB: dispositivo `SM_A356E` (`RFCX406KNYW`) autorizado; `adb reverse tcp:8081` + `adb install -r` Success.

| RF | EA | Título | Condición | Esperado | Prueba | Estado |
|----|----|--------|-----------|----------|--------|--------|
| RF52 | EA01 | MYCAR · RF52 — EA01 · VEHÍCULO TIVE INACTIVO | Consulta ya prevista por el RF; condición de excepción: Vehículo no es propio o  | Se retorna a P01 del EF si el usuario corrige/reintenta; de lo contrario, termin | — | PENDIENTE |
| RF52 | EA02 | MYCAR · RF52 — EA02 · VIN AUSENTE | Consulta ya prevista por el RF; condición de excepción: Vehículo propio ACTIVO c | Se retorna a P01 del EF si el usuario corrige/reintenta; de lo contrario, termin | — | PENDIENTE |
| RF52 | EA03 | MYCAR · RF52 — EA03 · TIVE PREVIAMENTE REGISTRADA | No se debe continuar a la operación exitosa incompatible: Existe TIVE actual vin | Se retorna a P01 del EF si el usuario corrige/reintenta; de lo contrario, termin | — | PENDIENTE |
| RF52 | EA04 | MYCAR · RF52 — EA04 · DATOS PRINCIPALES INCOMPLETOS | Se solicita la misma operación del caso exitoso; NO se confirma la mutación. | Se retorna a P23 del EF si el usuario corrige/reintenta; de lo contrario, termin | — | PENDIENTE |
| RF52 | EA05 | MYCAR · RF52 — EA05 · FECHA TÍTULO INVÁLIDA | Se solicita la misma operación del caso exitoso; NO se confirma la mutación. | Se retorna a P23 del EF si el usuario corrige/reintenta; de lo contrario, termin | — | PENDIENTE |
| RF52 | EA06 | MYCAR · RF52 — EA06 · ZONA Y SEDE INCOMPATIBLES | Condición: La combinación opcional zona/sede registral no cumple relación permit | Se retorna a P23 del EF si el usuario corrige/reintenta; de lo contrario, termin | — | PENDIENTE |
| RF52 | EA07 | MYCAR · RF52 — EA07 · ARCHIVO TIVE INVÁLIDO (JPG/PNG/PDF) | El adjunto opcional JPG/PNG/PDF no cumple tipo MIME real, integridad o tamaño ad | Mostrar error del archivo y conservar formulario; no registrar TIVE con un archi | — | PENDIENTE |
| RF52 | EA08 | MYCAR · RF52 — EA08 — Rechazar patrón de VIN, motor, serie o placa | VIN no cumple ^[A-HJ-NPR-Z0-9]{17}$; motor no tiene 5–15 caracteres permitidos;  | No registrar TIVE; señalar el campo a corregir y conservar vehículo intacto. | — | PENDIENTE |
| RF52 | EA09 | MYCAR · RF52 — EA09 · ERROR PERSISTIENDO TIVE | La pantalla inicia la operación documentada; disparador: E_TIVE no confirma regi | Se retorna a P23 del EF si el usuario corrige/reintenta; de lo contrario, termin | — | PENDIENTE |
| RF52 | EA10 | MYCAR · RF52 — EA10 · FALLA CARGA DE ARCHIVO TIVE OPCIONAL | Falla carga de imagen/PDF o persistencia de su referencia privada durante el alt | No confirmar éxito parcial; hacer rollback de BD y limpiar archivo provisional s | — | PENDIENTE |
| RF52 | EA11 | MYCAR · RF52 — EA11 · VEHÍCULO PASA A INACTIVO AL CONFIRMAR TIVE | La entidad devuelve que el vehículo ya no está ACTIVO. | No ejecuta registrarTIVESiVehiculoActivoYDisponible; libera bloqueo/ROLLBACK sin | — | PENDIENTE |
| RF52 | EA12 | MYCAR · RF52 — EA12 — Rechazar VIN o placa de TIVE discrepantes del vehículo | Los datos TIVE ingresados no concuerdan con VIN/placa del vehículo propio ACTIVO | Denegar creación de E_TIVE; VIN/placa permanecen inmutables y el vehículo no se  | — | PENDIENTE |
| RF53 | EA01 | MYCAR · RF53 — EA01 · ACCESO AJENO | Consulta ya prevista por el RF; condición de excepción: El vehículo/TIVE consult | Se retorna a P01 del EF si el usuario corrige/reintenta; de lo contrario, termin | — | PENDIENTE |
| RF53 | EA02 | MYCAR · RF53 — EA02 · TIVE NO ENCONTRADA | Consulta ya prevista por el RF; condición de excepción: No hay TIVE actual para  | Se retorna a P01 del EF si el usuario corrige/reintenta; de lo contrario, termin | — | PENDIENTE |
| RF53 | EA03 | MYCAR · RF53 — EA03 · DOCUMENTO TIVE AUSENTE | La TIVE propia existe, pero no tiene imagen JPG/PNG ni PDF adjunto. | Mostrar datos TIVE y VIN de solo lectura con «Sin documento adjunto»; ofrecer ag | — | PENDIENTE |
| RF53 | EA04 | MYCAR · RF53 — EA04 · VISOR DE FOTO/PDF NO ABRE | La imagen o PDF privado de TIVE no está disponible temporalmente o el visor fall | Mantener visibles datos TIVE, informar fallo y permitir reintentar sin modificar | — | PENDIENTE |
| RF54 | EA01 | MYCAR · RF54 — EA01 · VEHÍCULO INACTIVO | No se debe continuar a la operación exitosa incompatible: Se intenta Editar TIVE | Se retorna a P01 del EF si el usuario corrige/reintenta; de lo contrario, termin | — | PENDIENTE |
| RF54 | EA02 | MYCAR · RF54 — EA02 · NO EXISTE TIVE ACTUAL | Consulta ya prevista por el RF; condición de excepción: El idVehiculo ya no tien | Se retorna a P01 del EF si el usuario corrige/reintenta; de lo contrario, termin | — | PENDIENTE |
| RF54 | EA03 | MYCAR · RF54 — EA03 · NO SE MODIFICÓ NADA | Se solicita la misma operación del caso exitoso; NO se confirma la mutación. | Se retorna a P16 del EF si el usuario corrige/reintenta; de lo contrario, termin | — | PENDIENTE |
| RF54 | EA04 | MYCAR · RF54 — EA04 · DATOS PRINCIPALES INVÁLIDOS | Se solicita la misma operación del caso exitoso; NO se confirma la mutación. | Se retorna a P16 del EF si el usuario corrige/reintenta; de lo contrario, termin | — | PENDIENTE |
| RF54 | EA05 | MYCAR · RF54 — EA05 · REGISTROS OPCIONALES INVÁLIDOS | Condición: Zona y sede registral no corresponden entre sí, o datos opcionales in | Se retorna a P16 del EF si el usuario corrige/reintenta; de lo contrario, termin | — | PENDIENTE |
| RF54 | EA06 | MYCAR · RF54 — EA06 — Intento de cambiar VIN o placa por edición TIVE | El cliente envía un payload que cambia VIN o placa, incluso manipulando directam | Rechazar modificación sensible, sin cambios en E_Vehiculo ni E_TIVE; VIN solo ví | — | PENDIENTE |
| RF54 | EA07 | MYCAR · RF54 — EA07 · ERROR DE ACTUALIZACIÓN | La pantalla inicia la operación documentada; disparador: E_TIVE no confirma el U | Se retorna a P16 del EF si el usuario corrige/reintenta; de lo contrario, termin | — | PENDIENTE |
| RF54 | EA08 | MYCAR · RF54 — EA08 · REGRESAR A DETALLE | Ruta alternativa después del guardado exitoso de TIVE; no representa un error de | Se retorna a P27 del EF si el usuario corrige/reintenta; de lo contrario, termin | — | PENDIENTE |
| RF54 | EA09 | MYCAR · RF54 — EA09 · TIVE EDITADA SIMULTÁNEAMENTE | Verifica titularidad, estado ACTIVO y que el documento no cambió desde la lectur | La TIVE persistida permanece con la última versión válida; no se sobrescriben ca | — | PENDIENTE |
| RF54 | EA10 | MYCAR · RF54 — EA10 — Revertir edición sincronizada de motor y serie si falla escritura | Motor o serie válidos cambian, pero falla actualizar una de las copias persistid | ROLLBACK de ambas actualizaciones; no permitir diferencias entre entidades ni mo | — | PENDIENTE |
| RF52 | EA13 | MYCAR · RF52 — EA13 — VIN ya registrado por otro vehículo al crear TIVE | VIN de 17 caracteres válido pero asociado a otro vehículo. | Rechazar por unicidad del VIN sin crear TIVE; conservar información original y n | — | PENDIENTE |
| RF52 | EA14 | MYCAR · RF52 — EA14 — Registrar TIVE sin adjuntar JPG/PNG/PDF | El usuario completa todos los datos obligatorios de TIVE y omite el archivo opci | Se crea una sola TIVE válida, con referencia de adjunto nula; RF53 muestra «Sin  | — | PENDIENTE |
| RF53 | EA05 | MYCAR · RF53 — EA05 — Intento de abrir archivo TIVE de vehículo ajeno | El usuario intenta descargar/ver la imagen/PDF TIVE mediante un identificador de | Denegar sin emitir URL temporal, vista previa o bytes de JPG/PNG/PDF; registrar  | — | PENDIENTE |
| RF54 | EA11 | MYCAR · RF54 — EA11 — Agregar o reemplazar únicamente el documento TIVE | El titular abre Actualizar TIVE, conserva todos los campos documentales y solo a | Se mantiene el mismo registro idVehiculo/TIVE con VIN, placa, motor y serie inta | — | PENDIENTE |
| RF54 | EA12 | MYCAR · RF54 — EA12 — Falla reemplazo de documento: conservar archivo anterior | El usuario elige reemplazar la imagen/PDF pero falla la carga o la persistencia  | Conservar archivo y campos anteriores; no mostrar éxito ni borrar documento orig | — | PENDIENTE |
| RF54 | EA13 | MYCAR · RF54 — EA13 — Adjunto JPG/PNG/PDF inválido al actualizar TIVE | El usuario intenta agregar o reemplazar un archivo cuyo contenido/tamaño no cump | Rechazar el archivo, no modificar TIVE ni eliminar el documento antiguo; conserv | — | PENDIENTE |
| RF54 | EA14 | MYCAR · RF54 — EA14 — Quitar solo documento adjunto de TIVE propia | El usuario selecciona Quitar documento desde RF54, mantiene los campos y confirm | Referencia privada retirada del mismo TIVE; el archivo antiguo se limpia tras CO | — | PENDIENTE |
| RF58 | EA01 | MYCAR · RF58 — EA01 — Agente sin permisos | Usuario no autorizado intenta listar/decidir | Acceso denegado 403. | — | PENDIENTE |
| RF58 | EA02 | MYCAR · RF58 — EA02 — Caso ya cerrado | Se intenta resolver APROBADA/RECHAZADA | Mostrar caso finalizado, sin botones activos. | — | PENDIENTE |
| RF58 | EA03 | MYCAR · RF58 — EA03 — Dos agentes resuelven a la vez | Aprobaciones concurrentes | Solo una decisión se confirma; segunda recibe conflicto. | — | PENDIENTE |
| RF58 | EA04 | MYCAR · RF58 — EA04 — Falta evidencia legible | Documento insuficiente para respaldar | Permitir rechazar con motivo, sin cambiar VIN. | — | PENDIENTE |
| RF58 | EA05 | MYCAR · RF58 — EA05 — VIN propuesto se vuelve duplicado | Otro vehículo usó el VIN después del envío | No aprobar; mostrar conflicto, conservar EN REVISIÓN. | — | PENDIENTE |
| RF58 | EA06 | MYCAR · RF58 — EA06 — VIN actual ya cambió | VIN guardado no coincide con el del caso | Detener aprobación y pedir revisión humana. | — | PENDIENTE |
| RF58 | EA07 | MYCAR · RF58 — EA07 — TIVE asociada no coincide | Relación documento/vehículo inconsistente | Bloquear aprobación hasta corregir inconsistencia. | — | PENDIENTE |
| RF58 | EA08 | MYCAR · RF58 — EA08 — Error durante auditoría | Inserción de evento falla | Mostrar error sin afirmar aprobación. | — | PENDIENTE |
| RF58 | EA09 | MYCAR · RF58 — EA09 — Rechazo sin motivo | El agente pulsa Rechazar sin comentario | Bloquear resolución y enfocar campo. | — | PENDIENTE |
| RF58 | EA10 | MYCAR · RF58 — EA10 — Rechazo correcto | Agente rechaza con justificación | Confirmar RECHAZADA, mostrar motivo al usuario. | — | PENDIENTE |
| RF58 | EA11 | MYCAR · RF58 — EA11 — Caso general no es VIN | Usuario elige Otras incidencias no habilitada | Mostrar «Próximamente» o módulo existente si ya existe. | — | PENDIENTE |
| RF58 | EA12 | MYCAR · RF58 — EA12 — Decisión concurrente detectada al resolver | Otro agente inició o resolvió el mismo caso mientras estaba abierto | Rechazar intento obsoleto, actualizar vista y evitar doble aprobación. | — | PENDIENTE |
| RF58 | EA13 | MYCAR · RF58 — EA13 — Error al registrar auditoría de resolución | Error de escritura del evento obligatorio al resolver | Rollback de decisión y VIN; no mostrar confirmación. | — | PENDIENTE |
| RF58 | EA14 | MYCAR · RF58 — EA14 — Evidencia insuficiente: rechazo motivado | El documento adjunto no justifica la corrección | Permitir rechazo con motivo obligatorio; no existe SUBSANACIÓN en esta versión. | — | PENDIENTE |
| RF58 | EA15 | MYCAR · RF58 — EA15 — VIN inválido o duplicado al aprobar | La nueva comprobación en backend detecta VIN inválido o usado | No cambiar vehículo/TIVE ni cerrar como aprobado. | — | PENDIENTE |
| RF58 | EA16 | MYCAR · RF58 — EA16 — Falla atómica durante aprobación | Falla actualización del VIN, sincronización de TIVE, auditoría o estado | Rollback conjunto sin confirmar el cambio. | — | PENDIENTE |
| RF61 | EA01 | MYCAR · RF61 — EA01 — Historial vacío | Usuario nunca envió solicitudes | Mostrar «Aún no tienes solicitudes». | — | PENDIENTE |
| RF61 | EA02 | MYCAR · RF61 — EA02 — Detalle de otro titular | Cambia el ID en petición | 404 sin filtrar datos ajenos. | — | PENDIENTE |
| RF61 | EA03 | MYCAR · RF61 — EA03 — Sesión vencida | Token ausente/inválido | Solicitar inicio de sesión. | — | PENDIENTE |
| RF61 | EA04 | MYCAR · RF61 — EA04 — ID inexistente | Selecciona código ya no existente | 404 y regreso a la lista. | — | PENDIENTE |
| RF61 | EA05 | MYCAR · RF61 — EA05 — Estado PENDIENTE o EN REVISIÓN | Solicitud sigue abierta | Mostrar estado; sin acciones de resolución. | — | PENDIENTE |
| RF61 | EA06 | MYCAR · RF61 — EA06 — Estado APROBADA | Soporte confirmó cambio | Informar VIN actualizado internamente. | — | PENDIENTE |
| RF61 | EA07 | MYCAR · RF61 — EA07 — Estado RECHAZADA | Soporte resolvió rechazo | Mostrar razón legible y fecha. | — | PENDIENTE |
| RF61 | EA08 | MYCAR · RF61 — EA08 — Falla al visualizar archivo | Error de conexión/archivo temporal | Mostrar reintento; preservar información de caso. | — | PENDIENTE |
| RF61 | EA09 | MYCAR · RF61 — EA09 — Solicitud sin eventos posteriores a su registro | Solicitud reciente no tiene eventos de avance | Mostrar estado vigente sin inventar una cronología. | — | PENDIENTE |
| RF61 | EA10 | MYCAR · RF61 — EA10 — Falla al recuperar eventos de la solicitud | Error técnico al cargar eventos de seguimiento | Mostrar error y ofrecer reintento sin tratarlo como historial vacío. | — | PENDIENTE |
| RF61 | EA11 | MYCAR · RF61 — EA11 — Estado cambiado desde el listado | Soporte resolvió mientras el usuario abría el detalle | Consultar estado actualizado; mostrar Aprobada/Rechazada y motivo cuando proceda | — | PENDIENTE |
| RF63 | EA01 | MYCAR · RF63 — EA01 — Sin vehículos propios | Usuario no tiene vehículos disponibles | Estado vacío; pedir registrar vehículo. | — | PENDIENTE |
| RF63 | EA02 | MYCAR · RF63 — EA02 — Vehículo sin TIVE | Selecciona vehículo sin TIVE registrada | Bloquear y explicar requisito. | — | PENDIENTE |
| RF63 | EA03 | MYCAR · RF63 — EA03 — Vehículo ajeno o inexistente | Cambia idVehiculo desde petición | 404 o respuesta sin datos privados. | — | PENDIENTE |
| RF63 | EA04 | MYCAR · RF63 — EA04 — VIN mal formado | Longitud distinta de 17 o caracteres inválidos | Mostrar formato requerido; no enviar. | — | PENDIENTE |
| RF63 | EA05 | MYCAR · RF63 — EA05 — VIN con I/O/Q o minúsculas | Incluye I, O, Q o letra minúscula | Rechazar sin corregir silenciosamente. | — | PENDIENTE |
| RF63 | EA06 | MYCAR · RF63 — EA06 — VIN propuesto igual al actual | Ingresa mismo VIN registrado | Indicar que no existe cambio solicitado. | — | PENDIENTE |
| RF63 | EA07 | MYCAR · RF63 — EA07 — VIN ya existe en otro vehículo | Propone VIN duplicado | Bloquear con aviso de duplicidad. | — | PENDIENTE |
| RF63 | EA08 | MYCAR · RF63 — EA08 — Motivo vacío | No explica la razón | Mostrar validación obligatoria. | — | PENDIENTE |
| RF63 | EA09 | MYCAR · RF63 — EA09 — Sin evidencia obligatoria | Intenta enviar sin archivo | Solicitar JPG/PNG/PDF. | — | PENDIENTE |
| RF63 | EA10 | MYCAR · RF63 — EA10 — Evidencia inválida o excesiva | Archivo corrupto, MIME incorrecto o >10MB | Rechazar solo archivo; mantener campos. | — | PENDIENTE |
| RF63 | EA11 | MYCAR · RF63 — EA11 — Caso abierto para el mismo vehículo | Existe PENDIENTE o EN REVISIÓN | Informar caso existente y ofrecer verlo. | — | PENDIENTE |
| RF63 | EA12 | MYCAR · RF63 — EA12 — Reintento doble envío | Mismo idOperacion y payload | Devolver mismo código; nuevo payload con misma clave → conflicto. | — | PENDIENTE |
| RF63 | EA13 | MYCAR · RF63 — EA13 — Falla al almacenar evidencia | Archivo no se confirma o BD falla | Error recuperable; conservar formulario. | — | PENDIENTE |
| RF63 | EA14 | MYCAR · RF63 — EA14 — Sesión expirada | Acceso sin token válido | Solicitar inicio de sesión. | — | PENDIENTE |

## Leyenda

- PASS: evidencia de prueba ejecutada
- FAIL: fallo real documentado
- PENDIENTE: aún no ejecutado
- MANUAL_USB: requiere dispositivo
