"""RF06 plus RF01/RF02 regression, using three new databases in an owned PostgreSQL 17 container."""
import base64
import json
import os
import re
import secrets
import subprocess
import sys
import time
import uuid
import xml.etree.ElementTree as ET

sys.dont_write_bytecode=True
from test_rf02 import RF01, RF02, ROOT, TARGET, digest

RF06=["backend.cierresesion.CloseSessionTest","backend.cierresesion.LogoutWebTest",
      "backend.cierresesion.LogoutIntegrationTest"]


def main():
    TARGET.mkdir(exist_ok=True)
    report=TARGET/"rf06-evidencia.json"
    if report.exists():report.unlink()
    protected=[*sorted((ROOT/"backend/src/main/java/backend/registro").rglob("*.java")),
               *sorted((ROOT/"backend/src/main/java/backend/autenticacion").rglob("*.java")),
               *sorted((ROOT/"backend/src/test/java/backend/registro").rglob("*.java")),
               *sorted((ROOT/"backend/src/test/java/backend/autenticacion").rglob("*.java")),
               *sorted((ROOT/"backend/src/main/resources/db/mvp").glob("*.sql"))]
    before={str(p.relative_to(ROOT)):digest(p) for p in protected}
    suffix=uuid.uuid4().hex[:12]
    container="mycar-rf06-test-"+suffix
    databases={scope:"mycar_"+scope.lower()+"_test_"+suffix for scope in ("RF06","RF01","RF02")}
    env=os.environ.copy()
    env.update(POSTGRES_USER="mycar",POSTGRES_PASSWORD=secrets.token_urlsafe(32),POSTGRES_DB=databases["RF06"])
    started=False
    try:
        subprocess.run(["docker","run","--detach","--name",container,"--label","mycar.purpose=rf06-disposable-test",
                        "--publish","127.0.0.1::5432","--env","POSTGRES_USER","--env","POSTGRES_PASSWORD",
                        "--env","POSTGRES_DB","postgres:17"],env=env,check=True,stdout=subprocess.DEVNULL)
        started=True
        deadline=time.monotonic()+60
        while time.monotonic()<deadline:
            if subprocess.run(["docker","exec",container,"pg_isready","-h","127.0.0.1","-U","mycar","-d",databases["RF06"]],capture_output=True).returncode==0:break
            time.sleep(.5)
        else:raise RuntimeError("Disposable PostgreSQL did not become ready")
        for scope in ("RF01","RF02"):
            subprocess.run(["docker","exec",container,"createdb","-U","mycar",databases[scope]],check=True)
        binding=subprocess.run(["docker","port",container,"5432/tcp"],capture_output=True,text=True,check=True).stdout.strip()
        port=int(binding.rsplit(":",1)[1])
        for scope,database in databases.items():
            env["MYCAR_"+scope+"_TEST_URL"]=f"jdbc:postgresql://127.0.0.1:{port}/{database}"
            env["MYCAR_"+scope+"_TEST_USER"]="mycar"
            env["MYCAR_"+scope+"_TEST_PASSWORD"]=env["POSTGRES_PASSWORD"]
        for scope in ("RF02","RF06"):
            env["MYCAR_"+scope+"_TEST_SIGNING_KEY"]=base64.b64encode(secrets.token_bytes(32)).decode("ascii")
        wrapper=ROOT/"backend"/("mvnw.cmd" if os.name=="nt" else "mvnw")
        command=["cmd","/c",str(wrapper)] if os.name=="nt" else [str(wrapper)]
        tests=",".join(name.rsplit(".",1)[1] for name in RF06+RF01+RF02)
        with (TARGET/"rf06-test-run.log").open("w",encoding="utf-8") as log:
            result=subprocess.run(command+["-B","-Dtest="+tests,"test"],cwd=ROOT/"backend",env=env,stdout=log,stderr=subprocess.STDOUT)
        if result.returncode:raise RuntimeError("RF06/regression failed: inspect backend/target/rf06-test-run.log")
        evidence={"status":"PASÓ","database":"Three disposable PostgreSQL 17 databases; only V1-V4; no .env or shared volume",
                  "RF06":[],"RF01_regression":[],"RF02_regression":[],
                  "contract":"RF06 approved for this implementation in user confirmation",
                  "not_implemented":["Android confirmation/cleanup/navigation and offline notices","RF02 pending non-ACTIVA login and Inicio"],
                  "integration_adjustment":"Only RF02 logout-without-proof assertion changes from 403 (route absent) to 401 (proof required)"}
        for group,classes in [("RF06",RF06),("RF01_regression",RF01),("RF02_regression",RF02)]:
            for name in classes:
                suite=ET.parse(TARGET/"surefire-reports"/("TEST-"+name+".xml")).getroot()
                item={k:suite.get(k) for k in ("name","tests","failures","errors","skipped","time")}
                if any(int(item[k]) for k in ("failures","errors","skipped")):raise RuntimeError("Suite must pass without omissions: "+name)
                item["cases"]=[case.get("name") for case in suite.findall("testcase")]
                evidence[group].append(item)
        evidence["totals"]={group:sum(int(s["tests"]) for s in evidence[group]) for group in ("RF06","RF01_regression","RF02_regression")}
        assert before=={str(p.relative_to(ROOT)):digest(p) for p in protected},"RF01/RF02/V1-V4 changed during tests"
        evidence["protected_files_sha256"]=before
        report.write_text(json.dumps(evidence,ensure_ascii=False,indent=2),encoding="utf-8")
        print(json.dumps({"status":evidence["status"],"totals":evidence["totals"],"not_implemented":evidence["not_implemented"]},ensure_ascii=False,indent=2))
    finally:
        if started:
            label=subprocess.run(["docker","inspect","--format",'{{index .Config.Labels "mycar.purpose"}}',container],capture_output=True,text=True,check=True).stdout.strip()
            if re.fullmatch(r"mycar-rf06-test-[a-f0-9]{12}",container) and label=="rf06-disposable-test":
                subprocess.run(["docker","rm","--force","--volumes",container],check=True,stdout=subprocess.DEVNULL)
            else:raise RuntimeError("Container identity mismatch; cleanup refused")


if __name__=="__main__":main()
