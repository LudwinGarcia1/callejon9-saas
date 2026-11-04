// Cierra la orden A que deja sit-pedidos.mjs: el caso C01 la envía a cocina y
// su etapa «limpieza» solo cancela la orden B. Sin esto, cada corrida ocupa una
// mesa más del restaurante de pruebas hasta que no quedan dos libres.
//
// El administrador puede cancelar órdenes ya enviadas (CAL-113).
// Uso: OUT_DIR=<salida de sit-pedidos> node cerrar-orden-a.mjs
import { chromium } from "playwright";
import { readFile } from "node:fs/promises";
import { join } from "node:path";

const BASE_URL = process.env.BASE_URL ?? "https://restohub.azurewebsites.net";
const PASSWORD = process.env.C9_TEST_PASSWORD;
const OUT_DIR = process.env.OUT_DIR;
if (!PASSWORD) throw new Error("Falta la variable de entorno C9_TEST_PASSWORD.");
if (!OUT_DIR) throw new Error("Falta OUT_DIR con la salida de sit-pedidos.mjs.");

const state = JSON.parse(await readFile(join(OUT_DIR, "respuestas", "estado-ejecucion.json"), "utf8"));
if (!state.orderA) throw new Error("estado-ejecucion.json no trae orderA.");

const browser = await chromium.launch({ channel: "chrome" });
try {
  const context = await browser.newContext({ locale: "es-MX" });
  const page = await context.newPage();
  await page.goto(`${BASE_URL}/login?slug=pruebas`, { waitUntil: "networkidle" });
  await page.locator("#email").fill("admin@pruebas.com");
  await page.locator("#password").fill(PASSWORD);
  await page.locator('button[type="submit"]').click();
  await page.waitForURL((url) => !url.pathname.startsWith("/login"), { timeout: 60_000 });

  const status = await page.evaluate(async (orderId) => {
    const res = await fetch(`/api/v1/orders/${orderId}/cancel`, { method: "POST", credentials: "same-origin" });
    return res.status;
  }, state.orderA);
  console.log(`cerrar orden A ${state.orderA} (mesa ${state.tableA}) -> HTTP ${status}`);
  if (status !== 200) process.exitCode = 1;
} finally {
  await browser.close();
}
