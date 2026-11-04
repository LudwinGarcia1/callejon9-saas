// SIT de CAL-137 (Mesas), ejecutada con Playwright contra el frontend publicado y
// la API de Azure. Solo cliente web: el «otro cliente» es una segunda sesión web
// de otro mesero, en otro contexto de navegador.
//
//   casos      C01 el mesero 1 abre una mesa y ambas sesiones la ven ocupada;
//              C02 el mesero 1 cancela la orden y ambas la ven libre;
//              C03 el mesero 2 intenta abrir una mesa que el mesero 1 ocupó
//              mientras él tenía abierto el diálogo: 409.
//   limpieza   El mesero 1 cancela la orden de C03, que sigue en «Nueva».
//
// Uso, desde esta carpeta con Playwright instalado (ver ../../comun/evidencia-web.mjs):
//   $env:C9_TEST_PASSWORD = "<contraseña de las cuentas de prueba>"
//   node sit-mesas.mjs casos|limpieza

import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { BASE_URL, evidencia, freeTables, log, localTime, openTable, tableCard } from "../../comun/evidencia-web.mjs";

const ev = evidencia({ issue: "CAL-137", outDir: join(dirname(fileURLToPath(import.meta.url)), "..") });
const WAITER_1 = "mesero@pruebas.com";
const WAITER_2 = "mesero2@pruebas.com";

const cardText = async (page, number) => (await tableCard(page, number).innerText()).replace(/\s+/g, " ").trim();
const tableStatus = async (page, number) =>
  JSON.parse((await ev.api(page, "GET", "/api/v1/tables")).text).find((t) => t.number === number)?.status;

async function grid(page) {
  await page.goto(`${BASE_URL}/waiter`, { waitUntil: "networkidle" });
}

