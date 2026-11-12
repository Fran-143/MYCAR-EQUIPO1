# Plan seguro de despliegue — RF23 + recordatorios FCM (US$0)

**No ejecutar hasta autorización explícita.** Neon / Render / Cron / FCM real: gated.

## Hallazgos de continuidad FCM

| Categoría outbox | ¿Quién encola hoy? | ¿Quién envía? |
|------------------|--------------------|---------------|
| MANTENIMIENTO | `RecordatorioPushService` (cron/km) | `procesarLote` (cron 05:00 + worker diurno) |
| SOAT / LICENCIA / KILOMETRAJE | Nadie en código actual | Mismo `procesarLote` si hubiera filas |

Desactivar el tick 15 s **de golpe sin cron** dejaría filas PENDIENTE/ERROR sin drain → gap.  
Por eso Render queda con `worker-habilitado=true` **acotado a 05:00–21:59 Lima** hasta validar el cron.

## Orden de despliegue (sin gap)

1. **V25 en Neon** (solo SQL; sin apagar FCM). Conserva filas; asigna `LEGACY:{id}`; crea `alerta_push_control` + `outbox_push_alerta`.
2. **Backend Render** (código nuevo): endpoints eval + despacho; worker diurno ON; `EVAL` aún no vía cron.
3. **Worker Cloudflare** deploy con `EVAL_MODO=SIMULAR` y `crons=[]` (código dual-cron listo).
4. **Activación** (OK explícito): `EVAL_MODO=COMPLETO` + `crons=["10 5 * * *","0 10 * * *"]`.
5. **Verificar** un día: eval 00:10 escribe estados; despacho 05:00 envía; sin ráfagas.
6. **Apagar tick** (`mycar.fcm.worker-habilitado=false`) solo cuando el paso 5 esté OK.

Nunca: apagar worker **antes** de tener cron 05:00 activo y verificado.

## Horarios Lima

| Qué | Ventana |
|-----|---------|
| Eval RF23 | 00:10 (`10 5 * * *` UTC) |
| Recordatorios programados | desde 05:00 (`0 10 * * *` UTC), despacho 05:00–21:59 |
| Inmediato km | 05:00–21:59 |
| 22:00–04:59 | sin envíos ordinarios |

## Si Render o Cloudflare fallan a las 05:00

- Filas quedan `PENDIENTE`/`ERROR` (no se marcan entregadas).
- Worker Cloudflare reintenta wake (hasta 4 intentos).
- Mismo día: tick diurno en Render (mientras esté ON) puede drenar outbox.
- Día siguiente: nuevo ciclo de claves; **sin ráfaga** de días atrasados (máx. 1/alerta/día).
- Eval fallida a las 00:10: estados se actualizan al abrir Mis alertas / próximo cron.

## Prueba FCM real limitada (preparada, no ejecutar sin OK)

1. Usuario/dispositivo de prueba con RF48 mantenimiento ON + permiso CONCEDIDO + token FCM real.
2. Encolar **una** fila outbox con clave de prueba o disparar `POST /api/v1/interno/push/despachar-recordatorios` con secreto en horario 05:00–21:59.
3. Confirmar un solo push en el dispositivo; outbox → `ENVIADO`; `alerta_push_control` actualizado.
4. Script apoyo: `scripts/rf23_fcm_prueba_dispositivo.md` (checklist).

## V25 y datos existentes

- `UPDATE ... clave_idempotencia = 'LEGACY:'||id_outbox` sin cambiar `estado`.
- `PENDIENTE`/`ERROR` siguen reclamables (`intentos < 8`, `SKIP LOCKED`).
- Encolado ≠ entregado: control de frecuencia solo tras `ENVIADO`.
