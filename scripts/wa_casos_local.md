# Casos WhatsApp (local / aislado — NO Neon prod sin OK)

Número de laboratorio del equipo: `956188469` (no insertar autorizado en Neon sin OK).

## Caso A — Taller manual `Taller Prueba`
En la app (sesión real):
1. Mantenimientos → pestaña **Talleres**
2. **Agregar** → Manual
3. Nombre: `Taller Prueba` · Teléfono: `956188469` · marcar WhatsApp si quieres
4. Guardar

**Esperado:** en Detalle de alerta, si eliges este taller → **sin** botón Contactar / WA / Llamar.

## Caso B — Asociado sin autorización
Solo con backend local + admin secret:
```powershell
$env:MYCAR_TALLER_ADMIN_SECRET='secreto-local'
.\scripts\taller_admin_registrar.ps1 -BaseUrl http://127.0.0.1:8081 `
  -Nombre "MyCar Prueba WA" -Telefono 956188469 -TieneWhatsapp
# contactoAutorizado queda false
```
Vincular desde catálogo asociado en la app.

**Esperado:** taller visible; contacto bloqueado; sin teléfono accionable.

## Caso C — Asociado autorizado (solo local)
```powershell
.\scripts\taller_admin_registrar.ps1 -BaseUrl http://127.0.0.1:8081 `
  -Nombre "MyCar Prueba WA Auth" -Telefono 956188469 -TieneWhatsapp -ContactoAutorizado
```
O seed local: `mycar.taller-piloto.habilitado=true` + WhatsApp + `contacto-autorizado=true` (nunca contra Neon prod).

**Esperado:** Contactar taller → sheet WhatsApp (`wa.me/51956188469`) + Llamar (`tel:+51956188469`).

## Unit tests (sin dispositivo)
```powershell
cd android
.\gradlew.bat testDebugUnitTest --tests "com.mycar.app.features.talleres.WhatsAppCasosABCTest" --tests "com.mycar.app.features.talleres.PoliticaContactoTallerTest"
```

## Neon/Render (pendiente de tu OK)
1. Deploy backend con endpoint admin.
2. `MYCAR_TALLER_ADMIN_SECRET` en Render.
3. Registrar **un** taller controlado + `-ContactoAutorizado` solo con autorización explícita.
