# mycar-alertas-cron

Worker Cloudflare Free → RF23 en Render. **Reutilizar** el Worker existente
`mycar-alertas-cron` (cuenta `mycargrupo1`). No crear otro.

## Primera publicación (estado actual)

| Ajuste | Valor |
|--------|--------|
| `EVAL_MODO` | `SIMULAR` |
| `[triggers] crons` | `[]` (sin programación) |
| `POST /run` | **fuerza SIMULAR** (ignora body/query/`EVAL_MODO`) |
| Job Spring RF23 en Render | `false` |

`SIMULAR` en backend solo lista vehículos evaluables: **no** llama `sincronizar`,
**no** escribe Neon, **no** crea alertas/avisos, **no** encola FCM.

## Endpoint backend

```
POST .../api/v1/interno/alertas/evaluar-programadas
Header: X-MyCar-Cron-Secret: <secreto>
Body: {"modo":"SIMULAR"} | {"modo":"COMPLETO"}
```

## Secretos + deploy

```powershell
cd cloudflare\mycar-alertas-cron
npx wrangler secret put CRON_SECRET   # = MYCAR_CRON_SECRET en Render
npx wrangler deploy                   # actualiza mycar-alertas-cron; cron sigue vacío
```

## Activación futura (solo con aprobación explícita)

1. En `wrangler.toml`: `EVAL_MODO = "COMPLETO"`
2. En `wrangler.toml`: `crons = ["0 13 * * *"]`  # 08:00 America/Lima
3. `npx wrangler deploy`

`POST /run` permanece en **SIMULAR** a propósito (pruebas manuales seguras).
El Cron programado usará `EVAL_MODO` (`COMPLETO` tras el paso 1).
