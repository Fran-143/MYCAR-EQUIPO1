# Guía Samsung — Planes/Suscripciones Sandbox hotfix v46

## Artefacto

- APK: `dist/MyCar-Planes-Suscripciones-Sandbox-v46-Samsung.apk`
- `applicationId`: `com.mycar.app.hotfix`
- `versionCode`: 46
- API: `http://127.0.0.1:8081/` (requiere `adb reverse`)
- Dispositivo de referencia: `RFCX406KNYW`

## Prerrequisitos

1. PostgreSQL local `mycar_mvp` + Flyway **V46** aplicada.
2. Backend local `:8081` con `mycar.local-test.enabled=true`.
3. Para **Probar Premium**:
   - SQL de habilitación en `CONFIGURACION_PENDIENTE_PLANES_SANDBOX.md`
   - env `MYCAR_PLANES_SANDBOX_VIGENCIA=PT72H` (o Duration ISO-8601 tuya)
4. USB debugging + dispositivo autorizado.

## Instalación

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" reverse tcp:8081 tcp:8081
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s RFCX406KNYW install -r `
  dist\MyCar-Planes-Suscripciones-Sandbox-v46-Samsung.apk
```

Opcional: dejar `scripts/mantener-adb-reverse.ps1` abierto.

## Flujo usuario (RF27/28/29)

1. Login con cuenta fixture local (`rf.activa@mycar.test` / `PruebaCuentas35!` tras restablecer fixtures).
2. Perfil → **Planes y suscripción**.
3. Ver catálogo FREE/PREMIUM y plan actual.
4. Hotfix: **Probar Premium (pago simulado)** → resumen → escenarios APROBADA/RECHAZADA/PENDIENTE/CANCELADA.
5. **Mi suscripción e historial** para fechas (America/Lima) y eventos.

En APK general (sin `LOCAL_TEST_TOOLS`) la contratación muestra **Próximamente**.

## Flujo admin (RF57)

1. Admin + MFA step-up (igual que el resto del hub).
2. Administración → **Suscripciones** (ya no Próximamente).
3. Resumen, filtros, detalle solo lectura, **Verificar estado** (simulador → mensaje fijo; MP TEST → S2S si hay credenciales).

## Notas

- No hay cobros reales. Contadores sandbox ≠ ingresos.
- Mercado Pago TEST queda apagado sin credenciales (`PROVEEDOR_NO_CONFIGURADO`).
