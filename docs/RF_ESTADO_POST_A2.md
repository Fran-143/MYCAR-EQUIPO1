# RF — cerrados / parciales / siguientes (post Agente 2)

Fuente maestra en repo: `docs/requisitos/MyCar_63_RF_AUDITADOS_*` + guías `docs/vehiculos`.
Nota: `RF_SIGUIENTES_FRANCESCA.md` está desactualizado (aún lista RF12/RF24 como pendientes).

## Numeración contacto

- Contacto WhatsApp/llamada desde alerta = RF24 (EA contacto voluntario) + contrato V26.
- Catálogo/CRUD talleres = RF33–RF36 (migración `V26__rf33_taller_asociado_contacto.sql`).
- No confundir con SOAT RF31–33 del mapa antiguo de “dejar para después”.

## Cerrados en código local (no = probado en producción)

| RF | Nota |
|----|------|
| RF01–RF02–RF06 | Auth / verificación / recovery (BASE) |
| RF08–RF10, RF12–RF16 | Vehículos / km (parte ya en Render vía deploys previos) |
| RF18–RF21 | Historial / anular-modificar mant. (código + tests) |
| RF22–RF23 | Alertas / recordatorios (Live parcial; FCM en Render) |
| RF24 contacto | Política ASOCIADO+autorizado (`59a0817`) — E2E prod pendiente |
| RF33–RF36 | Talleres — E2E prod pendiente de V26+deploy |
| RF50 | Inicio/navegación |

## Parciales (prod)

| RF | Falta |
|----|-------|
| RF24 / RF33–36 Live | V26 Neon + redeploy Render + prueba APK E2E |
| FCM sidecar `.hotfix` | Cliente Firebase stub; push puede fallar |
| RF23 cron | Worker Cloudflare + secret; job Spring off en Render |

## Orden recomendado de los siguientes 3–5 RF (tras E2E talleres)

1. Cerrar E2E RF24 + RF33–36 (esta fase).
2. RF19 Detalle mantenimiento (si aún “próximamente” en UI).
3. RF46 / preferencias notificación — afinar UX sobre lo ya en BASE.
4. RF13 vehículo principal (si no quedó cerrado en RF12–16 Live).
5. Fotos / evidencia (RF38+) o backlog maestra — solo tras talleres estables en prod.

No iniciar implementación de RF nuevos hasta completar E2E post-deploy.
