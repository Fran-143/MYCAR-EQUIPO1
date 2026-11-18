# Hotfix RF52 — registro TIVE en APK general (sin tocar producción aún)

## Causa raíz confirmada

1. **Cliente Android (timeout):** `ClienteVehiculosAutenticado` usaba `callTimeout(30s)`. En Render Free el arranque en frío supera ese umbral (sondeo POST sin auth llegó a cortar a 90s). El formulario puede abrir tras un GET ya caliente, pero el `POST /api/v1/vehiculos/{id}/tive` se aborta → **no hay commit** en Neon.
2. **Evidencia Neon (solo SELECT):** `tive` = 0 filas; `operacion_idempotente` sin filas `REGISTRAR_TIVE` (la reserva va en la misma TX y se revierte si el request no confirma).
3. **Backend (enmascaramiento):** `TiveService.registrar` convertía cualquier `DataIntegrityViolationException` en `TIVE_EXISTE` (HTTP 409), ocultando CHECK/FK/idempotencia reales.
4. **Android (UX):** el banner de error se ocultaba si venían `campos` (`mensaje` solo si `errores.isEmpty()`), y VIN/placa de solo lectura no mostraban error de campo → sensación de “no guarda” sin mensaje claro.
5. **Post-éxito frágil:** tras HTTP 201, el cliente exigía un GET de consulta; si ese GET fallaba, se reportaba fallo aunque el POST hubiera persistido.

**Capa principal:** Android (timeout + UX) + Spring (`TiveService` / integridad). Neon/schema V32 OK para vehículos con VIN válido (ids 1, 2, 9). Almacenamiento: el registro **sin documento** no llama a storage; con documento depende de `MYCAR_DOCUMENTOS_MODO=supabase` en Render (mismo que SOAT).

## HTTP / negocio observados

| Señal | Valor |
|-------|--------|
| Neon TIVE | 0 filas |
| Neon ops REGISTRAR_TIVE | ninguna |
| POST sin auth (sondeo) | timeout largo en frío / luego 401 en upload |
| Cliente APK general | URL `https://mycar-backend-1dxi.onrender.com/`, JWT vía interceptor |

No se reenviaron POST autenticados contra producción.

## Archivos corregidos (rama `feat/tive-soporte-vin`)

- `backend/.../tive/service/IntegridadTive.java` (nuevo)
- `backend/.../tive/service/TiveService.java`
- `backend/.../tive/api/ErroresTiveController.java`
- `backend/.../tive/IntegridadTiveTest.java`
- `android/.../ClienteVehiculosAutenticado.kt` (timeout 90s)
- `android/.../tive/data/RemoteTiveRepository.kt`
- `android/.../tive/viewmodel/TiveFormViewModels.kt`
- `android/.../tive/ui/TiveFormScreen.kt`
- `android/.../vehiculos/domain/RegistroVehiculoRepository.kt` (`incierto`)

## Neon / Render

**Sin cambios** en este hotfix: no migrate, no push a `deploy/render-free`, no redeploy.

## Publicación segura (cuando autorices)

1. Confirmar backup fresco (o reutilizar `20261118_022801` si no hubo escrituras TIVE; preferible uno nuevo).
2. Commit en `feat/tive-soporte-vin`.
3. Push a `deploy/render-free` **solo con tu OK** → redeploy Render.
4. Generar nuevo APK general v14 (misma firma Debug, URL Render) **sin borrar** `MyCar-General-TIVE-Soporte.apk`.
5. Verificar: health → login → precheck → registrar **sin** documento en vehículo con VIN OK → consultar → opcional con documento.
6. Rollback código: volver `deploy/render-free` a `8b1f98a`. Datos: dump gate.
