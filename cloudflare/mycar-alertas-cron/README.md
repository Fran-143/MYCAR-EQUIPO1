# mycar-alertas-cron

Worker Cloudflare Free que dispara la evaluación RF23 del backend MyCar en Render.

**No crear otro Worker.** Reutilizar el ya existente `mycar-alertas-cron`.

## Endpoint backend

```
POST https://mycar-backend-1dxi.onrender.com/api/v1/interno/alertas/evaluar-programadas
Header: X-MyCar-Cron-Secret: <MYCAR_CRON_SECRET>
Body: {"modo":"SIMULAR"} | {"modo":"COMPLETO"}
```

- `SIMULAR`: solo cuenta vehículos; no escribe.
- `COMPLETO`: misma lógica que el job RF23 (idempotente).

El job Spring `evaluacion-diaria-habilitada` permanece **false** en perfiles neon/render.

## Secretos

| Dónde | Nombre |
|-------|--------|
| Render Environment | `MYCAR_CRON_SECRET` |
| Cloudflare Worker | `CRON_SECRET` (mismo valor) |

Nunca commitear el valor.

## Despliegue del código (cuando el backend ya esté en Live)

```bash
cd cloudflare/mycar-alertas-cron
npx wrangler secret put CRON_SECRET
npx wrangler deploy
```

## Cron Trigger (08:00 Lima = 13:00 UTC)

**Desactivado** en `wrangler.toml` hasta prueba controlada + aprobación.

Para activar después de aprobación:

1. Descomentar `[triggers]` / `crons = ["0 13 * * *"]` en `wrangler.toml`, **o**
2. En Cloudflare Dashboard → Worker → Triggers → Add Cron Trigger → `0 13 * * *`

## Prueba manual del Worker (sin cron)

```bash
curl -X POST https://mycar-alertas-cron.mycargrupo1.workers.dev/run \
  -H "X-MyCar-Cron-Secret: <secreto>"
```

## Extensión futura (SOAT / licencia)

Añadir reglas en `EvaluacionProgramadaAlertasService` (Spring). Este Worker no se reconstruye.
