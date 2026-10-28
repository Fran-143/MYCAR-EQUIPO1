# Resultados de actualización con main / RF18

Revisión 27/10/2026 (America/Lima). main 6fc5ad42a40b1091092d49d3b3e4e31bc508a6c9.

Se simuló el estado mostrado por Francesca: base 032aef2 más los 30 archivos de sesión sin commit.
En una copia aislada se verificó CONSERVAR.ps1, se creó un commit local temporal y se incorporó main.
Aparecieron únicamente los conflictos MainActivity.kt y HomeScreen.kt esperados.
RESOLVER.ps1 se probó en una segunda copia: produjo exactamente el mismo árbol Git que la combinación
verificada, hash bdf9392f956dd787c9aca8daa3ebcb1d7e43200b. No se publicó ningún commit de aplicación.

| Comprobación | Resultado |
|---|---|
| PostgreSQL 17 / migraciones y restricciones V1–V5 | 42 pruebas aprobadas |
| Validaciones, servicios, consultas, MVC e integración real de sesión RF08/RF09 | 215 pruebas aprobadas |
| RF18: servicio, controlador, DAO PostgreSQL y autenticación real | 30 pruebas aprobadas |
| Total backend de esta actualización | 287 pruebas, 0 fallos/errores/omitidas |
| Contexto Spring y HTTP real | PASÓ; health UP y cinco rutas de vehículos rechazan anónimos |
| Android unitarias (incluido historial) | 86 pruebas, 0 fallos/errores/omitidas |
| APK DEBUG y APK de pruebas | Compilan |
| Manifest RELEASE | Procesa correctamente |
| Launcher DEBUG y RELEASE | Único launcher: com.mycar.app.MainActivity |
| Lint DEBUG | 0 errores y 15 advertencias existentes |
| Flujo de auxiliares PowerShell | Ejecutado con PowerShell 7.4.6 en Linux, copias aisladas, hashes verificados |
| git diff --cached --check | Sin errores |

No se repitieron las 147 pruebas de Elsa para esta actualización; pasaron en la entrega previa de sesión.
Sí se comprobó su filtro/sesión real con las rutas de vehículos y del nuevo historial sobre PostgreSQL.
No se desactivaron filtros para hacer pasar pruebas. La propiedad de habilitación RF18 sigue siendo la de Nayu.

La prueba de historial utiliza fixtures únicamente en una BD mycar_mvp_test_rf18_* de un contenedor propio;
se comprobó su etiqueta y se retiró al terminar. El ejecutor RF08 también retiró su contenedor desechable.
No se utilizó la base personal de Francesca ni se insertaron vehículos de ejemplo en ella.

No se ejecutó connectedDebugAndroidTest aquí: requiere emulador, debe correr en la laptop tras incorporar RF18.
Que haya pasado antes de esta actualización no certifica las nuevas pruebas instrumentadas de historial.
No se declara que haya vehículos guardados en la BD de Francesca sin su consulta de verificación.
Los accesos a mantenimiento/historial conservan el requisito de sesión y repositorios reales de sus hosts.
Esta entrega combina navegación; no conecta esos dos clientes Android al ciclo de sesión de vehículos.

Cambios propios de resolución: cinco archivos, cuatro de navegación/manifest DEBUG y una nota de integración.
Los archivos RF18 y los ajustes condicionales de las cadenas de Elsa vienen de main, mediante Git merge.
Las migraciones V1–V5, catálogos, credenciales, .env y datos existentes permanecen intactos.
