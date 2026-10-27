"""RF01 only: disposable PostgreSQL 17 + V1-V4 + Java/HTTP tests. Never reads .env."""
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "backend" / "target"
TESTS = ("RegistrationValidatorTest,RegisterAccountTest,RegistrationWebTest,"
         "RegistrationIntegrationTest,RegistrationPrivacyUnavailableIntegrationTest")


def main():
    TARGET.mkdir(exist_ok=True)
    suffix = uuid.uuid4().hex[:12]
    container = "mycar-rf01-test-" + suffix
    database = "mycar_rf01_test_" + suffix
    env = os.environ.copy()
    env.update(POSTGRES_USER="mycar", POSTGRES_PASSWORD=secrets.token_urlsafe(32), POSTGRES_DB=database)
    started = False
    try:
        subprocess.run(["docker", "run", "--detach", "--name", container,
                        "--label", "mycar.purpose=rf01-disposable-test", "--publish", "127.0.0.1::5432",
                        "--env", "POSTGRES_USER", "--env", "POSTGRES_PASSWORD", "--env", "POSTGRES_DB",
                        "postgres:17"], env=env, check=True, stdout=subprocess.DEVNULL)
        started = True
        deadline = time.monotonic() + 60
        while time.monotonic() < deadline:
            ready = subprocess.run(["docker", "exec", container, "pg_isready", "-h", "127.0.0.1",
                                    "-U", "mycar", "-d", database], capture_output=True)
            if ready.returncode == 0:
                break
            time.sleep(0.5)
        else:
            raise RuntimeError("PostgreSQL de RF01 no quedó disponible")
        binding = subprocess.run(["docker", "port", container, "5432/tcp"], check=True,
                                 capture_output=True, text=True).stdout.strip()
        port = int(binding.rsplit(":", 1)[1])
        env.update(MYCAR_RF01_TEST_URL=f"jdbc:postgresql://127.0.0.1:{port}/{database}",
                   MYCAR_RF01_TEST_USER="mycar", MYCAR_RF01_TEST_PASSWORD=env["POSTGRES_PASSWORD"])
        # Config import is overridden by test annotations. Development .env is never needed.
        wrapper = ROOT / "backend" / ("mvnw.cmd" if os.name == "nt" else "mvnw")
        command = ["cmd", "/c", str(wrapper)] if os.name == "nt" else [str(wrapper)]
        log = TARGET / "rf01-test-run.log"
        with log.open("w", encoding="utf-8") as output:
            run = subprocess.run(command + ["-B", "-Dtest=" + TESTS, "test"], cwd=ROOT / "backend",
                                 env=env, stdout=output, stderr=subprocess.STDOUT)
        if run.returncode:
            raise RuntimeError(f"Falló RF01; revisar {log}")
        evidence = {"scope": "RF01", "privacy_version": "FIXTURE-NO-LEGAL (solo pruebas, sin validez legal)",
                    "database": "PostgreSQL 17 desechable; V1-V4 existentes; sin .env ni volumen compartido",
                    "tests": []}
        for name in TESTS.split(","):
            report = TARGET / "surefire-reports" / ("TEST-backend.registro." + name + ".xml")
            suite = ET.parse(report).getroot()
            item = {k: suite.get(k) for k in ("name", "tests", "failures", "errors", "skipped", "time")}
            if any(int(item[k]) for k in ("failures", "errors", "skipped")):
                raise RuntimeError("La suite debe pasar completa, sin omisiones: " + name)
            evidence["tests"].append(item)
        evidence["total"] = sum(int(s["tests"]) for s in evidence["tests"])
        evidence["result"] = "PASÓ"
        (TARGET / "rf01-evidencia.json").write_text(json.dumps(evidence, ensure_ascii=False, indent=2), encoding="utf-8")
        print(json.dumps(evidence, ensure_ascii=False, indent=2))
    finally:
        if started:
            # Verify exact ownership before removing only the disposable container created here.
            label = subprocess.run(["docker", "inspect", "--format", '{{index .Config.Labels "mycar.purpose"}}',
                                    container], capture_output=True, text=True, check=True).stdout.strip()
            if re.fullmatch(r"mycar-rf01-test-[a-f0-9]{12}", container) and label == "rf01-disposable-test":
                subprocess.run(["docker", "rm", "--force", "--volumes", container], check=True, stdout=subprocess.DEVNULL)
            else:
                raise RuntimeError("No se elimina un contenedor cuya identidad no coincide")


if __name__ == "__main__":
    main()
