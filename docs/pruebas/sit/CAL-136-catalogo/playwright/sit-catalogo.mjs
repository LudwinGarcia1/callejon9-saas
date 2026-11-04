// SIT de CAL-136 (Catálogo), ejecutada por completo con Playwright contra el
// frontend publicado y la API de Azure. Solo cliente web.
//
//   casos      C01 el administrador da de alta un producto y el mesero lo ve en
//              su menú; C02 el administrador lo da de baja mientras el mesero lo
//              tiene en el carrito: deja de aparecer y no entra a la comanda.
//   limpieza   El mesero cancela la orden de prueba, que sigue en «Nueva», para
//              liberar su mesa. El producto queda dado de baja (no hay borrado).
//
// Uso, desde esta carpeta con Playwright instalado:
//   $env:C9_TEST_PASSWORD = "<contraseña de las cuentas de prueba>"
//   node sit-catalogo.mjs casos|limpieza
//   $env:OUT_DIR = "<carpeta>"   # opcional: ensayo fuera de la evidencia
//   $env:CHROME_PATH = "<ruta>"  # opcional: navegador Chromium instalado
//
// La contraseña solo se lee de la variable de entorno; nunca se escribe en disco.

import { chromium } from "playwright";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const BASE_URL = process.env.BASE_URL ?? "https://restohub.azurewebsites.net";
const API_URL = "https://app-callejon9-api-prod-3878c6.azurewebsites.net";
const SLUG = "pruebas";
const PASSWORD = process.env.C9_TEST_PASSWORD;
const ISSUE = "CAL-136";
const WAITER = "mesero@pruebas.com";
const ADMIN = "admin@pruebas.com";
const CATEGORY = "Entradas";

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
  await page.waitForTimeout(500);
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
  const r = await page.evaluate(async ({ method, path, body }) => {
    const res = await fetch(path, { method, credentials: "same-origin",
      headers: body ? { "Content-Type": "application/json" } : {}, body: body ? JSON.stringify(body) : undefined });
    const text = await res.text();
    return { status: res.status, statusText: res.statusText, contentType: res.headers.get("content-type") ?? "", text };
  }, { method, path, body });
  return { ...r, at: localTime() };
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

async function health() {
  const res = await fetch(`${API_URL}/actuator/health`);
  const text = await res.text();
  await writeFile(join(respuestas, "health.txt"), httpLiteral("Salud de la API antes de ejecutar", "GET",
    `${API_URL}/actuator/health`, "", { status: res.status, statusText: res.statusText,
      contentType: res.headers.get("content-type") ?? "", text }).replace(` · por el proxy ${BASE_URL}`, " · directo a la API"));
  log(`health -> HTTP ${res.status} ${text}`);
  if (res.status !== 200) throw new Error("La API no está en UP.");
}

const productRow = (page, name) => page.locator("tr", { has: page.getByText(name, { exact: true }) });

async function openProductsTab(page) {
  await page.goto(`${BASE_URL}/admin`, { waitUntil: "networkidle" });
  await page.getByRole("tab", { name: "Productos" }).click();
  await page.waitForLoadState("networkidle");
}

async function showCategory(page) {
  await page.locator("button[aria-pressed]", { hasText: CATEGORY }).first().click();
  await page.waitForTimeout(300);
}

