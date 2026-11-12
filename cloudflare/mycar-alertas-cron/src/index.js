/**
 * mycar-alertas-cron — dispara RF23 en Spring Boot.
 * Worker existente (mycargrupo1); no crear otro.
 *
 * Primera publicación: EVAL_MODO=SIMULAR; POST /run fuerza SIMULAR siempre
 * (ignora body/query) para no activar COMPLETO por accidente.
 *
 * Activación futura (solo con aprobación):
 *  - wrangler.toml: EVAL_MODO = "COMPLETO"
 *  - wrangler.toml: crons = ["0 13 * * *"]  # 08:00 America/Lima
 *  - npx wrangler deploy
 */

const ENDPOINT = "/api/v1/interno/alertas/evaluar-programadas";
const WORKER_NAME = "mycar-alertas-cron";

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

/**
 * @param {string} modoForzado si se pasa, prevalece sobre EVAL_MODO (p. ej. /run → SIMULAR).
 */
async function evaluar(base, env, timeoutMs, modoForzado) {
  const url = `${base}${ENDPOINT}`;
  const desdeEnv = String(env.EVAL_MODO || "SIMULAR").toUpperCase();
  const modo = String(modoForzado || desdeEnv).toUpperCase() === "COMPLETO" ? "COMPLETO" : "SIMULAR";
  return fetchWithTimeout(
    url,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-MyCar-Cron-Secret": env.CRON_SECRET || "",
      },
      body: JSON.stringify({ modo }),
    },
    timeoutMs,
  );
}

async function runEval(env, { modoForzado, origen }) {
  if (!env.CRON_SECRET) {
    console.log(`${origen} abort: CRON_SECRET ausente`);
    return { ok: false, reason: "NO_SECRET" };
  }
  const base = String(env.BACKEND_URL || "").replace(/\/+$/, "");
  if (!base) {
    console.log(`${origen} abort: BACKEND_URL ausente`);
    return { ok: false, reason: "NO_BACKEND" };
  }

  const attempts = num(env, "MAX_ATTEMPTS", 4);
  const baseDelay = num(env, "RETRY_BASE_MS", 8000);
  const timeoutMs = num(env, "FETCH_TIMEOUT_MS", 25000);
  const modoEfectivo =
    modoForzado != null
      ? String(modoForzado).toUpperCase() === "COMPLETO"
        ? "COMPLETO"
        : "SIMULAR"
      : String(env.EVAL_MODO || "SIMULAR").toUpperCase() === "COMPLETO"
        ? "COMPLETO"
        : "SIMULAR";

  const wake = await wakeBackend(base, timeoutMs, attempts, baseDelay);
  if (!wake.ok) {
    console.log(`${origen} wake FAIL: ${wake.error}`);
    return { ok: false, reason: "WAKE_FAIL", detail: wake.error, modo: modoEfectivo };
  }
  console.log(`${origen} wake OK attempt=${wake.attempt} modo=${modoEfectivo}`);

  let lastStatus = 0;
  let lastBody = "";
  for (let i = 0; i < attempts; i++) {
    try {
      const res = await evaluar(base, env, timeoutMs, modoEfectivo);
      lastStatus = res.status;
      lastBody = await res.text();
      if (res.status === 401 || res.status === 403) {
        console.log(`${origen} auth FAIL HTTP ${res.status}`);
        return { ok: false, reason: "AUTH", status: res.status, modo: modoEfectivo };
      }
      if (res.ok) {
        console.log(`${origen} OK HTTP ${res.status} body=${lastBody.slice(0, 300)}`);
        return { ok: true, status: res.status, body: lastBody, modo: modoEfectivo };
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
  return { ok: false, reason: "EVAL_FAIL", status: lastStatus, body: lastBody, modo: modoEfectivo };
}

export default {
  async scheduled(event, env, ctx) {
    // Usa EVAL_MODO del wrangler (hoy SIMULAR). COMPLETO solo tras cambio explícito + deploy.
    ctx.waitUntil(runEval(env, { origen: "scheduled" }));
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
        cronTriggers: [],
        runManualFuerza: "SIMULAR",
        hint: "POST /run siempre usa SIMULAR. Cron vacío. COMPLETO solo tras cambio de vars + crons y aprobación.",
      });
    }
    if (request.method === "POST" && url.pathname === "/run") {
      const provided = request.headers.get("X-MyCar-Cron-Secret") || "";
      if (!env.CRON_SECRET || provided !== env.CRON_SECRET) {
        return Response.json({ ok: false, codigo: "CRON_NO_AUTORIZADO" }, { status: 401 });
      }
      // Seguridad pruebas: /run NUNCA ejecuta COMPLETO (ignora body, query y EVAL_MODO).
      const result = await runEval(env, { modoForzado: "SIMULAR", origen: "run" });
      return Response.json(result, { status: result.ok ? 200 : 502 });
    }
    return new Response("Not Found", { status: 404 });
  },
};
