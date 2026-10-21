// SIT de CAL-133 (Autenticación y sesiones), ejecutada con Playwright contra el
// frontend publicado y la API de Azure.
//
//   basicos       C01 login por el frontend; C02 cuenta inexistente; C04 logout.
//   sesion-larga  C03: la cocina web queda abierta más de 15 minutos y se registra
//                 cómo renueva el token sin pedir login (WAIT_MINUTES, 17 por defecto).
//   bloqueo       C05: seis contraseñas erróneas de mesero2@pruebas.com → 429.
//
// Uso, desde esta carpeta con Playwright y Google Chrome instalados:
//   $env:C9_TEST_PASSWORD = "<contraseña de las cuentas de prueba>"
//   node sit-autenticacion.mjs basicos|sesion-larga|bloqueo
//   $env:OUT_DIR = "<carpeta>"   # opcional: ensayo fuera de la evidencia
//
// La contraseña y los valores de las cookies nunca se escriben en disco.

import { chromium } from "playwright";
import { mkdir, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const BASE_URL = process.env.BASE_URL ?? "https://restohub.azurewebsites.net";
const HOST = new URL(BASE_URL).host;
const SLUG = "pruebas";
const PASSWORD = process.env.C9_TEST_PASSWORD;
const WAIT_MINUTES = Number(process.env.WAIT_MINUTES ?? 17);
const ISSUE = "CAL-133";

const outDir = process.env.OUT_DIR ?? join(dirname(fileURLToPath(import.meta.url)), "..");
const capturas = join(outDir, "capturas");
const respuestas = join(outDir, "respuestas");

const localTime = (date = new Date()) =>
  new Intl.DateTimeFormat("sv-SE", { timeZone: "America/Mexico_City", dateStyle: "short", timeStyle: "medium" }).format(date);
const log = (line) => console.log(`${localTime()}  ${line}`);
const REDACTED = "<redactado>";

async function stamp(page) {
  const text = `Captura automatizada · Playwright · ${ISSUE} · ${page.url()} · ${localTime()} America/Mexico_City`;
  await page.evaluate((t) => {
    document.getElementById("__evidence_stamp")?.remove();
    const bar = document.createElement("div");
    bar.id = "__evidence_stamp";
    bar.textContent = t;
    Object.assign(bar.style, { position: "fixed", top: "0", left: "0", right: "0", zIndex: "2147483647",
      padding: "6px 12px", font: "13px/1.4 monospace", color: "#fff", background: "#b91c1c", pointerEvents: "none" });
    document.body.appendChild(bar);
    document.body.style.paddingTop = "30px";
  }, text);
}

async function capture(page, name) {
  await page.waitForTimeout(400);
  await stamp(page);
  await page.screenshot({ path: join(capturas, `${name}.png`), fullPage: true });
  log(`captura  ${name}.png  (${page.url()})`);
  await page.evaluate(() => { document.getElementById("__evidence_stamp")?.remove(); document.body.style.paddingTop = ""; });
}

/** Cabeceras y cuerpo de una respuesta, con los valores de las cookies ocultos. */
async function literal(title, res, requestBody) {
  const req = res.request();
  const headers = (await res.headersArray())
    .filter((h) => !/^(date|server|x-powered-by|transfer-encoding|connection|vary|cache-control|expires|pragma)$/i.test(h.name))
    .map((h) => `${h.name}: ${/^set-cookie$/i.test(h.name) ? h.value.replace(/^([^=]+)=[^;]*/, `$1=${REDACTED}`) : h.value}`);
  let body = "";
  try { body = await res.text(); } catch { body = ""; }
  try { body = JSON.stringify(JSON.parse(body), null, 2); } catch { /* texto tal cual */ }
  return [
    `# ${title}`,
    `# ${localTime()} America/Mexico_City · por el proxy ${BASE_URL}`,
    `${req.method()} ${new URL(res.url()).pathname}`,
    requestBody ? `Content-Type: application/json\n\n${requestBody}` : "",
    "",
    `HTTP ${res.status()} ${res.statusText()}`.trim(),
    ...headers,
    "",
    body,
    "",
  ].join("\n");
}

async function newPage(browser) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, locale: "es-MX" });
  return { context, page: await context.newPage() };
}

async function fillLogin(page, email, password) {
  await page.goto(`${BASE_URL}/login?slug=${SLUG}`, { waitUntil: "networkidle" });
  await page.locator("#email").fill(email);
  await page.locator("#password").fill(password);
}

async function submitLogin(page) {
  const [res] = await Promise.all([
    page.waitForResponse((r) => r.url().endsWith("/api/v1/auth/login") && r.request().method() === "POST"),
    page.locator('button[type="submit"]').click(),
  ]);
  return res;
}

const loginBody = (email) => JSON.stringify({ slug: SLUG, email, password: REDACTED }, null, 2);

