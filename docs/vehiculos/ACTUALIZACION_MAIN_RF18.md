# Actualización de la rama de sesión con main / RF18

Base del bloque de sesión: 032aef2. Avance incorporado: main 6fc5ad4 (PR #10 de Nayu).

Se conserva el bloque RF02/RF06 → RF08/RF09 de Francesca y se incorpora el historial RF18 de Nayu.
Las diferencias de MainActivity y HomeScreen se combinan conservando los callbacks de ambos trabajos.
No se reemplazan los servicios, contratos, pruebas ni repositorios de historial/mantenimiento.

Inicio principal ofrece: Mis vehículos · iniciar sesión; Historial de mantenimientos;
Registrar mantenimiento; Comprobar conexión; y, exclusivamente en DEBUG, Pruebas visuales.
El contenido permite scroll en pantallas pequeñas. Al instalar y ejecutar la app abre MainActivity.
La demostración de vehículos permanece accesible por un botón explícito y sigue sin escribir en BD.
El permiso local DEBUG y las entradas RF18 se conservan; hay un solo launcher por variante.

Los accesos de mantenimiento/historial conservan el estado de integración de Nayu: sin repositorio/cuenta
inyectados requieren sesión y no se simulan datos ni se declara persistencia de esos módulos.
Este cambio combina navegación. No conecta automáticamente los tres módulos a una misma sesión.

El documento INTEGRACION_SESION_VEHICULOS.md describe el bloque original y su base histórica; desde esta
actualización, el arranque cambia a Inicio y «Pruebas visuales» lleva al menú de demostración.
La ruta de acceso real continúa siendo «Mis vehículos · iniciar sesión» y el backend local usa 8081.
Las migraciones V1–V5, .env y datos existentes no se modifican.

## Confirmar que RF08 persiste

Solo una sesión válida y un POST confirmado mediante la entrada real pueden registrar un vehículo.
El botón de health verifica disponibilidad, no inserta datos. Las pruebas automáticas usan contenedores
desechables y sus datos no aparecen en mycar_mvp.

Con el servicio local en marcha, registrar una placa válida y localizarla en el listado. En PostgreSQL:

    SELECT v.id_vehiculo, v.placa, v.marca, v.modelo, l.valor_km, o.estado
    FROM mycar.vehiculo v
    LEFT JOIN mycar.lectura_kilometraje l
      ON l.id_vehiculo=v.id_vehiculo AND l.origen='INICIAL'
    LEFT JOIN mycar.operacion_idempotente o ON o.id_operacion=l.id_operacion
    ORDER BY v.id_vehiculo DESC LIMIT 10;

La fila esperada tendrá la placa introducida, kilometraje inicial y operación CONFIRMADA.
No insertar SQL manualmente para simular una prueba de la aplicación.

## Actualización local

Conservar primero los 30 archivos de sesión en un commit local de la rama de Francesca.
Luego git merge --no-commit origin/main. Se esperan conflictos en MainActivity y HomeScreen.
Aplicar únicamente los cinco archivos del paquete de resolución, revisar el diff y correr pruebas.
La resolución se hace en feat/integracion-sesion-vehiculos, sin merge automático del PR hacia main.
No reaplicar el ZIP original de 30 archivos sobre esta resolución.
