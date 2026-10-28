// Utilidades compartidas por los scripts de SIT web: sesiones de Playwright contra
// el frontend publicado, capturas con banda de URL y hora, y respuestas HTTP
// literales tomadas del navegador. Cada script de docs/pruebas/sit/*/playwright/
// las importa con evidencia({ issue, outDir }).
//
// Variables de entorno:
//   C9_TEST_PASSWORD  contraseña de las cuentas de prueba (obligatoria; nunca se escribe en disco)
//   BASE_URL          frontend; por defecto https://restohub.azurewebsites.net
//   CHROME_PATH       ejecutable de un navegador Chromium instalado; sin él, el Chromium de Playwright
//   OUT_DIR           carpeta de salida; por defecto, la de la evidencia

import { chromium } from "playwright";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { join } from "node:path";

export const BASE_URL = process.env.BASE_URL ?? "https://restohub.azurewebsites.net";
export const API_URL = "https://app-callejon9-api-prod-3878c6.azurewebsites.net";
export const SLUG = "pruebas";

export const localTime = (date = new Date()) =>
  new Intl.DateTimeFormat("sv-SE", { timeZone: "America/Mexico_City", dateStyle: "short", timeStyle: "medium" }).format(date);
export const log = (line) => console.log(`${localTime()}  ${line}`);

export function httpLiteral(title, method, path, requestBody, r, via = `por el proxy ${BASE_URL}`) {
  let pretty = r.text ?? r.body ?? "";
  try { pretty = JSON.stringify(JSON.parse(pretty), null, 2); } catch { /* deja el texto tal cual */ }
  return [
    `# ${title}`,
    `# ${r.at ?? localTime()} America/Mexico_City · ${via}`,
    `${method} ${path}`,
    requestBody ? `Content-Type: application/json\n\n${requestBody}` : "",
    "",
    `HTTP ${r.status} ${r.statusText ?? ""}`.trim(),
    `Content-Type: ${r.contentType}`,
    "",
    pretty,
    "",
  ].join("\n");
}

