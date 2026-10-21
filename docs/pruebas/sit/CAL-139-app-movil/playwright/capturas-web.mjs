// Capturas del lado web de CAL-139 (SIT · App móvil de meseros).
//
// El teléfono no se automatiza: sus capturas se toman en Expo Go. Este script
// captura lo que el issue verifica en la web y la API:
//   paridad  Caso 2: el mesero web ve las mismas mesas y productos que la app.
//   cocina   Caso 3: la cocina web recibe por WebSocket el pedido que envía el teléfono.
//
// Uso (desde esta carpeta, con Playwright instalado y Google Chrome):
//   $env:C9_TEST_PASSWORD = "<contraseña de las cuentas de prueba>"
//   node capturas-web.mjs paridad
//   node capturas-web.mjs cocina      # espera hasta WAIT_MINUTES a que llegue un pedido nuevo
//   $env:OUT_DIR = "<carpeta temporal>"  # opcional: ensayo sin tocar capturas/ ni respuestas/
//
// La contraseña solo se lee de la variable de entorno; nunca se escribe en disco.

import { chromium } from "playwright";
import { mkdir, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const BASE_URL = process.env.BASE_URL ?? "https://restohub.azurewebsites.net";
const SLUG = "pruebas";
const PASSWORD = process.env.C9_TEST_PASSWORD;
const WAIT_MINUTES = Number(process.env.WAIT_MINUTES ?? 10);
const ISSUE = "CAL-139";

// OUT_DIR sirve para ensayar el script sin escribir en la carpeta de evidencia.
const outDir = process.env.OUT_DIR ?? join(dirname(fileURLToPath(import.meta.url)), "..");
const capturas = join(outDir, "capturas");
const respuestas = join(outDir, "respuestas");

const localTime = (date = new Date()) =>
  new Intl.DateTimeFormat("sv-SE", {
    timeZone: "America/Mexico_City",
    dateStyle: "short",
    timeStyle: "medium",
  }).format(date);

/** Banda superior con URL y hora reales: sustituye la barra de direcciones y el reloj en capturas automatizadas. */
async function stamp(page) {
  const text = `Captura automatizada · Playwright · ${ISSUE} · ${page.url()} · ${localTime()} America/Mexico_City`;
  await page.evaluate((t) => {
    document.getElementById("__evidence_stamp")?.remove();
    const bar = document.createElement("div");
    bar.id = "__evidence_stamp";
    bar.textContent = t;
    Object.assign(bar.style, {
      position: "fixed", top: "0", left: "0", right: "0", zIndex: "2147483647",
      padding: "6px 12px", font: "13px/1.4 monospace", color: "#fff", background: "#b91c1c",
    });
    document.body.appendChild(bar);
    document.body.style.paddingTop = "30px";
  }, text);
}

async function capture(page, name) {
  await stamp(page);
  const file = join(capturas, `${name}.png`);
  await page.screenshot({ path: file, fullPage: true });
  console.log(`${localTime()}  captura  ${name}.png  (${page.url()})`);
}

async function login(context, email) {
  const page = await context.newPage();
  await page.goto(`${BASE_URL}/login?slug=${SLUG}`, { waitUntil: "networkidle" });
  await page.locator("#email").fill(email);
  await page.locator("#password").fill(PASSWORD);
  await page.locator('button[type="submit"]').click();
  await page.waitForURL((url) => !url.pathname.startsWith("/login"), { timeout: 60_000 });
  await page.waitForLoadState("networkidle");
  return page;
}

/** Pide un recurso por el proxy same-origin del frontend, con la cookie de la sesión del navegador. */
async function apiFromPage(page, path) {
  return page.evaluate(async (p) => {
    const res = await fetch(p, { credentials: "same-origin" });
    return { status: res.status, body: await res.json() };
  }, path);
}

async function paridad(browser) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, locale: "es-MX" });
  const page = await login(context, "mesero@pruebas.com");
  await page.goto(`${BASE_URL}/waiter`, { waitUntil: "networkidle" });
  await capture(page, "C02-P05-mesero-web-mesas");

  for (const [name, path] of [["mesas", "/api/v1/tables"], ["productos", "/api/v1/products"]]) {
    const { status, body } = await apiFromPage(page, path);
    const header = `# ${localTime()} America/Mexico_City\n# GET ${BASE_URL}${path} (sesión web de mesero@pruebas.com)\n# HTTP ${status}\n`;
    await writeFile(join(respuestas, `C02-web-${name}.json.txt`), header + JSON.stringify(body, null, 2) + "\n");
    console.log(`${localTime()}  respuesta  GET ${path} -> ${status}, ${Array.isArray(body) ? body.length : "?"} elementos`);
  }
  await context.close();
}

async function cocina(browser) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, locale: "es-MX" });
  const page = await login(context, "cocina@pruebas.com");

  const log = [];
  const note = (line) => { const l = `${localTime()}  ${line}`; log.push(l); console.log(l); };

  // El código de estado del handshake solo lo expone el protocolo de DevTools.
  const cdp = await context.newCDPSession(page);
  await cdp.send("Network.enable");
  cdp.on("Network.webSocketCreated", (e) => note(`ws creado        ${e.url}`));
  cdp.on("Network.webSocketHandshakeResponseReceived", (e) =>
    note(`ws handshake     HTTP ${e.response.status} ${e.response.statusText}`));
  cdp.on("Network.webSocketFrameReceived", (e) => {
    const data = e.response.payloadData.replace(/\s+/g, " ").slice(0, 160);
    note(`ws frame <-      ${data}`);
  });
  cdp.on("Network.webSocketClosed", () => note("ws cerrado"));

  await page.goto(`${BASE_URL}/kitchen`, { waitUntil: "networkidle" });
  const folios = async () => page.locator("article .eyebrow").allTextContents();
  const before = new Set(await folios());
  note(`tablero listo con ${before.size} orden(es): ${[...before].join(", ") || "ninguna"}`);
  await capture(page, "C03-P01-cocina-web-antes");

  note(`esperando un pedido nuevo hasta ${WAIT_MINUTES} min; envíalo ahora desde el teléfono`);
  const deadline = Date.now() + WAIT_MINUTES * 60_000;
  let nuevo;
  while (!nuevo && Date.now() < deadline) {
    await page.waitForTimeout(1000);
    nuevo = (await folios()).find((f) => !before.has(f));
  }
  if (nuevo) {
    note(`pedido nuevo en el tablero sin recargar: ${nuevo}`);
    await capture(page, "C03-P04-cocina-web-recibe-pedido");
  } else {
    note("no llegó ningún pedido nuevo en el tiempo de espera");
    await capture(page, "C03-P04-cocina-web-sin-pedido");
  }

  await writeFile(join(respuestas, "C03-registro-cocina-web.txt"),
    `# Registro de la sesión de cocina web (${BASE_URL}/kitchen), tomado del protocolo de DevTools\n${log.join("\n")}\n`);
  await context.close();
}

const stage = process.argv[2];
if (!PASSWORD) throw new Error("Falta la variable de entorno C9_TEST_PASSWORD.");
if (!["paridad", "cocina"].includes(stage)) throw new Error("Uso: node capturas-web.mjs paridad|cocina");

await mkdir(capturas, { recursive: true });
await mkdir(respuestas, { recursive: true });
const browser = await chromium.launch({ channel: "chrome" });
try {
  await (stage === "paridad" ? paridad(browser) : cocina(browser));
} finally {
  await browser.close();
}
