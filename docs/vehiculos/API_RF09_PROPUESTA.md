# RF09 — Listado de vehículos propios (contrato técnico para integrar)

27/10/2026. Basado en las reglas RN09-01 a RN09-09 y sus escenarios alternativos.
No modifica los diagramas. Los nombres de ruta/DTO son la propuesta implementada, pendiente de coordinación con el equipo.

GET /api/v1/vehiculos?filtro=TODOS&busqueda=Toyota

- Identidad: la misma interfaz IdentidadVehiculos y Authentication validados por Elsa; nunca idUsuario de Android.
- Sin adaptador/identidad válida: 401. No se abre el acceso anónimo ni se modifica Spring Security.
- Cuenta no ACTIVA: 403 CUENTA_NO_AUTORIZADA.
- filtro ausente: TODOS. Valores exactos permitidos: TODOS, ACTIVO, INACTIVO. Otro valor, incluido vacío: 400 FILTRO_INVALIDO.
- busqueda ausente: vacía. Se recortan extremos y agrupan espacios. Coincide como texto literal, sin distinguir mayúsculas,
  en placa, marca, modelo o marca + modelo. % y _ no son comodines ni fragmentos SQL.
- Primero se limita a propietario en SQL; luego se aplican los criterios sin incorporar filas ajenas.
- Orden: principal primero si coincide, seguido de idVehiculo descendente para un orden estable.
- Resumen: todos/activos/inactivos del propietario, independientes del filtro y del texto.
- No exige un plan Free operativo para leer vehículos ya registrados.
- Kilometraje: última lectura INICIAL/ACTUALIZACION por fecha e ID. Ausencia = null, nunca cero inventado.
- Motor/combustible OTRO muestra su especificación. Eléctrico tiene combustible null.
- Una única consulta de vehículos obtiene tarjetas y lecturas. Listar no inserta ni actualiza datos.

Respuesta ilustrativa:

```json
{
  "vehiculos": [{
    "idVehiculo": 1, "placa": "A1B234", "marca": "Toyota", "modelo": "Corolla",
    "anioFabricacion": 2020, "usoVehiculo": "Particular", "color": "Gris",
    "tipoMotor": "Combustión", "combustible": "Gasolina", "estadoVehiculo": "ACTIVO",
    "principal": true, "kilometrajeActual": 58240
  }],
  "resumen": {"todos": 1, "activos": 1, "inactivos": 0},
  "filtro": "TODOS", "busqueda": "Toyota"
}
```

Lista completamente vacía: vehiculos=[] y resumen.todos=0.
Sin coincidencias: vehiculos=[] y resumen.todos>0; conservar criterios.
Error de BD: 503 SERVICIO_NO_DISPONIBLE, sin convertirlo en una lista vacía ni mostrar SQL.

MVP usa imagen incluida en el APK. No devuelve SOAT/TIVE como NO_REGISTRADO: sus indicadores se omiten.
No implementa RF10, RF11, RF13 ni compartir vehículos. El callback de detalle queda preparado para RF10;
no se muestra un botón funcional que finja abrir un detalle inexistente.

Elsa debe incluir este quinto endpoint en su cadena de seguridad de vehículos.
Las pruebas MVC con dobles no certifican JWT/sesión de Elsa; se verificó por HTTP el rechazo anónimo.
