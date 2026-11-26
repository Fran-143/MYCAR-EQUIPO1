# Borrador — GitHub Release MyCar v1.0.8 (NO PUBLICAR AÚN)

## Metadatos propuestos

- **Tag:** `v1.0.8` (crear solo al publicar)
- **Título:** MyCar v1.0.8 — Primera versión pública (borrador)
- **Target commit:** tip de `release/v1.0.8` (incluye backend `e72a3b5` + Android 1.0.8)
- **Estado:** draft / pre-release recomendado hasta revisión humana

## Artefactos locales (adjuntar al publicar)

| Archivo | Ruta |
|---|---|
| APK | `C:\Proyectos\MyCarEquipo1-talleres-apk\dist\release\MyCar-v1.0.8-release.apk` |
| SHA-256 | `C:\Proyectos\MyCarEquipo1-talleres-apk\dist\release\MyCar-v1.0.8-release.sha256.txt` |

- **versionName:** 1.0.8  
- **versionCode:** 79  
- **applicationId:** `com.mycar.app`  
- **SHA-256 APK:** `7fb77a98dc1534a1e32517b045ced6102405abf6bfe7ccc865e5407a9fcb92c5`  
- **Firma:** CN=MyCar Release, OU=MyCar, O=MyCar, L=Lima, ST=Lima, C=PE  
- **Cert SHA-256:** `b71e6b47bfd91d741433287d5750ca75d2f5909317ceaf6d88b9717b574345fd`

## Notas de la versión (texto sugerido)

Proyecto académico MyCar. Primera publicación pública candidata.

### Incluye

- Vehículos, kilometraje, mantenimientos, SOAT, TIVE, licencia.
- Alertas y notificaciones push (FCM).
- Plan Free (1 vehículo ACTIVO) y Premium simulado (sin cobro real).
- Prueba gratuita 14 días (una vez; vence sola).
- Pago sandbox: vigente hasta que administración apague el plan.
- Solicitudes admin: solo pendientes; lo resuelto se administra en Suscripciones.

### No incluye / aclaraciones

- No hay pagos reales ni pasarela de cobro.
- No es una certificación legal ni producto comercial final.

## Pasos exactos para publicar (cuando apruebes)

```powershell
cd C:\Proyectos\MyCar-release-v108
git push -u origin release/v1.0.8

# Con GitHub CLI (si disponible):
gh release create v1.0.8 `
  --draft `
  --title "MyCar v1.0.8 — Primera versión pública" `
  --notes-file docs/RELEASE_DRAFT_v1.0.8.md `
  "C:\Proyectos\MyCarEquipo1-talleres-apk\dist\release\MyCar-v1.0.8-release.apk" `
  "C:\Proyectos\MyCarEquipo1-talleres-apk\dist\release\MyCar-v1.0.8-release.sha256.txt"
```

O desde la web: Releases → Draft a new release → tag `v1.0.8` → adjuntar APK + SHA-256 → guardar como **draft**.

**No publicar** hasta revisión explícita.
