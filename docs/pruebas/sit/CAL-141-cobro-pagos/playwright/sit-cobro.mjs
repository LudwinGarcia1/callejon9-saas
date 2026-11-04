// SIT de CAL-141 (Cobro y pagos), ejecutada con Playwright contra el frontend
// publicado y la API de Azure. Solo cliente web.
//
//   casos      preparación: el mesero abre dos mesas y las envía a cocina; cocina
//              deja lista la primera desde su tablero.
//              C02 pagos que no cubren el total de la segunda orden: 422 y la
//              orden sigue abierta.
//              C01 caja cobra la primera orden con pago dividido (efectivo y
//              tarjeta) y 10 % de propina: la venta cierra y la mesa se libera.
//              C03 el mesero, con la orden abierta en su pantalla desde antes del
//              cobro, intenta agregar productos: la orden no cambia.
//   limpieza   El administrador cancela la segunda orden (ya en cocina).
//
// Uso, desde esta carpeta con Playwright instalado (ver ../../comun/evidencia-web.mjs):
//   $env:C9_TEST_PASSWORD = "<contraseña de las cuentas de prueba>"
//   node sit-cobro.mjs casos|limpieza

import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { BASE_URL, addProducts, evidencia, freeTables, log, localTime, openTable, tableCard } from "../../comun/evidencia-web.mjs";

const ev = evidencia({ issue: "CAL-141", outDir: join(dirname(fileURLToPath(import.meta.url)), "..") });
const WAITER = "mesero@pruebas.com";
const KITCHEN = "cocina@pruebas.com";
const CASHIER = "caja@pruebas.com";
const ADMIN = "admin@pruebas.com";

const getOrder = async (page, id) => JSON.parse((await ev.api(page, "GET", `/api/v1/orders/${id}`)).text);

async function sendToKitchen(page, orderId) {
  const [res] = await Promise.all([
    page.waitForResponse((r) => r.url().includes(`/orders/${orderId}/send-to-kitchen`)),
    page.getByRole("button", { name: "Enviar a cocina" }).click(),
  ]);
  await page.waitForLoadState("networkidle");
  return res.status();
}

/** Avanza en el tablero de cocina cada partida de la orden `folio` hasta «Listo». */
async function kitchenToReady(page, folio) {
  await page.goto(`${BASE_URL}/kitchen`, { waitUntil: "networkidle" });
  const card = page.locator("article", { hasText: folio });
  await card.waitFor({ timeout: 30_000 });
  for (const label of ["Marcar como En preparación", "Marcar como Listo"]) {
    while (await card.getByRole("button", { name: label }).count()) {
      await Promise.all([
        page.waitForResponse((r) => r.url().includes("/api/v1/kitchen/items/") && r.request().method() === "POST"),
        card.getByRole("button", { name: label }).first().click(),
      ]);
      await page.waitForTimeout(400);
    }
  }
}

async function selectInCashier(page, folio) {
  await page.goto(`${BASE_URL}/cashier`, { waitUntil: "networkidle" });
  await page.locator("button", { hasText: folio }).first().click();
  await page.getByRole("heading", { name: "Cobrar cuenta" }).waitFor();
  await page.waitForLoadState("networkidle");
}

