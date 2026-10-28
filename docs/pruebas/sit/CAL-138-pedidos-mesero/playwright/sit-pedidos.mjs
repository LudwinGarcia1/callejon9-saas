// SIT de CAL-138 (Pedidos del mesero), ejecutada por completo con Playwright
// contra el frontend publicado y la API de Azure.
//
//   preparacion  El administrador da de alta al segundo mesero si no existe.
//   casos        C01 abrir, agregar y enviar a cocina; C02 un segundo mesero
//                intenta operar la orden del primero; C03 cantidad de 100.
//   limpieza     El primer mesero cancela la orden de C02/C03, que sigue en
//                «Nueva», para liberar su mesa.
//
// Uso, desde esta carpeta con Playwright y Google Chrome instalados:
//   $env:C9_TEST_PASSWORD = "<contraseña de las cuentas de prueba>"
//   node sit-pedidos.mjs preparacion|casos|limpieza
//   $env:OUT_DIR = "<carpeta>"   # opcional: ensayo fuera de la evidencia
//
// La contraseña solo se lee de la variable de entorno; nunca se escribe en disco.

import { chromium } from "playwright";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const BASE_URL = process.env.BASE_URL ?? "https://restohub.azurewebsites.net";
const SLUG = "pruebas";
const PASSWORD = process.env.C9_TEST_PASSWORD;
const ISSUE = "CAL-138";
const WAITER_1 = "mesero@pruebas.com";
const WAITER_2 = "mesero2@pruebas.com";
const ADMIN = "admin@pruebas.com";

const outDir = process.env.OUT_DIR ?? join(dirname(fileURLToPath(import.meta.url)), "..");
const capturas = join(outDir, "capturas");
const respuestas = join(outDir, "respuestas");
const statePath = join(outDir, "respuestas", "estado-ejecucion.json");

const localTime = (date = new Date()) =>
  new Intl.DateTimeFormat("sv-SE", { timeZone: "America/Mexico_City", dateStyle: "short", timeStyle: "medium" }).format(date);
const log = (line) => console.log(`${localTime()}  ${line}`);

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

/** Registra cada respuesta de la API que pasa por el proxy del frontend. */
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
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, locale: "es-MX" });
  const page = await context.newPage();
  if (sink) recordApi(page, email, sink);
  await page.goto(`${BASE_URL}/login?slug=${SLUG}`, { waitUntil: "networkidle" });
  await page.locator("#email").fill(email);
  await page.locator("#password").fill(PASSWORD);
  await page.locator('button[type="submit"]').click();
  await page.waitForURL((url) => !url.pathname.startsWith("/login"), { timeout: 60_000 });
  await page.waitForLoadState("networkidle");
  return { context, page };
}

/** Petición a la API por el proxy same-origin, con la cookie de la sesión del navegador. */
async function api(page, method, path, body) {
  return page.evaluate(async ({ method, path, body }) => {
    const res = await fetch(path, { method, credentials: "same-origin",
      headers: body ? { "Content-Type": "application/json" } : {}, body: body ? JSON.stringify(body) : undefined });
    const text = await res.text();
    return { status: res.status, statusText: res.statusText, contentType: res.headers.get("content-type") ?? "", text };
  }, { method, path, body });
}

