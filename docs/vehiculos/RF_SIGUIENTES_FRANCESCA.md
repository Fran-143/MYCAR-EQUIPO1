# RF siguientes para Francesca (post-MVP)

Mientras Elsa perfecciona **login / RF01–RF02–RF06**, no tocar autenticación.
Tu zona natural: vehículos, kilometraje y lo colindante a alertas/mantenimiento.

## Ya entregado (MVP en `main`)

| RF | Qué |
|----|-----|
| RF08 | Registrar vehículo |
| RF09 | Listar vehículos |
| RF10 | Detalle de vehículo |
| RF14 | Actualizar kilometraje |
| RF50 | Inicio / navegación (fuera de los 11, pero ya en app) |

## Prioridad recomendada (cercanía al código)

| Orden | RF | Por qué conviene ahora |
|------:|----|------------------------|
| 1 | **RF13** Establecer vehículo principal | BD tiene `vehiculo.es_principal`; RF50 solo lo lee. Falta API + UI. |
| 2 | **RF12** Activar/desactivar vehículo | Existe `estado_vehiculo` ACTIVO/INACTIVO; el listado filtra ACTIVO. Falta toggle. |
| 3 | **RF15** Historial de kilometraje | `lectura_kilometraje` ya se llena con RF08/RF14. Falta consulta/listado. |
| 4 | **RF19** Detalle de mantenimiento | En historial RF18 aparece «Ver detalle · Próximamente». |
| 5 | **RF24** Atender alerta | Cierra el ciclo RF22 → RF23 → atender. Misma carpeta `alertas`. |

## Dejar para después

SOAT (RF31–33), talleres (RF34–37), fotos (RF38–40, RF42–45), admin (RF58–62),
licencia/TIVE (RF51–56), Premium (RF29). Planes Free (RF28/30) ya se apoyan en RF08 vía integración.

## Dependencias rápidas

```text
RF13 / RF12  →  detalle o listado de vehículos (RF09/RF10)
RF15         →  lecturas de RF08/RF14
RF19         →  historial RF18
RF24         →  alertas RF22/RF23
```

## Simulación de alertas (resumen)

Ver sección **«Simulación rápida de estados (km)»** en [GUIA_RF22_RF23.md](GUIA_RF22_RF23.md):
alerta **POR_KILOMETRAJE** + subir km con **RF14** para ver Programada → Próxima → Pendiente → Vencida
sin esperar meses.
