# Guía Samsung — Planes + aprobación admin v47

## APK

- `dist/MyCar-Planes-Aprobacion-Admin-v47-Samsung.apk`
- `com.mycar.app.hotfix` · versionCode **47**
- Backend: `http://127.0.0.1:8081/` + `adb reverse tcp:8081 tcp:8081`
- Dispositivo: `RFCX406KNYW`

## Config local previa

Ver `CONFIGURACION_PENDIENTE_PLANES_SANDBOX.md` (SQL demo + env vigencia/prueba).

## Caso A — FREE

1. Login `rf.activa@mycar.test` / `PruebaCuentas35!` (tras fixtures).
2. Perfil → Planes → tarjeta FREE / plan actual.
3. Mi suscripción → FREE, sin Premium.

## Caso B — Premium gratuito

1. **Solicitar prueba gratuita** → Enviar.
2. Ver “Solicitud enviada” / pendiente.
3. Usuario sigue FREE.
4. Login ADMIN + MFA.
5. Administración → Suscripciones → pestaña **Solicitudes Premium**.
6. Abrir solicitud → **Aprobar** (MFA ≤10 min).
7. Volver al usuario → Premium prueba activa.

## Caso C — Premium Sandbox

1. Usuario FREE → **Contratar Premium — Sandbox**.
2. Simular **APROBADA**.
3. Mensaje: pago OK, **pendiente de aprobación**; plan sigue FREE.
4. ADMIN + MFA → abrir solicitud → verificar operación (simulador) → **Aprobar**.
5. Usuario → `PREMIUM_SANDBOX_ACTIVA`.

## Caso D — Vencimiento

1. Con Premium activo (fixture local).
2. Endpoint local-test `POST .../pagos-prueba/forzar-vencimiento` o esperar job.
3. Regresa FREE; solo principal ACTIVO; historiales intactos.

## Rechazo

En detalle de solicitud pendiente → **Rechazar** con motivo obligatorio → usuario FREE + motivo en historial.
