# Checklist E2E — Render + Neon + APK (post-autorización)

APK: `dist/MyCar-a2-contacto-59a0817-sidecar-hotfix-render.apk`  
Backend: `https://mycar-backend-1dxi.onrender.com/`  
Precondición: Flyway Neon = **26** + backend `59a0817+` desplegado.

Usar solo talleres/números de **prueba autorizados**. No llamar ni WhatsAppeara terceros reales.

| # | Caso | Pasos | Esperado | Real | Resultado |
|---|------|-------|----------|------|-----------|
| 1 | Health | GET `/actuator/health` (esperar cold start) | `UP` | | |
| 2 | Login | APK → iniciar sesión | Entra a inicio | | |
| 3 | Reabrir app | Cerrar proceso → abrir | Sesión OK o login limpio | | |
| 4 | Recovery | Flujo recuperar acceso | Completa sin 5xx | | |
| 5 | Taller manual | Crear con teléfono | Lista MANUAL OK | | |
| 6 | Manual sin contacto | Alerta → Contactar taller (manual) | Sin WA/Llamar; mensaje bloqueo | | |
| 7 | API redacta | Detalle alerta JSON (auth) | `telefono` null / `contactoAutorizado=false` si no autorizado | | |
| 8 | Catálogo global | Talleres → catálogo | Responde (no 5xx); demos solo piloto | | |
| 9 | Asociado no autorizado | Vincular global con `contacto_autorizado=false` | Visible; sin WA/Llamar | | |
| 10 | Asociado autorizado* | Solo si se autorizó flag en BD de prueba | WA + Llamar habilitados | | |
| 11 | Asociado inactivo | Retirar vínculo | Sin contacto aunque flag previo | | |
| 12 | Mantenimiento | Registrar con taller | Guarda OK | | |
| 13 | Historial | Consultar | Entrada visible | | |
| 14 | Alertas | Listado/detalle | Sin crash | | |
| 15 | Perfil | Abrir/editar | OK | | |
| 16 | FCM (opcional) | Sidecar | Puede fallar; no bloquea talleres | | |

\* No activar `contacto_autorizado` en producción sin autorización humana separada.
