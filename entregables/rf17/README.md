# Descargas RF17 — backend, API y Android

Esta carpeta contiene los tres ZIP de implementación para descargar y revisar. La base es la
rama `feat/mantenimientos-alertas-rf17-rf27`, commit `a5d2264`. No integra cambios posteriores de main.
Esta rama de entregables solo agrega archivos en `entregables/rf17/`; no aplica los ZIP al código del equipo.

## Descargar

- [01_RF17_BACKEND.zip](01_RF17_BACKEND.zip?raw=true): proyecto backend completo, fuentes y pruebas conservadas.
- [02_RF17_API.zip](02_RF17_API.zip?raw=true): copia de revisión de la capa HTTP; depende del backend.
- [03_RF17_ANDROID.zip](03_RF17_ANDROID.zip?raw=true): proyecto Android con los componentes RF17.

Si GitHub muestra la vista del archivo, usar **Download raw file** (icono de descarga).
No es necesario descargar otra vez todo el repositorio.

## Estado real

Backend/API y Android compilan. No se ejecutaron suites JUnit/JDBC en esta entrega ni se probó en dispositivo.
Se conservaron las migraciones V1–V4 y todas las pruebas existentes. Los ZIP no contienen .env, credenciales,
.git, target, build ni cachés del entorno.

RF17 todavía requiere integración con el equipo: identidad de Elsa, contrato común de idempotencia,
Retrofit autenticado, acceso desde Historial y navegación RF14. La API propuesta está deshabilitada por
defecto y el guardado HTTP se rechaza sin los adaptadores necesarios. No hay login ni datos simulados.
MainActivity y la navegación compartida se conservan; el host debe conectar la entrada de RF17.

Antes de aplicar archivos, descomprimir en una carpeta aparte y comparar con tu proyecto actual para
conservar los cambios de otras integrantes y cualquier avance posterior al commit de origen.

- [Resumen detallado y lista de archivos](RESUMEN_ENTREGA_RF17.md)
- [Contrato HTTP propuesto y bloqueos](CONTRATO_RF17_PROPUESTO.md)
- [Integración Android y callbacks](INTEGRACION_ANDROID_RF17.md)
- [SHA256 de los ZIP](SHA256SUMS.txt)
