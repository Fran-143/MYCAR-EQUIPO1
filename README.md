# MyCar

Aplicación Android de gestión vehicular académica: registro de vehículos,
kilometraje, mantenimientos, documentos (SOAT, TIVE, licencia), alertas y
notificaciones. Proyecto universitario — **no es un producto comercial** y
**no procesa pagos reales**.

## Versión actual

| Ítem | Valor |
|---|---|
| App | **MyCar 1.0.8** (`versionCode` **79**) |
| applicationId | `com.mycar.app` |
| Backend Render | rama `deploy/render-free` (tip reciente: planes Free/Premium simulados) |
| API | `https://mycar-backend-1dxi.onrender.com` |

APK release verificada (local):

`dist/release/MyCar-v1.0.8-release.apk`

## Tecnologías

- Android: Kotlin, Jetpack Compose.
- Backend: Java 21, Spring Boot 3.x.
- Base de datos: PostgreSQL (Neon en hosting; Docker en local).
- Migraciones: Flyway en `backend/src/main/resources/db/mvp`.
- Push: Firebase Cloud Messaging (datos / outbox).
- Cron alertas: Cloudflare Worker (opcional).

## Funcionalidades implementadas

- Cuenta: registro, login, cierre de sesión, recuperación, privacidad 1.0.
- Vehículos: alta, listado, detalle, estado activo/inactivo, eliminación lógica (RF62).
- Kilometraje: actualización y corrección con reglas de negocio.
- Mantenimientos: registro, historial, anulación; export PDF Premium.
- SOAT y recordatorios asociados.
- TIVE / soporte de corrección VIN (flujos de incidencias).
- Licencia de conducir y recordatorios.
- Alertas preventivas (programación y consulta) + notificaciones en app / FCM.
- Fotografías de perfil y de vehículo (almacenamiento controlado).
- Administración: usuarios, catálogos, incidencias, **suscripciones**.

## Planes Free y Premium (simulado)

| Plan | Reglas |
|---|---|
| **Free** | Cupo de registro limitado; **solo 1 vehículo ACTIVO**. Si hay más, se conserva el principal ACTIVO y el resto pasa a **INACTIVO** (no se borran ni pierden historial). |
| **Prueba gratuita** | Una sola vez por cuenta · **14 días** · requiere aprobación admin · vence sola → Free. |
| **Premium sandbox** | Solicitud de pago **simulado** (sin cobro real) · admin aprueba/rechaza · vigente **hasta que admin apague** el plan. |

Cola admin **Solicitudes**: solo pendientes. Lo aprobado/rechazado se administra en **Suscripciones** (p. ej. apagar plan).

## Cómo conseguir la APK

1. Compilar release en Android Studio / Gradle (`:app:assembleRelease`), o
2. Usar el artefacto versionado en Releases (cuando se publique el draft `v1.0.8`).

Firma release: `CN=MyCar Release` (keystore del equipo; **no** se publica en el repo).

## Instalación (desarrollo)

```powershell
git clone https://github.com/Fran-143/MYCAR-EQUIPO1.git
cd MYCAR-EQUIPO1
Copy-Item .env.example .env
# Completar secretos locales; NUNCA subir .env
docker compose up -d --wait postgres
cd backend
.\mvnw.cmd spring-boot:run
```

Android Studio → abrir `android` → run debug apuntando al backend local o a Render.

## Política de Privacidad

Texto embebido en la app (`android/app/src/main/res/raw/mycar_privacy_1_0.txt`) y pantallas de consentimiento en registro / ajustes.

## Seguridad del repositorio

No subir: keystores, `.env`, credenciales Neon/Supabase/Firebase Admin, `CRON_SECRET`, Brevo, tokens, PII, fotos de usuarios ni respaldos de producción. Ver `.gitignore`.

## Ramas

- `main`: integración del equipo (actualizar vía PR desde `deploy/render-free` / `release/v1.0.8`).
- `deploy/render-free`: despliegue backend en Render.
- `release/v1.0.8`: empaquetado Android 1.0.8 + backend alineado.

## Documentación adicional

- `docs/` — guías por módulo (vehículos, alertas, inicio, planes).
- `docs/RELEASE_DRAFT_v1.0.8.md` — borrador de GitHub Release (**no publicar** sin revisión).