// ---------------------------------------------------------------- C01, C02, C04
async function basicos(browser) {
  // C01
  log("C01 · login por el frontend publicado");
  const a = await newPage(browser);
  await fillLogin(a.page, "mesero@pruebas.com", PASSWORD);
  await capture(a.page, "C01-P01-formulario");
  const res = await submitLogin(a.page);
  await writeFile(join(respuestas, "C01-login.txt"), await literal("C01 · login de mesero@pruebas.com desde la interfaz", res, loginBody("mesero@pruebas.com")));
  log(`login -> HTTP ${res.status()}`);
  await a.page.waitForURL((u) => !u.pathname.startsWith("/login"), { timeout: 60_000 });
  await a.page.waitForLoadState("networkidle");
  await capture(a.page, "C01-P02-sesion-iniciada");
  const cookies = await a.context.cookies(BASE_URL);
  const rows = cookies.filter((c) => /token/.test(c.name)).map((c) =>
    `| ${c.name} | ${c.domain} | ${c.path} | ${c.httpOnly} | ${c.secure} | ${c.sameSite} | ${new Date(c.expires * 1000).toISOString()} | ${REDACTED} |`);
  await writeFile(join(respuestas, "C01-cookies-del-navegador.md"), [
    `# C01 · cookies de sesión guardadas por el navegador (${localTime()} America/Mexico_City)`,
    "",
    `Leídas con Playwright (context.cookies) después del login en ${BASE_URL}. Los valores se ocultan.`,
    "",
    "| Cookie | Dominio | Ruta | HttpOnly | Secure | SameSite | Expira (UTC) | Valor |",
    "|---|---|---|---|---|---|---|---|",
    ...rows,
    "",
    `Cookies con «token» en el nombre visibles para JavaScript (document.cookie): ${(await a.page.evaluate(() => document.cookie)).split(";").filter((x) => /token/.test(x)).length}`,
    "",
  ].join("\n"));
  log(`cookies: ${cookies.filter((c) => /token/.test(c.name)).map((c) => `${c.name}@${c.domain} httpOnly=${c.httpOnly} secure=${c.secure}`).join(", ")}`);

  // C04: se reutiliza la sesión de C01
  log("C04 · logout invalida el token anterior");
  const oldToken = cookies.find((c) => c.name === "access_token");
  const meBefore = await a.page.evaluate(async () => { const r = await fetch("/api/v1/auth/me"); return { status: r.status, body: await r.text() }; });
  log(`/auth/me antes del logout -> HTTP ${meBefore.status}`);
  await capture(a.page, "C04-P01-antes-del-logout");
  const [logoutRes] = await Promise.all([
    a.page.waitForResponse((r) => r.url().endsWith("/api/v1/auth/logout")),
    a.page.getByRole("button", { name: "Cerrar sesión" }).first().click(),
  ]);
  await writeFile(join(respuestas, "C04-logout.txt"), await literal("C04 · logout desde la interfaz", logoutRes));
  log(`logout -> HTTP ${logoutRes.status()}`);
  await a.page.waitForURL((u) => u.pathname.startsWith("/login"), { timeout: 30_000 });
  await capture(a.page, "C04-P02-despues-del-logout");
  await a.context.close();

  // un navegador nuevo presenta el token viejo, que solo vive en memoria de este proceso
  const b = await newPage(browser);
  await b.context.addCookies([{ name: "access_token", value: oldToken.value, domain: oldToken.domain, path: oldToken.path,
    httpOnly: true, secure: true, sameSite: oldToken.sameSite }]);
  await b.page.goto(`${BASE_URL}/login?slug=${SLUG}`, { waitUntil: "networkidle" });
  const [meRes] = await Promise.all([
    b.page.waitForResponse((r) => r.url().endsWith("/api/v1/auth/me")),
    b.page.evaluate(() => fetch("/api/v1/auth/me")),
  ]);
  await writeFile(join(respuestas, "C04-token-anterior-tras-logout.txt"),
    `# /auth/me antes del logout, en la sesión original: HTTP ${meBefore.status}\n\n` +
    await literal("C04 · otro navegador presenta el access_token anterior al logout (valor oculto)", meRes));
  log(`/auth/me con el token anterior -> HTTP ${meRes.status()}`);
  await b.context.close();

  // C02
  log("C02 · cuenta inexistente");
  const c = await newPage(browser);
  await fillLogin(c.page, "noexiste@pruebas.com", "Cualquier-Clave-2026");
  const bad = await submitLogin(c.page);
  await writeFile(join(respuestas, "C02-cuenta-inexistente.txt"), await literal("C02 · login de una cuenta inexistente desde la interfaz", bad, loginBody("noexiste@pruebas.com")));
  log(`cuenta inexistente -> HTTP ${bad.status()}`);
  await c.page.waitForTimeout(600);
  await capture(c.page, "C02-P01-mensaje-al-usuario");
  await c.context.close();
}

