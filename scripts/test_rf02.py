"""RF02 + RF01 regression in two databases of an owned disposable PostgreSQL 17 container."""
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
TARGET=ROOT/"backend"/"target"
RF02=["backend.autenticacion.LoginValidatorTest","backend.autenticacion.SessionCredentialsTest",
      "backend.autenticacion.LoginFlowTest","backend.autenticacion.AuthenticationIntegrationTest"]
RF01=["backend.registro.RegistrationValidatorTest","backend.registro.RegisterAccountTest",
      "backend.registro.RegistrationWebTest","backend.registro.RegistrationIntegrationTest",
      "backend.registro.RegistrationPrivacyUnavailableIntegrationTest"]

def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    TARGET.mkdir(exist_ok=True)
    evidence_path=TARGET/"rf02-evidencia.json"
    # Do not leave an old passing result if this run fails.
    if evidence_path.exists():evidence_path.unlink()
    protected=[*sorted((ROOT/"backend/src/main/java/backend/registro").rglob("*.java")),
               *sorted((ROOT/"backend/src/test/java/backend/registro").rglob("*.java")),
               *sorted((ROOT/"backend/src/main/resources/db/mvp").glob("*.sql"))]
    before={str(p.relative_to(ROOT)):digest(p) for p in protected}
    suffix=uuid.uuid4().hex[:12]
    container="mycar-rf02-test-"+suffix
    db02="mycar_rf02_test_"+suffix
    db01="mycar_rf01_test_"+suffix
    env=os.environ.copy()
    env.update(POSTGRES_USER="mycar",POSTGRES_PASSWORD=secrets.token_urlsafe(32),POSTGRES_DB=db02)
    started=False
    try:
        subprocess.run(["docker","run","--detach","--name",container,"--label","mycar.purpose=rf02-disposable-test",
                        "--publish","127.0.0.1::5432","--env","POSTGRES_USER","--env","POSTGRES_PASSWORD",
                        "--env","POSTGRES_DB","postgres:17"],env=env,check=True,stdout=subprocess.DEVNULL)
        started=True
        deadline=time.monotonic()+60
        while time.monotonic()<deadline:
            if subprocess.run(["docker","exec",container,"pg_isready","-h","127.0.0.1","-U","mycar","-d",db02],capture_output=True).returncode==0:break
            time.sleep(.5)
        else:raise RuntimeError("Disposable PostgreSQL did not become ready")
        subprocess.run(["docker","exec",container,"createdb","-U","mycar",db01],check=True)
        binding=subprocess.run(["docker","port",container,"5432/tcp"],capture_output=True,text=True,check=True).stdout.strip()
        port=int(binding.rsplit(":",1)[1])
        for scope,database in [("RF02",db02),("RF01",db01)]:
            env["MYCAR_"+scope+"_TEST_URL"]=f"jdbc:postgresql://127.0.0.1:{port}/{database}"
            env["MYCAR_"+scope+"_TEST_USER"]="mycar"
            env["MYCAR_"+scope+"_TEST_PASSWORD"]=env["POSTGRES_PASSWORD"]
        env["MYCAR_RF02_TEST_SIGNING_KEY"]=base64.b64encode(secrets.token_bytes(32)).decode("ascii")
        wrapper=ROOT/"backend"/("mvnw.cmd" if os.name=="nt" else "mvnw")
        command=["cmd","/c",str(wrapper)] if os.name=="nt" else [str(wrapper)]
        tests=",".join(name.rsplit(".",1)[1] for name in RF02+RF01)
        with (TARGET/"rf02-test-run.log").open("w",encoding="utf-8") as log:
            result=subprocess.run(command+["-B","-Dtest="+tests,"test"],cwd=ROOT/"backend",env=env,stdout=log,stderr=subprocess.STDOUT)
        if result.returncode:raise RuntimeError("RF02/regression failed: inspect backend/target/rf02-test-run.log")
        evidence={"status":"PASÓ","database":"Two disposable PostgreSQL 17 databases; only existing V1-V4; no .env or shared volume",
                  "contracts":"RF02 provisional, not frozen", "pending":["Non-ACTIVA login branch (BCE/V4 conflict)","Inicio integration"],
                  "RF02":[],"RF01_regression":[]}
        for group,classes in [("RF02",RF02),("RF01_regression",RF01)]:
            for name in classes:
                suite=ET.parse(TARGET/"surefire-reports"/("TEST-"+name+".xml")).getroot()
                item={k:suite.get(k) for k in ["name","tests","failures","errors","skipped","time"]}
                if any(int(item[k]) for k in ["failures","errors","skipped"]):raise RuntimeError("Suite must pass without omissions: "+name)
                item["cases"]=[case.get("name") for case in suite.findall("testcase")]
                evidence[group].append(item)
        evidence["totals"]={group:sum(int(s["tests"]) for s in evidence[group]) for group in ["RF02","RF01_regression"]}
        assert before=={str(p.relative_to(ROOT)):digest(p) for p in protected},"RF01/V1-V4 changed during tests"
        evidence["protected_files_sha256"]=before
        evidence_path.write_text(json.dumps(evidence,ensure_ascii=False,indent=2),encoding="utf-8")
        print(json.dumps({"status":evidence["status"],"totals":evidence["totals"],"pending":evidence["pending"]},ensure_ascii=False,indent=2))
    finally:
        if started:
            label=subprocess.run(["docker","inspect","--format",'{{index .Config.Labels "mycar.purpose"}}',container],capture_output=True,text=True,check=True).stdout.strip()
            if re.fullmatch(r"mycar-rf02-test-[a-f0-9]{12}",container) and label=="rf02-disposable-test":
                subprocess.run(["docker","rm","--force","--volumes",container],check=True,stdout=subprocess.DEVNULL)
            else:raise RuntimeError("Container identity mismatch; cleanup refused")

if __name__=="__main__":main()
