// SIT de CAL-144 (Ventas, historial y analítica), ejecutada con Playwright contra
// el frontend publicado y la API de Azure. Solo cliente web. Lee lo que cobró la
// SIT de CAL-141 el mismo día y lo busca en tres lugares.
//
//   casos   C01 historial de ventas del día visto por caja y por el administrador:
//           mismas ventas, mismo total, e incluyen el cobro de CAL-141.
//           C02 analítica del administrador para el mismo día: ventas por día y
//           mezcla de pago con los mismos totales.
//   defecto Guarda las órdenes de hoy que suma el indicador «Cobrado hoy» de /admin.
//
// Uso, desde esta carpeta con Playwright instalado (ver ../../comun/evidencia-web.mjs):
//   $env:C9_TEST_PASSWORD = "<contraseña de las cuentas de prueba>"
//   node sit-ventas.mjs casos|defecto

import { readFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { BASE_URL, evidencia, log, localTime } from "../../comun/evidencia-web.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const ev = evidencia({ issue: "CAL-144", outDir: join(here, "..") });
const CASHIER = "caja@pruebas.com";
const ADMIN = "admin@pruebas.com";
const sale141 = join(here, "..", "..", "CAL-141-cobro-pagos", "respuestas", "estado-ejecucion.json");
const TODAY = new Intl.DateTimeFormat("sv-SE", { timeZone: "America/Mexico_City" }).format(new Date());

const metric = async (page, label) =>
  (await page.getByText(label, { exact: true }).locator("xpath=..").innerText()).replace(/\s+/g, " ").trim();

async function casos(browser) {
  const state = { startedAt: localTime(), day: TODAY };
  await ev.health();
  const s141 = JSON.parse(await readFile(sale141, "utf8"));
  state.sit141 = { orderFolio: s141.folioA, ticketFolio: s141.ticketFolio, total: s141.totalA, cash: s141.cashA, card: s141.cardA };
  const cashier = await ev.login(browser, CASHIER);
  const admin = await ev.login(browser, ADMIN);
  const range = `from=${TODAY}&to=${TODAY}`;

  // ---------- C01
  log(`C01 · historial del ${TODAY} en caja y en administración`);
  await cashier.page.goto(`${BASE_URL}/history`, { waitUntil: "networkidle" });
  state.c01CashierUi = { ventas: await metric(cashier.page, "Ventas en el rango"), total: await metric(cashier.page, "Total cobrado") };
  await ev.capture(cashier.page, "C01-P01-historial-de-caja");
  const sc = JSON.parse((await ev.apiSave(cashier.page, "C01-ventas-del-dia-caja.txt", "Ventas del día con la sesión de caja",
    "GET", `/api/v1/sales?${range}`)).text);
  await admin.page.goto(`${BASE_URL}/history`, { waitUntil: "networkidle" });
  state.c01AdminUi = { ventas: await metric(admin.page, "Ventas en el rango"), total: await metric(admin.page, "Total cobrado") };
  await ev.capture(admin.page, "C01-P02-historial-del-administrador");
  const sa = JSON.parse((await ev.apiSave(admin.page, "C01-ventas-del-dia-administrador.txt", "Ventas del día con la sesión del administrador",
    "GET", `/api/v1/sales?${range}`)).text);
  state.c01Cashier = sc.summary;
  state.c01Admin = sa.summary;
  state.c01Rows = sa.sales.map((r) => ({ orderFolio: r.orderFolio, total: r.total, paymentMethod: r.paymentMethod, createdAt: r.createdAt }));
  state.c01Has141 = sa.sales.some((r) => r.orderFolio === s141.folioA && Number(r.total) === s141.totalA);
  state.c01PaymentMix = sa.paymentMix;
  log(`C01 · caja ${JSON.stringify(sc.summary)} · admin ${JSON.stringify(sa.summary)} · incluye CAL-141: ${state.c01Has141}`);
  await admin.page.goto(`${BASE_URL}/admin`, { waitUntil: "networkidle" });
  state.c01AdminHeader = await metric(admin.page, "Cobrado hoy");
  await ev.capture(admin.page, "C01-P03-administracion-cobrado-hoy");

  // ---------- C02
  log(`C02 · analítica del ${TODAY}`);
  await admin.page.goto(`${BASE_URL}/analytics`, { waitUntil: "networkidle" });
  await admin.page.locator("#from").fill(TODAY);
  await admin.page.locator("#to").fill(TODAY);
  await Promise.all([
    admin.page.waitForResponse((r) => r.url().includes("/api/v1/analytics")),
    admin.page.getByRole("button", { name: "Buscar" }).click(),
  ]);
  await admin.page.waitForLoadState("networkidle");
  await ev.capture(admin.page, "C02-P01-analitica-del-dia");
  const an = JSON.parse((await ev.apiSave(admin.page, "C02-analitica-del-dia.txt", "Analítica del día con la sesión del administrador",
    "GET", `/api/v1/analytics?${range}`)).text);
  state.c02SalesByDay = an.salesByDay;
  state.c02PaymentMix = an.paymentMix;
  state.c02Pareto = an.pareto;
  await cashier.context.close();
  await admin.context.close();
  state.finishedAt = localTime();
  await ev.saveState(state);
  log(`listo: ${JSON.stringify(state)}`);
}

/** Evidencia del defecto de «Cobrado hoy»: las órdenes de hoy que suma la pantalla de administración. */
async function defecto(browser) {
  const admin = await ev.login(browser, ADMIN);
  const r = await ev.apiSave(admin.page, "C01-ordenes-que-suma-cobrado-hoy.txt",
    "Órdenes que recibe la pantalla de administración para calcular «Cobrado hoy»", "GET", "/api/v1/orders");
  const today = JSON.parse(r.text).filter((o) => new Intl.DateTimeFormat("sv-SE", { timeZone: "America/Mexico_City" })
    .format(new Date(o.openedAt)) === TODAY);
  for (const o of today) log(`${o.folio}  ${o.status}  total ${o.total}`);
  log(`suma de total de las órdenes abiertas hoy: ${today.reduce((s, o) => s + Number(o.total), 0)}`);
  await admin.context.close();
}

await ev.run({ casos, defecto });