// ---------------------------------------------------------------- C03
async function sesionLarga(browser) {
  log(`C03 · sesión de cocina abierta ${WAIT_MINUTES} min`);
  const { context, page } = await newPage(browser);
  const events = [];
  const stats = { ok: 0, unauthorized: 0, refresh: [] };
  page.on("response", (r) => {
    const path = new URL(r.url()).pathname;
    if (!path.startsWith("/api/v1/")) return;
    if (path === "/api/v1/auth/refresh") {
      stats.refresh.push({ at: localTime(), status: r.status() });
      events.push(`${localTime()}  POST ${path} -> HTTP ${r.status()}`);
    } else if (r.status() === 401) {
      stats.unauthorized += 1;
      events.push(`${localTime()}  ${r.request().method()} ${path} -> HTTP 401`);
    } else if (r.ok()) stats.ok += 1;
  });
  page.on("framenavigated", (f) => { if (f === page.mainFrame()) events.push(`${localTime()}  navegación -> ${f.url()}`); });
  await fillLogin(page, "cocina@pruebas.com", PASSWORD);
  const res = await submitLogin(page);
  events.push(`${localTime()}  POST /api/v1/auth/login -> HTTP ${res.status()}`);
  await page.waitForURL((u) => !u.pathname.startsWith("/login"), { timeout: 60_000 });
  await page.goto(`${BASE_URL}/kitchen`, { waitUntil: "networkidle" });
  const start = Date.now();
  await capture(page, "C03-P01-cocina-al-iniciar");
  let capturedAfterRefresh = false;
  while (Date.now() - start < WAIT_MINUTES * 60_000) {
    await page.waitForTimeout(15_000);
    const min = ((Date.now() - start) / 60_000).toFixed(1);
    if (!capturedAfterRefresh && stats.refresh.length) {
      await page.waitForTimeout(6_000);
      await capture(page, "C03-P02-cocina-tras-renovar");
      capturedAfterRefresh = true;
    }
    if (Math.round(Date.now() - start) % 60_000 < 15_000) log(`C03 · ${min} min · ok=${stats.ok} 401=${stats.unauthorized} refresh=${stats.refresh.length} url=${new URL(page.url()).pathname}`);
  }
  await capture(page, "C03-P03-cocina-al-final");
  const summary = [
    `# C03 · sesión de la cocina web abierta ${WAIT_MINUTES} minutos (${BASE_URL}/kitchen)`,
    `# Inicio ${localTime(new Date(start))} · fin ${localTime()} America/Mexico_City · token de acceso de 15 minutos`,
    "",
    `Peticiones correctas a la API: ${stats.ok}`,
    `Respuestas 401: ${stats.unauthorized}`,
    `Renovaciones (POST /api/v1/auth/refresh): ${stats.refresh.map((r) => `${r.at} -> HTTP ${r.status}`).join("; ") || "ninguna"}`,
    `Página al final: ${new URL(page.url()).pathname}`,
    "",
    "## Eventos",
    ...events,
    "",
  ].join("\n");
  await writeFile(join(respuestas, "C03-registro-sesion.txt"), summary);
  log(`C03 terminado: ${stats.refresh.length} renovaciones, ${stats.unauthorized} respuestas 401, página final ${new URL(page.url()).pathname}`);
  await context.close();
}

// ---------------------------------------------------------------- C05
async function bloqueo(browser) {
  log("C05 · seis contraseñas erróneas de mesero2@pruebas.com");
  const { context, page } = await newPage(browser);
  const parts = [];
  for (let i = 1; i <= 6; i += 1) {
    await fillLogin(page, "mesero2@pruebas.com", `Clave-Erronea-${i}-2026`);
    const res = await submitLogin(page);
    parts.push(await literal(`C05 · intento ${i} con contraseña errónea`, res, loginBody("mesero2@pruebas.com")));
    log(`intento ${i} -> HTTP ${res.status()}${res.headers()["retry-after"] ? ` (Retry-After ${res.headers()["retry-after"]})` : ""}`);
    await page.waitForTimeout(500);
    if (i === 5) await capture(page, "C05-P01-intento-5");
    if (i === 6) await capture(page, "C05-P02-intento-6-bloqueado");
  }
  await writeFile(join(respuestas, "C05-seis-intentos.txt"), parts.join("\n"));
  await context.close();
}

const stage = process.argv[2];
if (!PASSWORD) throw new Error("Falta la variable de entorno C9_TEST_PASSWORD.");
const stages = { basicos, "sesion-larga": sesionLarga, bloqueo };
if (!stages[stage]) throw new Error("Uso: node sit-autenticacion.mjs basicos|sesion-larga|bloqueo");
await mkdir(capturas, { recursive: true });
await mkdir(respuestas, { recursive: true });
const browser = await chromium.launch({ channel: "chrome" });
try {
  await stages[stage](browser);
} finally {
  await browser.close();
}
