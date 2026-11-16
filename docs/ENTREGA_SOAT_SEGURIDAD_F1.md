# Entrega Fase 1 — Seguridad SOAT

## 1. Medidas implementadas
- Máscara de póliza (últimos 4) + icono ojo; historial enmascarado; éxito post-alta enmascarado.
- Documento: revelación explícita; ocultar limpia bytes/temp; sin auto-descarga.
- AES-256-GCM (campos póliza/contratante + cuerpo documental) con nonce aleatorio y `keyId`.
- Índice ciego HMAC para duplicados de póliza.
- Upload/download autenticados (multipart + stream); sin URL pública al cliente en consulta.
- Fail-closed sin claves (`CIFRADO_NO_CONFIGURADO`).
- Backfill `SoatCifradoBackfill` para filas legacy.
- Titularidad existente reforzada; ADMIN sin bypass de documentos.

## 2. Archivos principales
Android: `SoatUiCommon`, `SoatDocumentoPreview`, `ConsultarSoatScreen`, `ConsultarSoatViewModel`, `SoatApi`, `RemoteSoatRepository`, `SoatFormulario.enmascararPoliza`.
Backend: `backend.crypto.*`, `V28__soat_cifrado_campos.sql`, `SoatJdbcDao`, `SoatService`, `SoatController`, `AlmacenamientoDocumentos` (+ Fake/Supabase), `SoatCifradoBackfill`, `docs/soat_cifrado.md`.

## 3. Claves
Env: `MYCAR_DATOS_SENSIBLES_ENABLED`, `KEY_ID`, `AES_KEY`, `BLIND_KEY` (Base64 32 bytes, distintas). Ver `docs/soat_cifrado.md`.
Limitación: operador con infra + claves puede descifrar.

## 4. Datos existentes
V28 + backfill al arrancar con cifrado habilitado. Respaldo obligatorio antes de migrate Neon. No se desplegó a producción en esta entrega.

## 5. Pruebas
- Unit: `DatosSensiblesCryptoTest`, `ValidadorSoatTest`, `SoatAutorizacionDocumentoTest`, `SoatFormularioTest` (máscara).
- IT: `Rf31Ea13RenovacionRollbackIT` adaptada (requiere JDBC + V28).
