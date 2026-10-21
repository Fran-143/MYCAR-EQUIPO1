\# MyCar — Equipo 1



\## Tecnologías



\- Android: Kotlin y Jetpack Compose.

\- Backend: Java 21 y Spring Boot 3.

\- Base de datos: PostgreSQL 17 mediante Docker.

\- Migraciones: Flyway.



\## Requisitos



\- Git.

\- Android Studio y sus componentes Android SDK.

\- JDK 21 para el backend.

\- Docker Desktop iniciado.

\- IntelliJ IDEA para trabajar en el backend.



\## Descargar el proyecto



```powershell

git clone https://github.com/Fran-143/MYCAR-EQUIPO1.git

cd MYCAR-EQUIPO1

```



\## Preparar PostgreSQL



Desde la carpeta principal del proyecto:



```powershell

Copy-Item .env.example .env

notepad .env

```



Reemplazar la contraseña de ejemplo por una propia.

No subir el archivo .env a GitHub.



Iniciar PostgreSQL:



```powershell

docker compose up -d --wait postgres

docker compose ps

```



El servicio postgres debe aparecer como healthy.



\## Ejecutar el backend



Desde la carpeta principal:



```powershell

cd backend

.\\mvnw.cmd spring-boot:run

```



Mantener esa ventana abierta.



Comprobar en el navegador:



http://localhost:8080/actuator/health



Respuesta esperada:



{"status":"UP"}



También se puede abrir la carpeta backend en IntelliJ.

Para ejecutar desde IntelliJ, configurar backend como

directorio de trabajo, porque la aplicación lee ../.env.



\## Ejecutar las pruebas del backend



Con PostgreSQL funcionando, desde la carpeta backend:



```powershell

.\\mvnw.cmd verify

```



Resultado esperado: BUILD SUCCESS.



\## Ejecutar Android



1\. Abrir la carpeta android en Android Studio.

2\. Esperar la sincronización de Gradle.

3\. Iniciar un emulador.

4\. Ejecutar la configuración app.



\## Organización



\- android/: aplicación móvil.

\- backend/: servidor.

\- compose.yml: PostgreSQL local.

\- .env.example: configuración de ejemplo.

\- .env: configuración privada de cada integrante.



\## Trabajo en equipo



Crear una rama para cada tarea, subir los cambios y abrir

un Pull Request hacia main para que otro integrante revise.



Los cambios de base de datos se guardan como migraciones

en backend/src/main/resources/db/migration.



No modificar migraciones ya compartidas y aplicadas.

Coordinar los números de las siguientes migraciones.



\## Estado inicial



La app muestra la pantalla de inicio.

El backend dispone de una comprobación de estado.

PostgreSQL está conectado y Flyway administra las migraciones.



Las funcionalidades de negocio se desarrollarán después.