async function casos(browser) {
  const calls = [];
  const state = { startedAt: localTime() };
  await ev.health();
  const w1 = await ev.login(browser, WAITER_1, calls);
  const w2 = await ev.login(browser, WAITER_2, calls);
  const free = await freeTables(ev, w1.page);
  if (free.length < 2) throw new Error(`Se necesitan dos mesas libres; hay ${free.length}.`);
  state.tableA = free[0].number;
  state.tableB = free[1].number;

  // ---------- C01
  log(`C01 · el mesero 1 abre la mesa ${state.tableA}`);
  await grid(w2.page);
  state.c01Before = await cardText(w2.page, state.tableA);
  await ev.capture(w2.page, "C01-P01-mesero-2-mesa-libre");
  state.orderA = await openTable(w1.page, state.tableA, 2);
  await ev.capture(w1.page, "C01-P02-mesero-1-orden-abierta");
  await grid(w1.page);
  state.c01AfterW1 = await cardText(w1.page, state.tableA);
  await ev.capture(w1.page, "C01-P03-mesero-1-mesa-ocupada");
  await grid(w2.page);
  state.c01AfterW2 = await cardText(w2.page, state.tableA);
  await ev.capture(w2.page, "C01-P04-mesero-2-mesa-ocupada");
  await ev.apiSave(w2.page, "C01-mesas-vistas-por-el-mesero-2.txt", "Mesas que recibe el mesero 2 tras la apertura", "GET", "/api/v1/tables");
  state.c01ApiStatus = await tableStatus(w2.page, state.tableA);
  log(`C01 · mesa ${state.tableA}: w1 «${state.c01AfterW1}» · w2 «${state.c01AfterW2}» · API ${state.c01ApiStatus}`);

  // ---------- C03 (antes de C02 para no depender de la mesa de C01)
  log(`C03 · el mesero 2 deja abierto el diálogo de la mesa ${state.tableB}`);
  await grid(w2.page);
  await tableCard(w2.page, state.tableB).click();
  await w2.page.locator("#guestCount").fill("3");
  await ev.capture(w2.page, "C03-P01-mesero-2-dialogo-abierto");
  log(`C03 · el mesero 1 abre la mesa ${state.tableB} primero`);
  state.orderB = await openTable(w1.page, state.tableB, 2);
  await ev.capture(w1.page, "C03-P02-mesero-1-abre-primero");
  const [conflict] = await Promise.all([
    w2.page.waitForResponse((r) => new URL(r.url()).pathname === "/api/v1/orders" && r.request().method() === "POST"),
    w2.page.getByRole("dialog").locator('button[type="submit"]').click(),
  ]);
  state.c03FromUi = conflict.status();
  log(`C03 · el mesero 2 confirma el diálogo -> HTTP ${state.c03FromUi}`);
  await ev.capture(w2.page, "C03-P03-mesero-2-rechazo-409");
  const tableB = JSON.parse((await ev.api(w2.page, "GET", "/api/v1/tables")).text).find((t) => t.number === state.tableB);
  const direct = await ev.apiSave(w2.page, "C03-abrir-mesa-ocupada-api.txt",
    "El mesero 2 pide abrir directo en la API la mesa que ya ocupa el mesero 1", "POST", "/api/v1/orders",
    { tableId: tableB.id, guestCount: 3 });
  state.c03Direct = direct.status;
  const ordersB = await ev.apiSave(w1.page, "C03-orden-del-mesero-1-intacta.txt",
    "La orden del mesero 1 sobre la mesa después de los intentos", "GET", `/api/v1/orders/${state.orderB}`);
  state.c03OrderB = JSON.parse(ordersB.text).status;

  // ---------- C02
  log(`C02 · el mesero 1 cancela la orden de la mesa ${state.tableA}`);
  await w1.page.goto(`${BASE_URL}/waiter/order/${state.orderA}`, { waitUntil: "networkidle" });
  await w1.page.getByRole("button", { name: "Cancelar orden" }).click();
  await ev.capture(w1.page, "C02-P01-confirmar-cancelacion");
  const [cancel] = await Promise.all([
    w1.page.waitForResponse((r) => r.url().includes(`/orders/${state.orderA}/cancel`)),
    w1.page.getByRole("button", { name: "Sí, cancelar orden" }).click(),
  ]);
  state.c02Cancel = cancel.status();
  log(`C02 · cancelar -> HTTP ${state.c02Cancel}`);
  await w1.page.waitForLoadState("networkidle");
  await ev.capture(w1.page, "C02-P02-orden-cancelada");
  await grid(w1.page);
  state.c02AfterW1 = await cardText(w1.page, state.tableA);
  await ev.capture(w1.page, "C02-P03-mesero-1-mesa-libre");
  await grid(w2.page);
  state.c02AfterW2 = await cardText(w2.page, state.tableA);
  await ev.capture(w2.page, "C02-P04-mesero-2-mesa-libre");
  await ev.apiSave(w2.page, "C02-mesas-vistas-por-el-mesero-2.txt", "Mesas que recibe el mesero 2 tras la cancelación", "GET", "/api/v1/tables");
  state.c02ApiStatus = await tableStatus(w2.page, state.tableA);
  log(`C02 · mesa ${state.tableA}: w1 «${state.c02AfterW1}» · w2 «${state.c02AfterW2}» · API ${state.c02ApiStatus}`);

  await ev.writeCalls("C01-C02-C03-peticiones-de-la-interfaz.txt", "Peticiones de la interfaz", calls, () => true);
  await w1.context.close();
  await w2.context.close();
  state.finishedAt = localTime();
  await ev.saveState(state);
  log(`listo: ${JSON.stringify(state)}`);
}

async function limpieza(browser) {
  const state = await ev.loadState();
  const w1 = await ev.login(browser, WAITER_1);
  await ev.apiSave(w1.page, "LIMPIEZA-cancelar-orden-c03.txt",
    "Limpieza: el mesero 1 cancela la orden de C03, que sigue en «Nueva»", "POST", `/api/v1/orders/${state.orderB}/cancel`);
  await grid(w1.page);
  await ev.capture(w1.page, "LIMPIEZA-mesas-al-final");
  await w1.context.close();
}

await ev.run({ casos, limpieza });
