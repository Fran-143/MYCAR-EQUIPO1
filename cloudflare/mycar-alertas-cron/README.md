# mycar-alertas-cron

Worker Cloudflare Free → RF23 + despacho push en Render. **Reutilizar** el Worker
`mycar-alertas-cron` (cuenta `mycargrupo1`). No crear otro. Presupuesto US$0.

## Estado seguro actual

| Ajuste | Valor |
|--------|--------|
| `EVAL_MODO` | `SIMULAR` |
| `[triggers] crons` | `[]` (sin programación) |
| `POST /run` | eval **SIMULAR** forzado |
| `POST /run-push` | dispara despacho (backend aplica ventana Lima) |
| Tick FCM 15s en Render | **apagado** (`worker-habilitado=false`) |

## Fases (activación futura, solo con aprobación)

| Cron UTC | Lima | Acción |
|----------|------|--------|
| `10 5 * * *` | 00:10 | `POST .../alertas/evaluar-programadas` |
| `0 10 * * *` | 05:00 | `POST .../push/despachar-recordatorios` |

El Worker usa `event.cron` para ramificar. Mismo secreto `X-MyCar-Cron-Secret`.

## Frecuencias push (mantenimiento)

- PROGRAMADA: sin push
- PROXIMA: 1× al entrar
- PENDIENTE: 1× + cada 3 días
- VENCIDA: 1× + diario (varias → 1 resumen/usuario)
- REALIZADA/ANULADA: detiene
- Máx. 1 push/alerta/día Lima; RF48 revalidado al enviar

Recordatorios de actualizar km (**categoría KILOMETRAJE**): no implementados; esquema extensible.

## Deploy sin activar horarios

```powershell
cd cloudflare\mycar-alertas-cron
npx wrangler secret put CRON_SECRET
npx wrangler deploy   # crons siguen []
```
