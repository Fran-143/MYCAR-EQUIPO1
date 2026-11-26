# Guía Samsung — Planes UX + admin plan v48

## APK
- `dist/MyCar-Planes-UX-Admin-v48-Samsung.apk`
- `com.mycar.app.hotfix` · versionCode **48** · `1.2.24-rf27-ux-admin-plan`
- Backend local `http://127.0.0.1:8081` · sin Neon/Render/cobros reales

## Flujo usuario (Planes)
1. Entras a **Planes** y ves tu plan actual (FREE / Premium).
2. **Solicitar prueba (30 días)** → queda pendiente; al vencer vuelve a FREE.
3. **Simular pago Premium** → pago sandbox; Premium solo tras **aprobación admin**.
4. Si ya pediste: banner **Ya solicitaste Premium** + ver solicitud.

## Flujo admin
1. **Administración → Usuarios → Info → pestaña Suscripciones**
2. Ver plan, modalidad, fin, solicitud abierta.
3. **Aprobar** / **Rechazar** solicitud Premium simulada.
4. Si es prueba o sandbox activo: **Cancelar suscripción Premium** → FREE + conciliación vehículos.

## Cuentas de prueba
Admin → Pruebas locales → Escenarios → **Preparar cuentas Planes**

| Correo | Uso |
|--------|-----|
| `plan.free@mycar.test` | Solo FREE |
| `plan.prueba@mycar.test` | Solicitar prueba 30d |
| `plan.sandbox@mycar.test` | Simular pago |
| `plan.premium@mycar.test` | Flujo completo / cancelación |

Password: `PruebaCuentas35!`

## Perfil
Menú con iconos y colores por acción (contraseña, licencia, soporte, planes, etc.).
