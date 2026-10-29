// SIT de CAL-145, caso C02 (aislamiento entre restaurantes), con Playwright contra
// el frontend publicado y la API de Azure. Usa el restaurante B que prepara la SIT
// de CAL-142 (../../CAL-142-tickets/respuestas/estado-restaurante-b.json).
//
//   c02   Desde el restaurante `pruebas`, el mesero pide la orden del restaurante B,
//         intenta abrir una orden sobre su mesa y busca esa mesa en su lista:
//         404 y nada visible. Control: el administrador de B sí ve su orden.
//
// Uso, desde esta carpeta con Playwright instalado:
//   $env:C9_TEST_PASSWORD = "<contraseña de las cuentas de prueba>"
//   node ../../CAL-142-tickets/playwright/sit-tickets.mjs preparacion   # una sola vez
//   node sit-aislamiento.mjs c02

import { existsSync } from "node:fs";
import { readFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { BASE_URL, evidencia, log, localTime } from "../../comun/evidencia-web.mjs";
import { loginB } from "../../comun/restaurante-b.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const ev = evidencia({ issue: "CAL-145", outDir: join(here, "..") });
const WAITER = "mesero@pruebas.com";
const stateB = join(here, "..", "..", "CAL-142-tickets", "respuestas", "estado-restaurante-b.json");

async function c02(browser) {
  if (!existsSync(stateB)) throw new Error("Falta el restaurante B: corre antes sit-tickets.mjs preparacion.");
  const b = JSON.parse(await readFile(stateB, "utf8"));
  const state = { startedAt: localTime(), restaurantB: b.slug, orderB: b.orderId, tableB: b.tableId };
  const waiter = await ev.login(browser, WAITER);

  log(`C02 · el mesero de pruebas pide la orden ${b.orderFolio} del restaurante B`);
  state.getOrder = (await ev.apiSave(waiter.page, "C02-orden-de-otro-restaurante.txt",
    "El mesero de `pruebas` pide la orden del restaurante B", "GET", `/api/v1/orders/${b.orderId}`)).status;
  await waiter.page.goto(`${BASE_URL}/waiter/order/${b.orderId}`, { waitUntil: "networkidle" });
  await ev.capture(waiter.page, "C02-P01-orden-de-otro-restaurante");

  log("C02 · intenta abrir una orden sobre la mesa del restaurante B");
  state.openOnTableB = (await ev.apiSave(waiter.page, "C02-abrir-orden-en-mesa-de-otro-restaurante.txt",
    "El mesero de `pruebas` intenta abrir una orden sobre la mesa del restaurante B", "POST", "/api/v1/orders",
    { tableId: b.tableId, guestCount: 1 })).status;

  const tables = await ev.apiSave(waiter.page, "C02-mesas-de-pruebas.txt",
    "Mesas que recibe el mesero de `pruebas`", "GET", "/api/v1/tables");
  state.tableBInList = JSON.parse(tables.text).some((t) => t.id === b.tableId);
  await waiter.context.close();

  log("C02 · control: el administrador de B sí ve su orden");
  const adminB = await loginB(ev, browser, b);
  const own = await ev.apiSave(adminB.page, "C02-restaurante-b-su-propia-orden.txt",
    "El administrador del restaurante B pide su propia orden", "GET", `/api/v1/orders/${b.orderId}`);
  state.ownOrder = own.status;
  state.ownOrderStatus = JSON.parse(own.text).status;
  const ownTables = JSON.parse((await ev.api(adminB.page, "GET", "/api/v1/tables")).text);
  state.ownTableCount = ownTables.length;
  await adminB.context.close();

  state.finishedAt = localTime();
  await ev.saveState(state);
  log(`listo: ${JSON.stringify(state)}`);
}

await ev.run({ c02 });
