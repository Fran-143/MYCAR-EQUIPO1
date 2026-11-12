/**
 * mycar-alertas-cron — dispara RF23 en Spring Boot.
 * Reutiliza el Worker ya creado; no crea otro.
 *
 * Flujo:
 * 1) Despierta Render Free (GET /actuator/health) con reintentos.
 * 2) POST /api/v1/interno/alertas/evaluar-programadas con secreto.
 * 3) Si 5xx o red: reintenta con backoff (sin duplicar lógica de negocio;
 *    el backend es idempotente y omite concurrencia).
 *
 * Futuras reglas SOAT/licencia viven en el backend; este Worker no cambia.
 */

const ENDPOINT = "/api/v1/interno/alertas/evaluar-programadas";

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

async function evaluar(base, env, timeoutMs) {
  const url = `${base}${ENDPOINT}`;
  const modo = String(env.EVAL_MODO || "COMPLETO").toUpperCase();
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

async function runCron(env) {
  if (!env.CRON_SECRET) {
    console.log("cron abort: CRON_SECRET ausente");
    return { ok: false, reason: "NO_SECRET" };
  }
  const base = String(env.BACKEND_URL || "").replace(/\/+$/, "");
  if (!base) {
    console.log("cron abort: BACKEND_URL ausente");
    return { ok: false, reason: "NO_BACKEND" };
  }

  const attempts = num(env, "MAX_ATTEMPTS", 4);
  const baseDelay = num(env, "RETRY_BASE_MS", 8000);
  const timeoutMs = num(env, "FETCH_TIMEOUT_MS", 25000);

  const wake = await wakeBackend(base, timeoutMs, attempts, baseDelay);
  if (!wake.ok) {
    console.log(`cron wake FAIL: ${wake.error}`);
    return { ok: false, reason: "WAKE_FAIL", detail: wake.error };
  }
  console.log(`cron wake OK attempt=${wake.attempt}`);

  let lastStatus = 0;
  let lastBody = "";
  for (let i = 0; i < attempts; i++) {
    try {
      const res = await evaluar(base, env, timeoutMs);
      lastStatus = res.status;
      lastBody = await res.text();
      if (res.status === 401 || res.status === 403) {
        console.log(`cron auth FAIL HTTP ${res.status}`);
        return { ok: false, reason: "AUTH", status: res.status };
      }
      if (res.ok) {
        console.log(`cron OK HTTP ${res.status} body=${lastBody.slice(0, 300)}`);
        return { ok: true, status: res.status, body: lastBody };
      }
      // 409/429/5xx → reintento controlado
      console.log(`cron retryable HTTP ${res.status} attempt=${i + 1}`);
    } catch (e) {
      lastStatus = 0;
      lastBody = String(e);
      console.log(`cron fetch error attempt=${i + 1}: ${e}`);
    }
    if (i + 1 < attempts) {
      await sleep(baseDelay * (i + 1));
    }
  }
  console.log(`cron FAIL status=${lastStatus}`);
  return { ok: false, reason: "EVAL_FAIL", status: lastStatus, body: lastBody };
}

export default {
  async scheduled(event, env, ctx) {
    ctx.waitUntil(runCron(env));
  },

  // GET / → estado (sin secretos). POST /run → disparo manual de prueba (requiere secreto en env).
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/") {
      return Response.json({
        service: "mycar-alertas-cron",
        backend: env.BACKEND_URL || null,
        cronConfigured: false,
        hint: "Cron Trigger desactivado hasta aprobación. Use POST /run tras configurar CRON_SECRET.",
      });
    }
    if (request.method === "POST" && url.pathname === "/run") {
      // Protege el disparo manual con el mismo secreto (header), sin exponerlo en logs.
      const provided = request.headers.get("X-MyCar-Cron-Secret") || "";
      if (!env.CRON_SECRET || provided !== env.CRON_SECRET) {
        return Response.json({ ok: false, codigo: "CRON_NO_AUTORIZADO" }, { status: 401 });
      }
      const result = await runCron(env);
      return Response.json(result, { status: result.ok ? 200 : 502 });
    }
    return new Response("Not Found", { status: 404 });
  },
};
