# Corrección RF22 reapertura + RF14 desde mantenimiento

## Causas concretas

1. **Tercera (y siguientes) alertas:** `ProgramarAlertaViewModel` quedaba en el `ViewModelStore` de la cuenta con `exito != null`. Al pulsar de nuevo «Programar alerta», `LaunchedEffect(exito)` cerraba el formulario al instante. **No hay tope de 2 alertas** en los Excel.
2. **Actualizar km solo una vez:** el mismo ViewModel RF14 quedaba en `EXITO`; la segunda apertura reutilizaba la clave y mostraba el éxito previo en lugar del formulario.

## Reglas aplicadas (fuentes)

| Regla | Fuente | Exigencia |
|-------|--------|-----------|
| Sin máximo de alertas; solo equivalencia activa | `02_29_…Nayu…xlsx` RF22-EA04; consolidado RF22 pasos 57–59 | Rechazar duplicado equivalente; REALIZADA/ANULADA permiten otro ciclo |
| 7 tipos compartidos RF17/RF22 | Nayu MVP_LEEME fila tipos; consolidado RF22 | Catálogo activo |
| RF14 no modifica alertas; RF23 sincroniza al listar | Nayu MVP_LEEME RF14↔RF23 | Tras guardar km, recargar mantenimiento; lista RF23 al volver |
| Abrir RF14 no escribe lectura | Francesca RF14 / consolidado | Solo POST en confirmar |
| Conservar borrador RF17 | Nayu CONCORDANCIA / INTEGRACION | Overlay sin desmontar el flujo |

## Corrección

- Sesión de formulario (`alertasSesion` / `kmSesion`) en la clave del ViewModel.
- Idempotencia: conservar `idOperacion` en reintento; invalidar si cambian los datos.
- Overlay RF14 sobre mantenimiento + `recargaRegistroToken` tras guardar km.
- Textos de UI sin jerga interna de RF23.