function httpLiteral(title, method, path, requestBody, r) {
  let pretty = r.text ?? r.body ?? "";
  try { pretty = JSON.stringify(JSON.parse(pretty), null, 2); } catch { /* deja el texto tal cual */ }
  return [
    `# ${title}`,
    `# ${r.at ?? localTime()} America/Mexico_City · por el proxy ${BASE_URL}`,
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

async function saveState(state) { await writeFile(statePath, JSON.stringify(state, null, 2) + "\n"); }
async function loadState() { return JSON.parse(await readFile(statePath, "utf8")); }

// ---------------------------------------------------------------- preparación
async function preparacion(browser) {
  const { context, page } = await login(browser, ADMIN);
  const users = JSON.parse((await api(page, "GET", "/api/v1/users")).text);
  const existing = users.find((u) => u.email === WAITER_2);
  if (existing) {
    log(`el segundo mesero ya existe: ${WAITER_2} (${existing.role}, activo=${existing.active})`);
  } else {
    await page.goto(`${BASE_URL}/admin`, { waitUntil: "networkidle" });
    await page.getByRole("tab", { name: "Usuarios" }).click();
    await page.getByRole("button", { name: "Nuevo usuario" }).click();
    await page.locator("#fullName").fill("Mesero de prueba 2");
    await page.locator("#email").fill(WAITER_2);
    await page.locator("#password").fill(PASSWORD);
    await capture(page, "PREP-01-alta-segundo-mesero");
    const [res] = await Promise.all([
      page.waitForResponse((r) => r.url().endsWith("/api/v1/users") && r.request().method() === "POST"),
      page.getByRole("button", { name: "Crear usuario" }).click(),
    ]);
    const r = { status: res.status(), statusText: res.statusText(), contentType: res.headers()["content-type"] ?? "", text: await res.text() };
    const reqBody = JSON.stringify({ fullName: "Mesero de prueba 2", email: WAITER_2, password: "<redactado>", role: "WAITER" }, null, 2);
    await writeFile(join(respuestas, "PREP-01-alta-segundo-mesero.txt"),
      httpLiteral("Alta del segundo mesero desde la sesión web del administrador", "POST", "/api/v1/users", reqBody, r));
    log(`alta del segundo mesero -> HTTP ${r.status}`);
    await page.waitForTimeout(800);
  }
  await page.goto(`${BASE_URL}/admin`, { waitUntil: "networkidle" });
  await page.getByRole("tab", { name: "Usuarios" }).click();
  await capture(page, "PREP-02-usuarios-del-restaurante");
  await context.close();
}

// ---------------------------------------------------------------- casos
async function freeTables(page) {
  const tables = JSON.parse((await api(page, "GET", "/api/v1/tables")).text);
  return tables.filter((t) => t.active && t.status === "FREE").sort((a, b) => a.number - b.number);
}

async function openTableFromUi(page, number, guests) {
  await page.goto(`${BASE_URL}/waiter`, { waitUntil: "networkidle" });
  const card = page.locator('[role="button"]').filter({ has: page.locator("p.font-display", { hasText: new RegExp(`^${number}$`) }) });
  await card.first().click();
  await page.locator("#guestCount").fill(String(guests));
  return card;
}

/** items: [categoría, producto, cantidad]. El selector muestra una categoría a la vez. */
async function addProducts(page, items) {
  for (const [category, name, qty] of items) {
    await page.locator("button[aria-pressed]", { hasText: category }).first().click();
    await page.getByRole("button", { name: `Agregar ${name}` }).click();
    for (let i = 1; i < qty; i += 1) {
      // el último carrito agregado es el del producto actual
      await page.getByRole("button", { name: "Agregar una unidad" }).last().click();
    }
  }
}

async function casos(browser) {
  const calls = [];
  const state = { startedAt: localTime() };

  // ---------- C01
  log("C01 · abrir, agregar y enviar a cocina (mesero 1)");
  const w1 = await login(browser, WAITER_1, calls);
  const free = await freeTables(w1.page);
  if (free.length < 2) throw new Error(`Se necesitan dos mesas libres; hay ${free.length}.`);
  const [tableA, tableB] = free;
  state.tableA = tableA.number; state.tableB = tableB.number;
  await w1.page.goto(`${BASE_URL}/waiter`, { waitUntil: "networkidle" });
  await capture(w1.page, "C01-P01-mesas-antes");

  await openTableFromUi(w1.page, tableA.number, 2);
  await capture(w1.page, "C01-P02-abrir-mesa");
  await Promise.all([
    w1.page.waitForURL(/\/waiter\/order\//, { timeout: 30_000 }),
    w1.page.getByRole("dialog").locator('button[type="submit"]').click(),
  ]);
  await w1.page.waitForLoadState("networkidle");
  state.orderA = w1.page.url().split("/").pop();
  await addProducts(w1.page, [["Entradas", "Guacamole con totopos", 2], ["Bebidas", "Margarita", 1]]);
  await capture(w1.page, "C01-P03-carrito");
  await Promise.all([
    w1.page.waitForResponse((r) => r.url().includes(`/orders/${state.orderA}/items`) && r.request().method() === "POST"),
    w1.page.getByRole("button", { name: "Agregar a la orden" }).click(),
  ]);
  await w1.page.waitForLoadState("networkidle");
  await capture(w1.page, "C01-P04-productos-agregados");
  await Promise.all([
    w1.page.waitForResponse((r) => r.url().includes(`/orders/${state.orderA}/send-to-kitchen`)),
    w1.page.getByRole("button", { name: "Enviar a cocina" }).click(),
  ]);
  await w1.page.waitForLoadState("networkidle");
  await capture(w1.page, "C01-P05-enviada-a-cocina");

  // ---------- orden B del mesero 1, que se queda en «Nueva» para C02 y C03
  log(`preparando la orden de la mesa ${tableB.number} (mesero 1, estado Nueva)`);
  await openTableFromUi(w1.page, tableB.number, 2);
  await Promise.all([
    w1.page.waitForURL(/\/waiter\/order\//, { timeout: 30_000 }),
    w1.page.getByRole("dialog").locator('button[type="submit"]').click(),
  ]);
  await w1.page.waitForLoadState("networkidle");
  state.orderB = w1.page.url().split("/").pop();
  await addProducts(w1.page, [["Entradas", "Sopa de tortilla", 1]]);
  await Promise.all([
    w1.page.waitForResponse((r) => r.url().includes(`/orders/${state.orderB}/items`) && r.request().method() === "POST"),
    w1.page.getByRole("button", { name: "Agregar a la orden" }).click(),
  ]);
  await w1.page.waitForLoadState("networkidle");
  await capture(w1.page, "C02-P01-orden-del-mesero-1");

  // ---------- C02
  log("C02 · el segundo mesero intenta operar la orden del primero");
  const w2 = await login(browser, WAITER_2, calls);
  await w2.page.goto(`${BASE_URL}/waiter/order/${state.orderB}`, { waitUntil: "networkidle" });
  await capture(w2.page, "C02-P02-mesero-2-abre-la-orden");
  await addProducts(w2.page, [["Bebidas", "Agua de horchata", 1]]);
  const [addRes] = await Promise.all([
    w2.page.waitForResponse((r) => r.url().includes(`/orders/${state.orderB}/items`) && r.request().method() === "POST"),
    w2.page.getByRole("button", { name: "Agregar a la orden" }).click(),
  ]);
  log(`mesero 2 agrega productos -> HTTP ${addRes.status()}`);
  await capture(w2.page, "C02-P03-mesero-2-agregar-rechazado");
  await w2.page.getByRole("button", { name: "Cancelar orden" }).click();
  const [cancelRes] = await Promise.all([
    w2.page.waitForResponse((r) => r.url().includes(`/orders/${state.orderB}/cancel`)),
    w2.page.getByRole("button", { name: "Sí, cancelar orden" }).click(),
  ]);
  log(`mesero 2 cancela -> HTTP ${cancelRes.status()}`);
  await capture(w2.page, "C02-P04-mesero-2-cancelar-rechazado");
  const sendB = await api(w2.page, "POST", `/api/v1/orders/${state.orderB}/send-to-kitchen`);
  sendB.at = localTime();
  log(`mesero 2 envía a cocina (API) -> HTTP ${sendB.status}`);
  await writeFile(join(respuestas, "C02-mesero-2-enviar-a-cocina.txt"),
    httpLiteral("El segundo mesero intenta enviar a cocina la orden del primero", "POST", `/api/v1/orders/${state.orderB}/send-to-kitchen`, "", sendB));
  await w1.page.goto(`${BASE_URL}/waiter/order/${state.orderB}`, { waitUntil: "networkidle" });
  await capture(w1.page, "C02-P05-orden-sin-cambios");
  await w2.context.close();

  // ---------- C03
  log("C03 · cantidad de 100 en una línea (mesero 1, dueño de la orden)");
  await addProducts(w1.page, [["Postres", "Flan napolitano", 100]]);
  await capture(w1.page, "C03-P01-interfaz-tope-99");
  await w1.page.getByRole("button", { name: "Quitar del carrito" }).last().click();
  const products = JSON.parse((await api(w1.page, "GET", "/api/v1/products")).text);
  const flan = products.find((p) => p.name === "Flan napolitano");
  const body100 = { items: [{ productId: flan.id, quantity: 100 }] };
  const q100 = await api(w1.page, "POST", `/api/v1/orders/${state.orderB}/items`, body100);
  q100.at = localTime();
  log(`cantidad 100 -> HTTP ${q100.status}`);
  await writeFile(join(respuestas, "C03-cantidad-100.txt"),
    httpLiteral("El dueño de la orden envía una línea con cantidad 100", "POST", `/api/v1/orders/${state.orderB}/items`, JSON.stringify(body100, null, 2), q100));
  await w1.page.reload({ waitUntil: "networkidle" });
  await capture(w1.page, "C03-P02-orden-sin-cambios");
  await w1.context.close();

  // ---------- respuestas registradas desde la interfaz
  const pick = (pred) => calls.filter(pred);
  const write = (file, title, list) => writeFile(join(respuestas, file),
    list.map((c) => httpLiteral(`${title} · ${c.actor}`, c.method, c.path, c.request, { ...c, text: c.body })).join("\n"));
  await write("C01-abrir-agregar-enviar.txt", "C01 · peticiones de la interfaz", pick((c) => c.path.includes(state.orderA) || (c.path === "/api/v1/orders" && c.body.includes(state.orderA))));
  await write("C02-mesero-2-desde-la-interfaz.txt", "C02 · peticiones de la interfaz", pick((c) => c.actor === WAITER_2));
  state.finishedAt = localTime();
  await saveState(state);
  log(`listo: orden A ${state.orderA} (mesa ${state.tableA}), orden B ${state.orderB} (mesa ${state.tableB})`);
}

// ---------------------------------------------------------------- limpieza
async function limpieza(browser) {
  const state = await loadState();
  const w1 = await login(browser, WAITER_1);
  const r = await api(w1.page, "POST", `/api/v1/orders/${state.orderB}/cancel`);
  r.at = localTime();
  await writeFile(join(respuestas, "LIMPIEZA-cancelar-orden-b.txt"),
    httpLiteral("Limpieza: el dueño cancela la orden de C02 y C03, que sigue en «Nueva»", "POST", `/api/v1/orders/${state.orderB}/cancel`, "", r));
  log(`limpieza: cancelar orden B -> HTTP ${r.status}`);
  await w1.page.goto(`${BASE_URL}/waiter`, { waitUntil: "networkidle" });
  await capture(w1.page, "LIMPIEZA-mesas-al-final");
  await w1.context.close();
}

const stage = process.argv[2];
if (!PASSWORD) throw new Error("Falta la variable de entorno C9_TEST_PASSWORD.");
const stages = { preparacion, casos, limpieza };
if (!stages[stage]) throw new Error("Uso: node sit-pedidos.mjs preparacion|casos|limpieza");
await mkdir(capturas, { recursive: true });
await mkdir(respuestas, { recursive: true });
const browser = await chromium.launch({ channel: "chrome" });
try {
  await stages[stage](browser);
} finally {
  await browser.close();
}
