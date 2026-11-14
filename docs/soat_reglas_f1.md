# SOAT RF30 / RF64 / RF31 / RF32 — reglas fijadas (Fase 1)

Fuentes: `SOAT.pdf` (3 páginas, visual), `MyCar_Maestra_64RF_SOAT_Flujos_Definitivos.xlsx`,
`MyCar_MAESTRA_64RF_DIAGRAMAS_BCE_FORMATO_ORIGINAL_FINAL.xlsx`,
`MyCar_EA_487_DIAGRAMAS_BCE_FORMATO_ORIGINAL_FINAL.xlsx` (`SOAT_EA_DEFINITIVOS`).

## Numeración oficial

| RF | Nombre |
|----|--------|
| RF30 | Registrar SOAT |
| RF31 | Corregir datos **o** renovar SOAT (ramas excluyentes) |
| RF32 | Configurar recordatorio |
| RF64 | Consultar SOAT |

El PDF usa encabezados antiguos; no renumerar.

## Campos

Obligatorios: aseguradora, número de póliza, fecha inicio, fecha vencimiento.
Opcionales: fecha emisión, contratante, prima (≥0), documento JPG/PNG/PDF (imagen ≤5 MB, PDF ≤10 MB).
Un único SOAT `ACTUAL` por vehículo; anteriores `HISTORICO`. Estado de cobertura derivado: `POR_INICIAR` / `VIGENTE` / `VENCIDA` (no confundir con vigencia documental ACTUAL).
Sin “póliza verificada”. Anticipación recordatorio: **30 días** (PROXIMA). Activación recordatorio = operación separada post-registro.

## Contradicción documentada (navegación)

| Fuente | Acceso Corregir / Renovar / Recordatorio |
|--------|------------------------------------------|
| Maestra RF31/RF32 | Desde **Consultar SOAT (RF64)**; botones Renovar/Corregir en la misma pantalla; sin hub intermedio. |
| Plan aprobado (APK) | Detalle → **Configurar SOAT** (hub) → Corregir / Renovar / Recordatorio; también Consultar desde Detalle. |

**Decisión de implementación (negocio no cambia):** seguir el **plan aprobado** y `SOAT.pdf`:

`Detalle vehículo → Configurar SOAT → Corregir / Renovar / Configurar recordatorio`.

**Consultar SOAT (RF64) es solo lectura** (+ acceso a Configurar). La maestra colocaba Corregir/Renovar dentro de Consultar; se documenta la diferencia y no se altera el resto del BCE.

Registro + recordatorio: el toggle de revisión es preferencia UI; **primero** se confirma RF30/RF31 Renovar; **después** PUT recordatorio independiente. Si falla el recordatorio, el SOAT permanece y la confirmación muestra el estado real.