// ---------------------------------------------------------------- casos
async function casos(browser) {
  const calls = [];
  const state = { startedAt: localTime() };
  await health();
  const productName = `SIT CAL-136 Tostada de prueba ${new Intl.DateTimeFormat("sv-SE", { timeZone: "America/Mexico_City", hour: "2-digit", minute: "2-digit" }).format(new Date()).replace(":", "")}`;
  state.productName = productName;

  // ---------- C01
  log("C01 · el administrador da de alta un producto");
  const admin = await login(browser, ADMIN, calls);
  await openProductsTab(admin.page);
  await capture(admin.page, "C01-P01-productos-antes");

  await admin.page.getByRole("button", { name: "Nuevo producto" }).click();
  await admin.page.locator("#name").fill(productName);
  await admin.page.locator("#description").fill("Producto creado por la SIT de catálogo");
  await admin.page.locator("#price").fill("79");
  await admin.page.locator("#categoryId").click();
  await admin.page.getByRole("option", { name: CATEGORY, exact: true }).click();
  await capture(admin.page, "C01-P02-formulario-nuevo-producto");
  const [createRes] = await Promise.all([
    admin.page.waitForResponse((r) => new URL(r.url()).pathname === "/api/v1/products" && r.request().method() === "POST"),
    admin.page.getByRole("button", { name: "Crear producto" }).click(),
  ]);
  log(`alta del producto -> HTTP ${createRes.status()}`);
  if (createRes.status() !== 201) throw new Error("El alta del producto no devolvió 201.");
  state.productId = JSON.parse(await createRes.text()).id;
  await admin.page.waitForLoadState("networkidle");
  await capture(admin.page, "C01-P03-producto-activo-en-la-lista");

  log("C01 · el mesero ve el producto en su menú");
  const waiter = await login(browser, WAITER, calls);
  const tables = JSON.parse((await api(waiter.page, "GET", "/api/v1/tables")).text)
    .filter((t) => t.active && t.status === "FREE").sort((a, b) => a.number - b.number);
  if (tables.length < 1) throw new Error("No hay mesas libres.");
  state.table = tables[0].number;
  await waiter.page.goto(`${BASE_URL}/waiter`, { waitUntil: "networkidle" });
  const card = waiter.page.locator('[role="button"]').filter({ has: waiter.page.locator("p.font-display", { hasText: new RegExp(`^${state.table}$`) }) });
  await card.first().click();
  await waiter.page.locator("#guestCount").fill("2");
  await Promise.all([
    waiter.page.waitForURL(/\/waiter\/order\//, { timeout: 30_000 }),
    waiter.page.getByRole("dialog").locator('button[type="submit"]').click(),
  ]);
  await waiter.page.waitForLoadState("networkidle");
  state.orderId = waiter.page.url().split("/").pop();
  await showCategory(waiter.page);
  const visible = await waiter.page.getByRole("button", { name: `Agregar ${productName}` }).count();
  state.c01VisibleInPicker = visible;
  log(`C01 · botón «Agregar ${productName}» en el menú del mesero: ${visible}`);
  await capture(waiter.page, "C01-P04-menu-del-mesero-con-el-producto");

  const menu1 = await api(waiter.page, "GET", "/api/v1/products");
  state.c01InWaiterMenu = JSON.parse(menu1.text).some((p) => p.id === state.productId);
  await writeFile(join(respuestas, "C01-menu-del-mesero.txt"),
    httpLiteral("Menú activo que recibe el mesero tras el alta", "GET", "/api/v1/products", "", menu1));

  // ---------- C02
  log("C02 · el mesero lleva el producto al carrito");
  await waiter.page.getByRole("button", { name: `Agregar ${productName}` }).click();
  await capture(waiter.page, "C02-P01-producto-en-el-carrito");

  log("C02 · el administrador da de baja el producto");
  await openProductsTab(admin.page);
  const [patchRes] = await Promise.all([
    admin.page.waitForResponse((r) => r.url().includes(`/api/v1/products/${state.productId}`) && r.request().method() === "PATCH"),
    productRow(admin.page, productName).getByRole("button", { name: "Dar de baja" }).click(),
  ]);
  log(`baja del producto -> HTTP ${patchRes.status()}`);
  await admin.page.waitForLoadState("networkidle");
  await capture(admin.page, "C02-P02-producto-dado-de-baja");

  log("C02 · el mesero intenta confirmar el carrito con el producto dado de baja");
  const [addRes] = await Promise.all([
    waiter.page.waitForResponse((r) => r.url().includes(`/orders/${state.orderId}/items`) && r.request().method() === "POST"),
    waiter.page.getByRole("button", { name: "Agregar a la orden" }).click(),
  ]);
  state.c02AddFromUi = addRes.status();
  log(`mesero confirma el carrito -> HTTP ${addRes.status()}`);
  await capture(waiter.page, "C02-P03-comanda-rechazada");

  await waiter.page.reload({ waitUntil: "networkidle" });
  await showCategory(waiter.page);
  state.c02VisibleAfter = await waiter.page.getByRole("button", { name: `Agregar ${productName}` }).count();
  log(`C02 · botón del producto tras recargar: ${state.c02VisibleAfter}`);
  await capture(waiter.page, "C02-P04-menu-sin-el-producto");

  const menu2 = await api(waiter.page, "GET", "/api/v1/products");
  state.c02InWaiterMenu = JSON.parse(menu2.text).some((p) => p.id === state.productId);
  await writeFile(join(respuestas, "C02-menu-del-mesero.txt"),
    httpLiteral("Menú activo que recibe el mesero tras la baja", "GET", "/api/v1/products", "", menu2));

  const body = { items: [{ productId: state.productId, quantity: 1 }] };
  const direct = await api(waiter.page, "POST", `/api/v1/orders/${state.orderId}/items`, body);
  state.c02AddDirect = direct.status;
  log(`mesero envía el producto directo a la API -> HTTP ${direct.status}`);
  await writeFile(join(respuestas, "C02-agregar-producto-inactivo-api.txt"),
    httpLiteral("El mesero envía el producto dado de baja directo a la API, sin pasar por el menú", "POST",
      `/api/v1/orders/${state.orderId}/items`, JSON.stringify(body, null, 2), direct));

  const order = await api(waiter.page, "GET", `/api/v1/orders/${state.orderId}`);
  await writeFile(join(respuestas, "C02-orden-sin-cambios.txt"),
    httpLiteral("La orden de prueba después de los dos intentos", "GET", `/api/v1/orders/${state.orderId}`, "", order));
  const o = JSON.parse(order.text);
  state.c02OrderItems = (o.items ?? []).length;
  state.c02OrderTotal = o.total;
  log(`orden tras los intentos: ${state.c02OrderItems} línea(s), total ${o.total}`);

  // ---------- respuestas registradas desde la interfaz
  const write = (file, title, list) => writeFile(join(respuestas, file),
    list.map((c) => httpLiteral(`${title} · ${c.actor}`, c.method, c.path, c.request, { ...c, text: c.body })).join("\n"));
  await write("C01-alta-desde-la-interfaz.txt", "C01 · peticiones de la interfaz",
    calls.filter((c) => c.path === "/api/v1/products" || c.path === "/api/v1/orders"));
  await write("C02-baja-y-comanda-desde-la-interfaz.txt", "C02 · peticiones de la interfaz",
    calls.filter((c) => c.path.includes(state.productId) || c.path.includes(`${state.orderId}/items`)));

  await admin.context.close();
  await waiter.context.close();
  state.finishedAt = localTime();
  await saveState(state);
  log(`listo: producto ${state.productId}, orden ${state.orderId} (mesa ${state.table})`);
}

// ---------------------------------------------------------------- limpieza
async function limpieza(browser) {
  const state = await loadState();
  const waiter = await login(browser, WAITER);
  const r = await api(waiter.page, "POST", `/api/v1/orders/${state.orderId}/cancel`);
  await writeFile(join(respuestas, "LIMPIEZA-cancelar-orden.txt"),
    httpLiteral("Limpieza: el mesero cancela la orden de prueba, que sigue en «Nueva»", "POST", `/api/v1/orders/${state.orderId}/cancel`, "", r));
  log(`limpieza: cancelar orden -> HTTP ${r.status}`);
  await waiter.page.goto(`${BASE_URL}/waiter`, { waitUntil: "networkidle" });
  await capture(waiter.page, "LIMPIEZA-mesas-al-final");
  await waiter.context.close();
}

const stage = process.argv[2];
if (!PASSWORD) throw new Error("Falta la variable de entorno C9_TEST_PASSWORD.");
const stages = { casos, limpieza };
if (!stages[stage]) throw new Error("Uso: node sit-catalogo.mjs casos|limpieza");
await mkdir(capturas, { recursive: true });
await mkdir(respuestas, { recursive: true });
// CHROME_PATH: ejecutable de un navegador Chromium ya instalado (p. ej. Brave);
// sin él, usa el Chromium que descarga Playwright.
const browser = await chromium.launch(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {});
try {
  await stages[stage](browser);
} finally {
  await browser.close();
}
