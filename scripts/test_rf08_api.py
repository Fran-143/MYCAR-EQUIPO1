#!/usr/bin/env python3
"""Prueba BD, servicio, consultas y contrato MVC RF08 en PostgreSQL 17 desechable. No utiliza .env ni Compose local."""
import argparse
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys
import time
import urllib.request
import uuid


ROOT = Path(__file__).resolve().parents[1]


def run(args, **kwargs):
    return subprocess.run(args, check=True, text=True, **kwargs)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--maven-settings", help="settings.xml externo, si la red requiere proxy")
    parser.add_argument("--maven-repo", help="caché Maven opcional")
    parser.add_argument("--incluir-rf09", action="store_true", help="Añade el listado y las pruebas RF09")
    args = parser.parse_args()
    container = "mycar-mvp-test-" + uuid.uuid4().hex[:12]
    db_name = "mycar_mvp_test_" + uuid.uuid4().hex[:12]
    env = os.environ.copy()
    env["POSTGRES_PASSWORD"] = secrets.token_urlsafe(32)
    app = None
    started = False
    target = ROOT / "backend" / "target"
    target.mkdir(parents=True, exist_ok=True)
    try:
        run(["docker", "run", "--detach", "--name", container,
             "--label", "mycar.purpose=mvp-disposable-test",
             "--publish", "127.0.0.1::5432", "--env", "POSTGRES_PASSWORD",
             "--env", "POSTGRES_USER=mycar", "--env", "POSTGRES_DB=" + db_name,
             "postgres:17"], env=env, stdout=subprocess.DEVNULL)
        started = True
        deadline = time.monotonic() + 60
        while time.monotonic() < deadline:
            ready = subprocess.run(["docker", "exec", container, "pg_isready", "-h", "127.0.0.1",
                                    "-U", "mycar", "-d", db_name], capture_output=True)
            if ready.returncode == 0:
                break
            time.sleep(0.5)
        else:
            raise RuntimeError("PostgreSQL no quedó disponible en 60 segundos")
        binding = run(["docker", "port", container, "5432/tcp"], capture_output=True).stdout.strip()
        port = int(binding.rsplit(":", 1)[1])
        env.update(MYCAR_TEST_JDBC_URL=f"jdbc:postgresql://127.0.0.1:{port}/{db_name}",
                   MYCAR_TEST_USER="mycar", MYCAR_TEST_PASSWORD=env["POSTGRES_PASSWORD"])
        version = run(["docker", "exec", container, "psql", "-U", "mycar", "-d", db_name,
                       "-tAc", "SHOW server_version"], capture_output=True).stdout.strip()
        if not version.startswith("17."):
            raise RuntimeError("Se requiere PostgreSQL 17: " + version)
        print("PostgreSQL:", version, flush=True)
        wrapper = ROOT / "backend" / ("mvnw.cmd" if os.name == "nt" else "mvnw")
        command = (["cmd", "/c", str(wrapper)] if os.name == "nt" else [str(wrapper)])
        if args.maven_settings:
            command += ["-s", str(Path(args.maven_settings).resolve())]
        if args.maven_repo:
            command += ["-Dmaven.repo.local=" + str(Path(args.maven_repo).resolve())]
        run(command + ["-Pdb-mvp-tests", "verify"], cwd=ROOT / "backend", env=env)

        env["MYCAR_ENV_FILE"] = "optional:file:" + (target / "rf08-sin-env.properties").as_posix()

        # Ejecuta las validaciones existentes y el servicio RF08 real sobre la BD ya migrada.
        pruebas = "Validador*Test,Validacion*Test,RegistroVehiculoServicePostgresTest,VehiculosControllerTest,ConsultaRegistroVehiculoPostgresTest"
        if args.incluir_rf09:
            pruebas += ",ListadoVehiculosPostgresTest,ListadoVehiculosControllerTest"
        run(command + ["-Dtest=" + pruebas,
                       "-Dmycar.rf08.tests=true", "test"], cwd=ROOT / "backend", env=env)
        print("Servicio RF08" + ("/RF09" if args.incluir_rf09 else "") + ", consultas, contrato MVC y validaciones: PASÓ", flush=True)

        # Comprobar también la prueba Spring existente contra el mismo PostgreSQL aislado.
        env.update(MYCAR_ENV_FILE="optional:file:" + (target / "sin-env-local.properties").as_posix(),
                   SPRING_DATASOURCE_URL=env["MYCAR_TEST_JDBC_URL"], SPRING_DATASOURCE_USERNAME="mycar",
                   SPRING_DATASOURCE_PASSWORD=env["POSTGRES_PASSWORD"])
        context_log = target / "mvp-spring-context.log"
        with context_log.open("w", encoding="utf-8") as output:
            run(command + ["-Dtest=BackendApplicationTests", "test"], cwd=ROOT / "backend", env=env,
                stdout=output, stderr=subprocess.STDOUT)
        print("BackendApplicationTests: PASÓ", flush=True)

        # Arranque HTTP real. El log puede contener el password de desarrollo generado por Spring;
        # permanece en target (ignorado por Git) y nunca se imprime ni se publica.
        jar = ROOT / "backend" / "target" / "backend-0.0.1-SNAPSHOT.jar"
        app_log = target / "mvp-spring-http.log"
        with app_log.open("w", encoding="utf-8") as output:
            app = subprocess.Popen(["java", "-jar", str(jar), "--server.address=127.0.0.1", "--server.port=0"],
                                   cwd=ROOT / "backend", env=env, stdout=output, stderr=subprocess.STDOUT)
            deadline = time.monotonic() + 60
            health = None
            while time.monotonic() < deadline:
                if app.poll() is not None:
                    raise RuntimeError("El backend terminó antes de publicar health; revisar target/mvp-spring-http.log")
                content = app_log.read_text(encoding="utf-8", errors="replace")
                match = re.search(r"Tomcat started on port (\d+)", content)
                if match:
                    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
                    try:
                        with opener.open(f"http://127.0.0.1:{match.group(1)}/actuator/health", timeout=2) as response:
                            health = json.load(response)
                        if health.get("status") == "UP":
                            break
                    except (OSError, ValueError):
                        pass
                time.sleep(0.5)
            if not health or health.get("status") != "UP":
                raise RuntimeError("Health no devolvió UP en 60 segundos")
        # Seguridad REAL actual: sin credenciales ninguna ruta debe exponer datos ni aceptar altas.
        base_http = f"http://127.0.0.1:{match.group(1)}"
        rutas = [("GET", "/api/v1/vehiculos/catalogos"),
                             ("GET", "/api/v1/vehiculos/cupo-registro"),
                             ("GET", "/api/v1/vehiculos/operaciones/" + str(uuid.uuid4())),
                             ("POST", "/api/v1/vehiculos")]
        if args.incluir_rf09:
            rutas.append(("GET", "/api/v1/vehiculos"))
        for method, path in rutas:
            request = urllib.request.Request(base_http + path,
                data=b"{}" if method == "POST" else None, method=method,
                headers={"Accept": "application/json", "Content-Type": "application/json"})
            try:
                with opener.open(request, timeout=5) as response:
                    raise RuntimeError("Una ruta de vehículos aceptó una petición anónima")
            except urllib.error.HTTPError as rejected:
                if rejected.code not in (401, 403):
                    raise RuntimeError("Estado inesperado para petición anónima: " + str(rejected.code))
        print(f"HTTP vehículos: {len(rutas)} peticiones anónimas rechazadas (401/403).", flush=True)
        print("HTTP /actuator/health:", json.dumps(health), flush=True)
        print("PASÓ: BD MVP, servicio RF08, consultas, contrato MVC, contexto Spring y HTTP protegido.", flush=True)
        return 0
    finally:
        if app is not None and app.poll() is None:
            app.terminate()
            try:
                app.wait(timeout=15)
            except subprocess.TimeoutExpired:
                app.kill()
                app.wait(timeout=5)
        if started:
            cleanup = subprocess.run(["docker", "rm", "--force", "--volumes", container],
                                     stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True)
            if cleanup.returncode:
                print("No se pudo retirar el contenedor de pruebas " + container, file=sys.stderr)
            else:
                print("Contenedor y datos desechables retirados.", flush=True)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, subprocess.SubprocessError, RuntimeError) as error:
        print("FALLÓ: " + str(error), file=sys.stderr)
        sys.exit(1)
