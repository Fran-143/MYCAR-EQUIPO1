# API Licencia de conducir (`/api/v1/licencia`)

Autenticación: sesión `Bearer` (`ROLE_USUARIO`). Solo el titular accede a su licencia y fotos (admin sin acceso).

## Endpoints

| Método | Ruta | Descripción |
|--------|------|-------------|
| `GET` | `/api/v1/licencia` | Licencia actual, `tieneHistorial` y resúmenes históricos (sin fotos ni número completo). |
| `POST` | `/api/v1/licencia` | Registrar licencia (`idOperacion` UUID). |
| `PUT` | `/api/v1/licencia` | Editar la licencia actual (sin historial de cambios). |
| `POST` | `/api/v1/licencia/renovar` | Renovar (idempotente; archiva la anterior). |
| `POST` | `/api/v1/licencia/fotos/upload` | Subida multipart (`lado=FRONTAL\|POSTERIOR`). |
| `GET` | `/api/v1/licencia/fotos/{lado}` | Descarga de bytes cifrados (solo titular). |
| `PUT` | `/api/v1/licencia/recordatorio` | Activar/desactivar recordatorio (alerta `LICENCIA`, sin vehículo). |

## Validación

- **Categoría:** `A-I` o `A-IIa`.
- **Número:** tras `trim` + mayúsculas debe cumplir `^[A-Z][0-9]{8}$`. Mensaje:  
  `El número de licencia debe contener una letra seguida de 8 números, sin guiones. Ejemplo: Q12345678.`
- **Emisión:** obligatoria, ≤ hoy (America/Lima).
- **Vencimiento:** obligatorio, posterior a emisión, entre `emisión + 6 meses` y `emisión + 10 años` (inclusive). Mensaje de rango:  
  `La fecha de vencimiento debe estar entre 6 meses y 10 años después de la emisión.`
- **Alta/renovación:** no se admite vencimiento pasado.
- **Edición:** admite vencimientos históricos ya vencidos.
- **Renovación:** nuevo vencimiento &gt; vencimiento anterior.
- **Fotos:** JPG/PNG/WebP ≤ 5 MB; frontal y posterior obligatorias al registrar; en renovación son opcionales y no se reutilizan claves de storage anteriores.
- **Cifrado:** número y bytes de fotos (AES-GCM vía `mycar.datos-sensibles.*`). Índice ciego por usuario para unicidad del número.

## Storage

Rutas: `licencia/{idUsuario}/temp/...` (presubida) y `licencia/{idUsuario}/{idLicencia}/...`.