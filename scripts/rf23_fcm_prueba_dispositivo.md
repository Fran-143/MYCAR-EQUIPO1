# Checklist — prueba FCM real (1 dispositivo) — NO ejecutar sin autorización

Objetivo: un solo push real a un token de prueba. Sin Cron prod. Sin ráfaga.

## Precondiciones

- [ ] Backend Render con FCM SA y `mycar.fcm.habilitado=true`
- [ ] Usuario de prueba: RF48 `mantenimiento_on=true`, `permiso_dispositivo=CONCEDIDO`
- [ ] App instalada; token en `dispositivo_push` activo
- [ ] Hora Lima entre 05:00 y 21:59
- [ ] V25 aplicada; Worker puede seguir en SIMULAR / crons=[]

## Pasos (manual)

1. Confirmar 0 filas outbox PENDIENTE del usuario (o anotar ids).
2. `POST /api/v1/interno/push/despachar-recordatorios` con `X-MyCar-Cron-Secret`  
   **o** generar transición PROXIMA/PENDIENTE controlada en vehículo de prueba.
3. Verificar en Neon: exactamente **1** fila nueva `ENVIADO` (o resumen) para ese usuario/día.
4. Verificar push único en el teléfono.
5. Segundo `POST` despacho mismo día → `encolados=0` (sin duplicado).

## Abortar si

- Aparece más de un push
- Se encolan usuarios no de prueba
- Hora fuera de 05:00–21:59
