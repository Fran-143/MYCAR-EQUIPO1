/**
 * mycar-alertas-cron — Eval 00:10 Lima (RF23 + RF43) + despacho push 05:00 Lima.
 * Worker existente (mycargrupo1); no crear otro.
 *
 * Seguridad vigente (NO desplegar cambios sin autorización):
 *   EVAL_MODO=SIMULAR
 *   crons=[]
 *
 * Activación futura (solo con aprobación):
 *   EVAL_MODO = "COMPLETO"
 *   crons = ["10 5 * * *", "0 10 * * *"]  # 00:10 y 05:00 America/Lima
 *   npx wrangler deploy
 *
 * RF23 y RF43 se invocan por separado: un fallo aislado no cancela el otro.
 */

const EVAL_ALERTAS_PATH = "/api/v1/interno/alertas/evaluar-programadas";
const EVAL_RF43_PATH = "/api/v1/interno/recordatorios-km/evaluar";
const PUSH_PATH = "/api/v1/interno/push/despachar-recordatorios";
const WORKER_NAME = "mycar-alertas-cron";
const CRON_EVAL = "10 5 * * *";
const CRON_PUSH = "0 10 * * *";

function num(env, key, fallback) {
  const n = Number.parseInt(String(env[key] ?? ""), 10);
  return Number.isFinite(n) && n > 0 ? n : fallback;
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

async function fetchWithTimeout(url, init, timeoutMs) {
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort("timeout"), timeoutMs);
  try {
    return await fetch(url, { ...init, signal: ctrl.signal });
  } finally {
    clearTimeout(t);
  }
}

async function wakeBackend(base, timeoutMs, attempts, baseDelay) {
  const healthUrl = `${base}/actuator/health`;
  let lastErr = null;
  for (let i = 0; i < attempts; i++) {
    try {
      const res = await fetchWithTimeout(healthUrl, { method: "GET" }, timeoutMs);
      if (res.ok) {
        return { ok: true, status: res.status, attempt: i + 1 };
      }
      lastErr = `health HTTP ${res.status}`;
    } catch (e) {
      lastErr = String(e);
    }
    if (i + 1 < attempts) {
      await sleep(baseDelay * (i + 1));
    }
  }
  return { ok: false, error: lastErr };
}

function authHeaders(env) {
  return {
    "Content-Type": "application/json",
    "X-MyCar-Cron-Secret": env.CRON_SECRET || "",
  };
}

async function postInterno(base, env, path, body, timeoutMs) {
  return fetchWithTimeout(
    `${base}${path}`,
    {
      method: "POST",
      headers: authHeaders(env),
      body: body == null ? undefined : JSON.stringify(body),
    },
    timeoutMs,
  );
}

async function runWithWake(env, { origen, path, body, modoLabel }) {
  if (!env.CRON_SECRET) {
    console.log(`${origen} abort: CRON_SECRET ausente`);
    return { ok: false, reason: "NO_SECRET", fase: modoLabel };
  }
  const base = String(env.BACKEND_URL || "").replace(/\/+$/, "");
  if (!base) {
    console.log(`${origen} abort: BACKEND_URL ausente`);
    return { ok: false, reason: "NO_BACKEND", fase: modoLabel };
  }

  const attempts = num(env, "MAX_ATTEMPTS", 4);
  const baseDelay = num(env, "RETRY_BASE_MS", 8000);
  const timeoutMs = num(env, "FETCH_TIMEOUT_MS", 25000);

  const wake = await wakeBackend(base, timeoutMs, attempts, baseDelay);
  if (!wake.ok) {
    console.log(`${origen} wake FAIL: ${wake.error}`);
    return { ok: false, reason: "WAKE_FAIL", detail: wake.error, fase: modoLabel };
  }
  console.log(`${origen} wake OK attempt=${wake.attempt} fase=${modoLabel}`);

  let lastStatus = 0;
  let lastBody = "";
  for (let i = 0; i < attempts; i++) {
    try {
      const res = await postInterno(base, env, path, body, timeoutMs);
      lastStatus = res.status;
      lastBody = await res.text();
      if (res.status === 401 || res.status === 403) {
        console.log(`${origen} auth FAIL HTTP ${res.status}`);
        return { ok: false, reason: "AUTH", status: res.status, fase: modoLabel, body: lastBody };
      }
      if (res.ok) {
        console.log(`${origen} OK HTTP ${res.status} body=${lastBody.slice(0, 400)}`);
        return { ok: true, status: res.status, body: lastBody, fase: modoLabel };
      }
      console.log(`${origen} retryable HTTP ${res.status} attempt=${i + 1}`);
    } catch (e) {
      lastStatus = 0;
      lastBody = String(e);
      console.log(`${origen} fetch error attempt=${i + 1}: ${e}`);
    }
    if (i + 1 < attempts) {
      await sleep(baseDelay * (i + 1));
    }
  }
  console.log(`${origen} FAIL status=${lastStatus}`);
  return { ok: false, reason: "CALL_FAIL", status: lastStatus, body: lastBody, fase: modoLabel };
}