async function casos(browser) {
  const calls = [];
  const state = { startedAt: localTime() };
  await ev.health();
  const waiter = await ev.login(browser, WAITER, calls);
  const kitchen = await ev.login(browser, KITCHEN, calls);
  const cashier = await ev.login(browser, CASHIER, calls);

  // ---------- preparación
  const free = await freeTables(ev, waiter.page);
  if (free.length < 2) throw new Error(`Se necesitan dos mesas libres; hay ${free.length}.`);
  state.tableA = free[0].number;
  state.tableB = free[1].number;
  log(`PREP · orden A en la mesa ${state.tableA}`);
  state.orderA = await openTable(waiter.page, state.tableA, 2);
  state.prepAddA = await addProducts(waiter.page, state.orderA, [["Entradas", "Guacamole con totopos", 2], ["Bebidas", "Margarita", 1]]);
  state.prepSendA = await sendToKitchen(waiter.page, state.orderA);
  await ev.capture(waiter.page, "PREP-01-orden-a-enviada-a-cocina");
  state.folioA = (await getOrder(waiter.page, state.orderA)).folio;
  log(`PREP · orden B en la mesa ${state.tableB}`);
  state.orderB = await openTable(waiter.page, state.tableB, 2);
  state.prepAddB = await addProducts(waiter.page, state.orderB, [["Entradas", "Queso fundido", 1]]);
  state.prepSendB = await sendToKitchen(waiter.page, state.orderB);
  state.folioB = (await getOrder(waiter.page, state.orderB)).folio;
  log(`PREP · cocina deja lista la orden ${state.folioA}`);
  await kitchenToReady(kitchen.page, state.folioA);
  await ev.capture(kitchen.page, "PREP-02-cocina-orden-a-lista");
  const a0 = await getOrder(waiter.page, state.orderA);
  state.prepStatusA = a0.status;
  state.subtotalA = a0.total;
  log(`PREP · orden A ${state.folioA}: ${a0.status}, total ${a0.total}; orden B ${state.folioB}`);
  if (a0.status !== "READY") throw new Error("La orden A no quedó en READY.");

  // El mesero deja abierta la orden A en su pantalla antes del cobro (C03).
  await waiter.page.goto(`${BASE_URL}/waiter/order/${state.orderA}`, { waitUntil: "networkidle" });
  await ev.capture(waiter.page, "C03-P01-mesero-con-la-orden-abierta");

  // ---------- C02
  log(`C02 · pagos que no cubren el total de ${state.folioB}`);
  await selectInCashier(cashier.page, state.folioB);
  await cashier.page.getByRole("button", { name: "Agregar pago por monto libre" }).click();
  await cashier.page.getByLabel("Monto recibido 1").fill("50");
  state.c02ButtonDisabled = await cashier.page.getByRole("button", { name: "Confirmar cobro" }).isDisabled();
  await ev.capture(cashier.page, "C02-P01-caja-pago-insuficiente");
  const short = { payments: [{ method: "CASH", amount: 50.0 }], tipPercent: 0 };
  const r422 = await ev.apiSave(cashier.page, "C02-pago-insuficiente.txt",
    "Caja envía pagos que no cubren el total", "POST", `/api/v1/orders/${state.orderB}/checkout`, short);
  state.c02Status = r422.status;
  const b1 = await ev.apiSave(cashier.page, "C02-orden-sigue-abierta.txt",
    "La orden después del rechazo", "GET", `/api/v1/orders/${state.orderB}`);
  state.c02OrderStatus = JSON.parse(b1.text).status;
  await selectInCashier(cashier.page, state.folioB);
  await ev.capture(cashier.page, "C02-P02-orden-sigue-por-cobrar");

  // ---------- C01
  log(`C01 · cobro dividido de ${state.folioA} con 10 % de propina`);
  await selectInCashier(cashier.page, state.folioA);
  await cashier.page.getByLabel("Propina (%)").fill("10");
  state.totalA = Math.round(state.subtotalA * 110) / 100;
  const cash = 200;
  state.cashA = cash;
  state.cardA = Math.round((state.totalA - cash) * 100) / 100;
  await cashier.page.getByRole("button", { name: "Agregar pago por monto libre" }).click();
  await cashier.page.getByLabel("Monto recibido 1").fill(String(cash));
  await cashier.page.getByRole("button", { name: "Agregar pago por monto libre" }).click();
  await cashier.page.getByLabel("Método 2").selectOption("CARD");
  await cashier.page.getByLabel("Monto recibido 2").fill(String(state.cardA));
  await ev.capture(cashier.page, "C01-P01-caja-pago-dividido");
  const [checkout] = await Promise.all([
    cashier.page.waitForResponse((r) => r.url().includes(`/orders/${state.orderA}/checkout`)),
    cashier.page.getByRole("button", { name: "Confirmar cobro" }).click(),
  ]);
  state.c01Checkout = checkout.status();
  const ticket = JSON.parse(await checkout.text());
  state.ticketFolio = ticket.folio;
  log(`C01 · cobro -> HTTP ${state.c01Checkout}, ticket ${ticket.folio}`);
  await cashier.page.waitForLoadState("networkidle");
  await ev.capture(cashier.page, "C01-P02-ticket-del-cobro");
  const a1 = await ev.apiSave(cashier.page, "C01-orden-pagada.txt", "La orden después del cobro", "GET", `/api/v1/orders/${state.orderA}`);
  state.c01OrderStatus = JSON.parse(a1.text).status;
  const tables = await ev.apiSave(cashier.page, "C01-mesas-tras-el-cobro.txt", "Mesas tras el cobro", "GET", "/api/v1/tables");
  state.c01TableStatus = JSON.parse(tables.text).find((t) => t.number === state.tableA)?.status;
  const w2 = await ev.login(browser, "mesero2@pruebas.com");
  await w2.page.goto(`${BASE_URL}/waiter`, { waitUntil: "networkidle" });
  state.c01Card = (await tableCard(w2.page, state.tableA).innerText()).replace(/\s+/g, " ").trim();
  await ev.capture(w2.page, "C01-P03-mesa-liberada");
  await w2.context.close();

  // ---------- C03
  log(`C03 · el mesero intenta agregar productos a ${state.folioA} ya cobrada`);
  const addButton = waiter.page.getByRole("button", { name: "Agregar Queso fundido" });
  await waiter.page.locator("button[aria-pressed]", { hasText: "Entradas" }).first().click().catch(() => {});
  state.c03UiPickerOpen = await addButton.count() > 0;
  if (state.c03UiPickerOpen) {
    await addButton.click();
    const [add] = await Promise.all([
      waiter.page.waitForResponse((r) => r.url().includes(`/orders/${state.orderA}/items`) && r.request().method() === "POST"),
      waiter.page.getByRole("button", { name: "Agregar a la orden" }).click(),
    ]);
    state.c03FromUi = add.status();
    log(`C03 · desde la pantalla abierta -> HTTP ${state.c03FromUi}`);
  } else {
    log("C03 · la pantalla del mesero ya muestra la orden cerrada");
  }
  await ev.capture(waiter.page, "C03-P02-mesero-intenta-agregar");
  const products = JSON.parse((await ev.api(waiter.page, "GET", "/api/v1/products")).text);
  const queso = products.find((p) => p.name === "Queso fundido");
  const direct = await ev.apiSave(waiter.page, "C03-agregar-a-orden-cobrada.txt",
    "El mesero agrega productos a la orden ya cobrada", "POST", `/api/v1/orders/${state.orderA}/items`,
    { items: [{ productId: queso.id, quantity: 1 }] });
  state.c03Direct = direct.status;
  const a2 = await ev.apiSave(waiter.page, "C03-orden-sin-cambios.txt", "La orden después del intento", "GET", `/api/v1/orders/${state.orderA}`);
  const a2j = JSON.parse(a2.text);
  state.c03Items = a2j.items.length;
  state.c03Total = a2j.total;
  state.c03Status = a2j.status;
  await waiter.page.reload({ waitUntil: "networkidle" });
  await ev.capture(waiter.page, "C03-P03-orden-cerrada");

  await ev.writeCalls("peticiones-de-la-interfaz.txt", "Peticiones de la interfaz", calls, () => true);
  for (const s of [waiter, kitchen, cashier]) await s.context.close();
  state.finishedAt = localTime();
  await ev.saveState(state);
  log(`listo: ${JSON.stringify(state)}`);
}

async function limpieza(browser) {
  const state = await ev.loadState();
  const admin = await ev.login(browser, ADMIN);
  await ev.apiSave(admin.page, "LIMPIEZA-cancelar-orden-b.txt",
    "Limpieza: el administrador cancela la orden de C02, que ya estaba en cocina", "POST", `/api/v1/orders/${state.orderB}/cancel`);
  await admin.page.goto(`${BASE_URL}/waiter`, { waitUntil: "networkidle" });
  await ev.capture(admin.page, "LIMPIEZA-mesas-al-final");
  await admin.context.close();
}

await ev.run({ casos, limpieza });
