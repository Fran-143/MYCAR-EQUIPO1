# Verificación — permiso de red local

28/10/2026, America/Lima. Base: integración Elsa/RF50 entregada previamente, árbol Git 373d5940.
Cinco archivos: MainActivity y su prueba de navegación modificados; política, pruebas unitarias y guía nuevas.

- Android unitarias: 208 pruebas, 0 fallos/errores/omitidas (200 anteriores + 8 de política de permiso).
- DEBUG APK y APK de pruebas: compilan.
- Kotlin RELEASE: compila.
- Lint: 0 errores, 17 advertencias existentes.
- Pruebas de política: DEBUG/Release, API anterior/actual/posterior, host local/remoto,
  una ruta con nombre similar al host y configuración inválida.
- Instalador ejecutado en dos copias aisladas: integración ya confirmada y merge pendiente sin conflictos.
- Archivos instalados: hashes de los cinco archivos iguales a las fuentes verificadas.
- git diff --cached --check: pasó.

No hay emulador aquí. Las pruebas instrumentadas se compilaron, no se ejecutaron.
La solicitud real de permiso y el registro contra el servidor personal siguen pendientes en la laptop.
El instalador se ejecutó con PowerShell 7/Linux y usa sintaxis compatible con Windows PowerShell 5.1.
No se modifica código del backend ni PostgreSQL. No hay .env, credenciales, commit/push de aplicación
ni merge hacia main; únicamente ZIP y documentación en la rama de descargas autorizada.