function modoEval(env, modoForzado) {
  const desdeEnv = String(env.EVAL_MODO || "SIMULAR").toUpperCase();
  const raw = modoForzado != null ? String(modoForzado).toUpperCase() : desdeEnv;
  return raw === "COMPLETO" ? "COMPLETO" : "SIMULAR";
}

/** Eval 00:10: RF23 y RF43 independientes; resultados separados. */
async function runEval(env, { modoForzado, origen }) {
  const modo = modoEval(env, modoForzado);
  const rf23 = await runWithWake(env, {
    origen: `${origen}:rf23`,
    path: EVAL_ALERTAS_PATH,
    body: { modo },
    modoLabel: `EVAL_RF23_${modo}`,
  });
  const rf43 = await runWithWake(env, {
    origen: `${origen}:rf43`,
    path: EVAL_RF43_PATH,
    body: { modo },
    modoLabel: `EVAL_RF43_${modo}`,
  });
  return {
    ok: rf23.ok || rf43.ok,
    modo,
    rf23,
    rf43,
    nota: "SIMULAR no escribe estados ni RF43; COMPLETO requiere autorización.",
  };
}

async function runPush(env, origen) {
  return runWithWake(env, {
    origen,
    path: PUSH_PATH,
    body: null,
    modoLabel: "PUSH_RECORDATORIOS",
  });
}

export default {
  async scheduled(event, env, ctx) {
    const cron = String(event.cron || "");
    if (cron === CRON_PUSH) {
      ctx.waitUntil(runPush(env, "scheduled-push"));
      return;
    }
    ctx.waitUntil(runEval(env, { origen: `scheduled-eval:${cron || CRON_EVAL}` }));
  },

  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/") {
      return Response.json({
        service: WORKER_NAME,
        worker: WORKER_NAME,
        accountHint: "mycargrupo1.workers.dev",
        backend: env.BACKEND_URL || null,
        evalModoVars: String(env.EVAL_MODO || "SIMULAR"),
        cronTriggersEsperados: [CRON_EVAL, CRON_PUSH],
        cronEvalUtc: CRON_EVAL,
        cronPushUtc: CRON_PUSH,
        evalPaths: [EVAL_ALERTAS_PATH, EVAL_RF43_PATH],
        pushPath: PUSH_PATH,
        runManualFuerza: "SIMULAR",
        hint: "POST /run → EVAL SIMULAR (RF23+RF43). POST /run-push → despacho. Crons Dashboard: 10 5 / 0 10 UTC.",
      });
    }

    const provided = request.headers.get("X-MyCar-Cron-Secret") || "";
    if (!env.CRON_SECRET || provided !== env.CRON_SECRET) {
      if (request.method === "POST" && (url.pathname === "/run" || url.pathname === "/run-push")) {
        return Response.json({ ok: false, codigo: "CRON_NO_AUTORIZADO" }, { status: 401 });
      }
    }

    if (request.method === "POST" && url.pathname === "/run") {
      const result = await runEval(env, { modoForzado: "SIMULAR", origen: "run" });
      return Response.json(result, { status: result.ok ? 200 : 502 });
    }
    if (request.method === "POST" && url.pathname === "/run-push") {
      const result = await runPush(env, "run-push");
      return Response.json(result, { status: result.ok ? 200 : 502 });
    }
    return new Response("Not Found", { status: 404 });
  },
};
