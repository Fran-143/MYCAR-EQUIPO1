# Servicio interno RF08 — propuesta de integración

Fecha: 27 de octubre de 2026. Responsable: Francesca.

Este lote termina el núcleo transaccional del alta RF08. El controlador HTTP, su autorización, los códigos HTTP definitivos y la pantalla Android siguen pendientes. Las rutas de los contratos funcionales anteriores siguen siendo propuestas. No modifica diagramas ni migraciones V1–V5.

## Punto de entrada interno

`RegistroVehiculoService.registrar(long idUsuarioAutenticado, DatosRegistroVehiculo datos)` devuelve `ResultadoRegistroVehiculo`.

El controlador que se acuerde con Elsa debe resolver el identificador desde una sesión validada por Spring Security. El parámetro del servicio no es una API pública para que Android envíe cualquier idUsuario. El servicio comprueba la cuenta ACTIVA, pero no valida credenciales, tokens, revocación ni expiración de sesiones. No se añadió una configuración que permita todas las peticiones.

## Orden y transacción

1. Verificar estructura mínima (solicitud e idOperacion) y cuenta ACTIVA.
2. Calcular huella SHA-256 del DTO, reservar UUID global y recuperar operación con filtro usuario/tipo y bloqueo.
3. Rechazar clave ajena, otro tipo o distinta huella con un conflicto genérico sin revelar respuesta ajena.
4. Si ya está CONFIRMADA, devolver el resultado guardado antes de validar catálogos, cupo o plan.
5. Una EN_PROCESO ya existente genera OPERACION_EN_PROCESO: no se inventa éxito ni se toma propiedad de ella. La reconciliación HTTP/Android está pendiente; conservar misma clave y contenido ante resultado incierto.
6. Para operación nueva: validaciones existentes, bloqueo usuario FOR NO KEY UPDATE, lectura de Free real ACTIVA sin fin, plan activo con límite 1 y fecha de inicio vigente. La consulta bloquea suscripción/plan FOR SHARE durante la transacción.
7. Contar vehículos del usuario incluyendo INACTIVOS; verificar placa/VIN globales.
8. Insertar vehículo y primer principal, lectura INICIAL y configuración individual INTERDIARIO/PAUSADO con fecha_base idéntica a la lectura.
9. Guardar el resultado JSONB y marcar operación CONFIRMADA. La salida correcta del proxy transaccional confirma todos los cambios; cualquier RuntimeException los revierte. Un identificador SQL retornado antes del COMMIT no acredita éxito final.

Todas las escrituras forman parte de la misma transacción Spring. No se crea Free, no se altera la preferencia global de recordatorios y no se crean/sincronizan alertas.

## Huella e idempotencia

La huella interna v1 es SHA-256 de `MYCAR:RF08:v1` seguido de salto de línea y el JSON UTF-8 del DTO con propiedades ordenadas alfabéticamente. Incluye null y todos los campos del record, incluido idOperacion. No transforma mayúsculas, espacios ni valores informados. Solo el servidor calcula la huella.

Mantener este formato estable mientras existan operaciones registradas con v1. Un cambio futuro debe conservar la capacidad de recuperar los resultados anteriores. El resultado guardado es un record interno, no un contrato REST aprobado ni una garantía de orden de claves del JSON (PostgreSQL JSONB puede reordenarlas).

`ResultadoRegistroVehiculo`: idOperacion UUID; idVehiculo Long; placa String; principal boolean; kilometrajeActual int; idLecturaKm Long; fechaLectura Instant UTC; versionKm int inicial 0. La repetición conserva estos valores originales, aunque después cambie la lectura vigente.

## Free y coordinación

`CondicionesRegistroVehiculoDao` consulta la suscripción existente. No sustituye el alta de cuenta de Elsa ni asigna planes de Sebastián. Cuando su API interna esté disponible, acordar si se reutiliza esta consulta o se sustituye su adaptador; preservar reglas y transacción. Un resultado confirmado no vuelve a exigir que la suscripción siga vigente, pero exige cuenta ACTIVA y que el llamador haya autenticado la sesión.

Todo escritor que cambie cupo/suscripción debe coordinar el mismo bloqueo de usuario y orden usuario → vehículo. No sustituir FOR NO KEY UPDATE por FOR UPDATE: las FK de operaciones pueden tomar KEY SHARE y provocar un interbloqueo al convertir ambos bloqueos.

## Errores internos propuestos

- DATOS_INVALIDOS: campos disponibles en `campos()`.
- CUENTA_NO_AUTORIZADA: no crear ni recuperar operaciones para cuenta no activa.
- PLAN_FREE_NO_DISPONIBLE: falta suscripción operativa conforme al MVP.
- LIMITE_FREE: incluye vehículos inactivos.
- PLACA_DUPLICADA / VIN_DUPLICADO: verificación previa sin datos del otro propietario.
- CONFLICTO_REGISTRO: restricción única detectada durante una carrera entre propietarios.
- CONFLICTO_IDEMPOTENCIA: clave/propietario/tipo/contenido incompatibles.
- OPERACION_EN_PROCESO: operación previa sin resultado confirmado recuperable.

Los errores técnicos se propagan y revierten; el futuro manejador HTTP debe devolver un error técnico genérico sin SQL, credenciales ni stacktrace al cliente. No se fijaron códigos HTTP nuevos ni rutas.

## Pruebas y alcance

El ejecutor nuevo `scripts/test_rf08_backend.py` crea PostgreSQL 17 desechable, ejecuta la suite DB existente, las validaciones y 21 casos nuevos del servicio real con Spring y transacciones. Después ejecuta BackendApplicationTests y comprueba HTTP /actuator/health. Nunca utiliza .env ni Compose local; elimina solo su contenedor temporal.

La suite RF08 valida URL temporal y solo se habilita con `mycar.rf08.tests=true`. No ejecutar este test contra mycar_mvp local. Los usuarios y respuestas ficticios son fixtures exclusivos del contenedor desechable; no se agregaron semillas de prueba a producción.

Entre los casos: alta integral, resultado persistido, reintento tras pérdida de respuesta (simulado repitiendo después del COMMIT), cupo después de cambiar suscripción, conflictos de contenido/propietario/tipo, cuenta suspendida, Free ausente, VIN/placa globales, eléctrico, fallo inducido después de lectura/configuración, exigencia de transacción y tres carreras reales de dos hilos. No son pruebas de pérdida real de red, autenticación HTTP ni interfaz Android.

## Pendientes para cerrar RF08 de extremo a extremo

1. Acordar e integrar autenticación/sesión con Elsa y la frontera de consulta Free con Sebastián.
2. Implementar controlador, catálogos, errores HTTP y recuperación de operación; confirmar contratos con el equipo.
3. Implementar formulario Android y reintentos con la misma idOperacion y contenido.
4. Ejecutar aceptación desde la app con sesión real.