export function evidencia({ issue, outDir }) {
  const password = process.env.C9_TEST_PASSWORD;
  if (!password) throw new Error("Falta la variable de entorno C9_TEST_PASSWORD.");
  const out = process.env.OUT_DIR ?? outDir;
  const capturas = join(out, "capturas");
  const respuestas = join(out, "respuestas");
  const statePath = join(respuestas, "estado-ejecucion.json");

  async function stamp(page) {
    const text = `Captura automatizada · Playwright · ${issue} · ${page.url()} · ${localTime()} America/Mexico_City`;
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
    await page.waitForTimeout(500);
    await stamp(page);
    await page.screenshot({ path: join(capturas, `${name}.png`), fullPage: true });
    log(`captura  ${name}.png  (${page.url()})`);
    await page.evaluate(() => { document.getElementById("__evidence_stamp")?.remove(); document.body.style.paddingTop = ""; });
  }

  /** Registra cada respuesta de la API (salvo GET y auth) que pasa por el proxy del frontend. */
  function recordApi(page, actor, sink) {
    page.on("response", async (res) => {
      const url = new URL(res.url());
      if (!url.pathname.startsWith("/api/v1/") || url.pathname.startsWith("/api/v1/auth/")) return;
      const method = res.request().method();
      if (method === "GET") return;
      let body = "";
      try { body = await res.text(); } catch { body = "<sin cuerpo>"; }
      sink.push({ at: localTime(), actor, method, path: url.pathname, status: res.status(), statusText: res.statusText(),
        contentType: res.headers()["content-type"] ?? "", request: res.request().postData() ?? "", body });
    });
  }

  async function login(browser, email, sink) {
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, locale: "es-MX", acceptDownloads: true });
    const page = await context.newPage();
    if (sink) recordApi(page, email, sink);
    await page.goto(`${BASE_URL}/login?slug=${SLUG}`, { waitUntil: "networkidle" });
    await page.locator("#email").fill(email);
    await page.locator("#password").fill(password);
    await page.locator('button[type="submit"]').click();
    await page.waitForURL((url) => !url.pathname.startsWith("/login"), { timeout: 60_000 });
    await page.waitForLoadState("networkidle");
    return { context, page, email };
  }

  /** Petición a la API por el proxy same-origin, con la cookie de la sesión del navegador. */
  async function api(page, method, path, body) {
    const r = await page.evaluate(async ({ method, path, body }) => {
      const res = await fetch(path, { method, credentials: "same-origin",
        headers: body ? { "Content-Type": "application/json" } : {}, body: body ? JSON.stringify(body) : undefined });
      const text = await res.text();
      return { status: res.status, statusText: res.statusText, contentType: res.headers.get("content-type") ?? "", text };
    }, { method, path, body });
    return { ...r, at: localTime() };
  }

  /** api() que además guarda la respuesta literal en respuestas/<file>. */
  async function apiSave(page, file, title, method, path, body) {
    const r = await api(page, method, path, body);
    await writeFile(join(respuestas, file), httpLiteral(title, method, path, body ? JSON.stringify(body, null, 2) : "", r));
    log(`${method} ${path} -> HTTP ${r.status}  (${file})`);
    return r;
  }

  /** Guarda las peticiones registradas desde la interfaz que cumplen pred. */
  async function writeCalls(file, title, calls, pred) {
    const list = calls.filter(pred);
    await writeFile(join(respuestas, file),
      list.map((c) => httpLiteral(`${title} · ${c.actor}`, c.method, c.path, c.request, { ...c, text: c.body })).join("\n"));
    return list;
  }

  async function health() {
    const res = await fetch(`${API_URL}/actuator/health`);
    const r = { status: res.status, statusText: res.statusText, contentType: res.headers.get("content-type") ?? "", text: await res.text() };
    await writeFile(join(respuestas, "health.txt"),
      httpLiteral("Salud de la API antes de ejecutar", "GET", `${API_URL}/actuator/health`, "", r, "directo a la API"));
    log(`health -> HTTP ${r.status} ${r.text}`);
    if (r.status !== 200) throw new Error("La API no está en UP.");
  }

  const saveState = (state) => writeFile(statePath, JSON.stringify(state, null, 2) + "\n");
  const loadState = async () => JSON.parse(await readFile(statePath, "utf8"));

  /** Corre la etapa pedida en argv[2] con un navegador nuevo. */
  async function run(stages) {
    const stage = process.argv[2];
    if (!stages[stage]) throw new Error(`Uso: node <script> ${Object.keys(stages).join("|")}`);
    await mkdir(capturas, { recursive: true });
    await mkdir(respuestas, { recursive: true });
    const browser = await chromium.launch(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {});
    try {
      await stages[stage](browser);
    } finally {
      await browser.close();
    }
  }

  return { capturas, respuestas, capture, recordApi, login, api, apiSave, writeCalls, health, saveState, loadState, run };
}

// ---------------------------------------------------------------- pantallas comunes
/** Tarjeta de la mesa `number` en /waiter. */
export const tableCard = (page, number) => page.locator('[role="button"]').filter({
  has: page.locator("p.font-display", { hasText: new RegExp(`^${number}$`) }) }).first();

/** Abre la mesa libre `number` desde /waiter y devuelve el id de la orden creada. */
export async function openTable(page, number, guests = 2) {
  await page.goto(`${BASE_URL}/waiter`, { waitUntil: "networkidle" });
  await tableCard(page, number).click();
  await page.locator("#guestCount").fill(String(guests));
  await Promise.all([
    page.waitForURL(/\/waiter\/order\//, { timeout: 30_000 }),
    page.getByRole("dialog").locator('button[type="submit"]').click(),
  ]);
  await page.waitForLoadState("networkidle");
  return page.url().split("/").pop();
}

/** items: [categoría, producto, cantidad]. Agrega al carrito y confirma con «Agregar a la orden». */
export async function addProducts(page, orderId, items) {
  for (const [category, name, qty] of items) {
    await page.locator("button[aria-pressed]", { hasText: category }).first().click();
    await page.getByRole("button", { name: `Agregar ${name}` }).click();
    for (let i = 1; i < qty; i += 1) await page.getByRole("button", { name: "Agregar una unidad" }).last().click();
  }
  const [res] = await Promise.all([
    page.waitForResponse((r) => r.url().includes(`/orders/${orderId}/items`) && r.request().method() === "POST"),
    page.getByRole("button", { name: "Agregar a la orden" }).click(),
  ]);
  await page.waitForLoadState("networkidle");
  return res.status();
}

export async function freeTables(ev, page) {
  const tables = JSON.parse((await ev.api(page, "GET", "/api/v1/tables")).text);
  return tables.filter((t) => t.active && t.status === "FREE").sort((a, b) => a.number - b.number);
}
