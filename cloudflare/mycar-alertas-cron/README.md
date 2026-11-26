# mycar-alertas-cron

Worker Cloudflare existente. **No desplegar ni activar crons sin autorización.**

## Horarios aprobados (UTC, America/Lima UTC−5)

| Cron UTC | Hora Lima | Acción |
|----------|-----------|--------|
| `10 5 * * *` | 00:10 | Eval RF23 + RF43 (separados) |
| `0 10 * * *` | 05:00 | Despacho push (ventana 05:00–21:59) |

## Seguridad actual (conservar)

```toml
EVAL_MODO = "SIMULAR"
crons = []
```

- `POST /run` fuerza **SIMULAR** (sin escrituras).
- No ejecutar `wrangler deploy` en esta fase.
- No apuntar pruebas locales a Render/Neon productivos.

## Rutas backend

- `POST /api/v1/interno/alertas/evaluar-programadas` body `{ "modo": "SIMULAR"|"COMPLETO" }`
- `POST /api/v1/interno/recordatorios-km/evaluar` body `{ "modo": "SIMULAR"|"COMPLETO" }`
- `POST /api/v1/interno/push/despachar-recordatorios`

## Prueba local sugerida

1. Backend local `:8081` con `MYCAR_CRON_SECRET`.
2. `BACKEND_URL=http://127.0.0.1:8081` solo en entorno de desarrollo (no en wrangler productivo).
3. Invocar endpoints con curl + secreto; revisar conteos RF23/RF43 por separado.
