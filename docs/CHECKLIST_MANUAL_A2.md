# Checklist manual APK — Agente 2 (`59a0817`)

APK: `dist/MyCar-a2-contacto-59a0817-sidecar-hotfix-render.apk` (`com.mycar.app.hotfix`)  
o `dist/MyCar-a2-contacto-59a0817-debug-render.apk` (`com.mycar.app`)  
Backend: `https://mycar-backend-1dxi.onrender.com/`

**Limitación:** Render aún no tiene V26 ni el backend con `contactoAutorizado`. Casos 6–9 de asociado autorizado quedarán BLOQUEADOS en producción hasta migrate+redeploy. En local/Docker sí se validó la política.

| # | Caso | Pasos | Esperado | Real | Resultado |
|---|------|-------|----------|------|-----------|
| 1 | Iniciar sesión | Abrir APK → login con cuenta de prueba | Entra a inicio | _tú_ | |
| 2 | Cerrar y reabrir | Forzar cierre → abrir de nuevo | Sesión restaurada o login limpio | _tú_ | |
| 3 | Recuperar sesión | Flujo “olvidé contraseña” / recovery | Códigos y reset OK | _tú_ | |
| 4 | Registrar taller manual | Talleres → nuevo → guardar con teléfono | Aparece en lista MANUAL | _tú_ | |
| 5 | Manual sin contacto | Detalle alerta → contacto con taller manual | Sin botones WA/llamada; mensaje de no autorizado | _tú_ | |
| 6 | Asociado no autorizado | Catálogo/vínculo ASOCIADO (requiere V26 Live) | Visible, sin WA/llamada | — | BLOQUEADO sin V26 |
| 7 | WA/llamada bloqueados | Igual #6 | Sin botones / sin teléfono accionable en API | — | BLOQUEADO sin V26 |
| 8 | Asociado autorizado | Solo tras autorización humana + V26 | Botones WA y Llamar | — | BLOQUEADO sin V26 |
| 9 | Botones OK | Pulsar WA / Llamar | Abre WhatsApp / marcador | — | BLOQUEADO sin V26 |
| 10 | Mant. con taller | Registrar mantenimiento eligiendo taller | Guarda OK | _tú_ | |
| 11 | Historial | Ver historial del vehículo | Entrada visible | _tú_ | |
| 12 | Alertas | Listado / detalle alerta | Sin crash | _tú_ | |
| 13 | Perfil | Abrir perfil | Datos / edición OK | _tú_ | |
| 14 | Navegación | Recorrer menús principales | Sin errores inesperados | _tú_ | |

Marca PASS / FAIL / BLOQUEADO en la última columna al probar en el teléfono.
