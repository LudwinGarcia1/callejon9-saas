// SIT de CAL-143 (Inventario), ejecutada con Playwright contra el frontend
// publicado y la API de Azure. Solo cliente web.
//
//   casos   preparación: el administrador da de alta un insumo con 10 kg.
//           C01 cocina registra una salida de 3 kg desde su pantalla y el
//           administrador ve la existencia en 7 kg.
//           C02 cocina tiene abierto el diálogo de movimiento cuando el
//           administrador da de baja el insumo; al registrar, la API lo rechaza
//           con mensaje, y tampoco acepta el movimiento enviado directo.
//   El insumo queda dado de baja: no hay borrado de insumos.
//
// Uso, desde esta carpeta con Playwright instalado (ver ../../comun/evidencia-web.mjs):
//   $env:C9_TEST_PASSWORD = "<contraseña de las cuentas de prueba>"
//   node sit-inventario.mjs casos

import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { BASE_URL, evidencia, log, localTime } from "../../comun/evidencia-web.mjs";

const ev = evidencia({ issue: "CAL-143", outDir: join(dirname(fileURLToPath(import.meta.url)), "..") });
const ADMIN = "admin@pruebas.com";
const KITCHEN = "cocina@pruebas.com";

const row = (page, name) => page.locator("tr", { has: page.getByText(name, { exact: true }) });
const items = async (page) => JSON.parse((await ev.api(page, "GET", "/api/v1/inventory/items?includeInactive=true")).text);

async function openInventory(page) {
  await page.goto(`${BASE_URL}/inventory`, { waitUntil: "networkidle" });
}

async function fillMovement(page, name, type, quantity, reason) {
  await row(page, name).getByRole("button", { name: "Movimiento" }).click();
  await page.locator("#movementType").click();
  await page.getByRole("option", { name: type, exact: true }).click();
  await page.locator("#quantity").fill(String(quantity));
  await page.locator("#reason").fill(reason);
}

async function casos(browser) {
  const calls = [];
  const state = { startedAt: localTime() };
  await ev.health();
  const hhmm = new Intl.DateTimeFormat("sv-SE", { timeZone: "America/Mexico_City", hour: "2-digit", minute: "2-digit" })
    .format(new Date()).replace(":", "");
  state.itemName = `SIT CAL-143 Tortilla ${hhmm}`;
  const admin = await ev.login(browser, ADMIN, calls);
  const kitchen = await ev.login(browser, KITCHEN, calls);

  // ---------- preparación
  log(`PREP · el administrador da de alta «${state.itemName}» con 10 kg`);
  await openInventory(admin.page);
  await admin.page.getByRole("button", { name: "Nuevo insumo" }).click();
  await admin.page.locator("#name").fill(state.itemName);
  await admin.page.locator("#unit").fill("kg");
  await admin.page.locator("#minStock").fill("2");
  await admin.page.locator("#unitCost").fill("25");
  await admin.page.locator("#initialStock").fill("10");
  await ev.capture(admin.page, "PREP-01-nuevo-insumo");
  const [created] = await Promise.all([
    admin.page.waitForResponse((r) => new URL(r.url()).pathname === "/api/v1/inventory/items" && r.request().method() === "POST"),
    admin.page.getByRole("button", { name: "Crear insumo" }).click(),
  ]);
  log(`PREP · alta del insumo -> HTTP ${created.status()}`);
  if (created.status() !== 201) throw new Error(`Alta del insumo: ${created.status()} ${await created.text()}`);
  state.itemId = JSON.parse(await created.text()).id;
  await admin.page.waitForLoadState("networkidle");
  await ev.capture(admin.page, "PREP-02-insumo-con-10-kg");

  // ---------- C01
  log("C01 · cocina registra una salida de 3 kg");
  await openInventory(kitchen.page);
  await ev.capture(kitchen.page, "C01-P01-cocina-inventario");
  await fillMovement(kitchen.page, state.itemName, "Salida", 3, "Salida para producción, SIT CAL-143");
  await ev.capture(kitchen.page, "C01-P02-cocina-registra-salida");
  const [out] = await Promise.all([
    kitchen.page.waitForResponse((r) => new URL(r.url()).pathname === "/api/v1/inventory/movements" && r.request().method() === "POST"),
    kitchen.page.getByRole("button", { name: "Registrar" }).click(),
  ]);
  state.c01Movement = out.status();
  log(`C01 · salida -> HTTP ${state.c01Movement}`);
  await kitchen.page.waitForLoadState("networkidle");
  await ev.capture(kitchen.page, "C01-P03-cocina-tras-la-salida");
  await openInventory(admin.page);
  state.c01AdminRow = (await row(admin.page, state.itemName).innerText()).replace(/\s+/g, " ").trim();
  await ev.capture(admin.page, "C01-P04-administrador-ve-7-kg");
  await ev.apiSave(admin.page, "C01-existencia-vista-por-el-administrador.txt",
    "Insumos que recibe el administrador tras la salida", "GET", "/api/v1/inventory/items?includeInactive=true");
  state.c01Stock = (await items(admin.page)).find((i) => i.id === state.itemId)?.stock;
  log(`C01 · fila del administrador: «${state.c01AdminRow}»; stock API ${state.c01Stock}`);

  // ---------- C02
  log("C02 · cocina abre el diálogo; el administrador da de baja el insumo");
  await openInventory(kitchen.page);
  await fillMovement(kitchen.page, state.itemName, "Salida", 1, "Intento sobre insumo dado de baja, SIT CAL-143");
  await ev.capture(kitchen.page, "C02-P01-cocina-dialogo-abierto");
  await openInventory(admin.page);
  const [patch] = await Promise.all([
    admin.page.waitForResponse((r) => r.url().includes(`/api/v1/inventory/items/${state.itemId}`) && r.request().method() === "PATCH"),
    row(admin.page, state.itemName).getByRole("button", { name: "Dar de baja" }).click(),
  ]);
  state.c02Deactivate = patch.status();
  log(`C02 · baja -> HTTP ${state.c02Deactivate}`);
  await admin.page.waitForLoadState("networkidle");
  await ev.capture(admin.page, "C02-P02-insumo-dado-de-baja");
  const [rejected] = await Promise.all([
    kitchen.page.waitForResponse((r) => new URL(r.url()).pathname === "/api/v1/inventory/movements" && r.request().method() === "POST"),
    kitchen.page.getByRole("button", { name: "Registrar" }).click(),
  ]);
  state.c02FromUi = rejected.status();
  log(`C02 · cocina registra -> HTTP ${state.c02FromUi}`);
  await ev.capture(kitchen.page, "C02-P03-cocina-rechazo-con-mensaje");
  state.c02Direct = (await ev.apiSave(kitchen.page, "C02-movimiento-sobre-insumo-de-baja.txt",
    "Cocina envía directo a la API una salida sobre el insumo dado de baja", "POST", "/api/v1/inventory/movements",
    { inventoryItemId: state.itemId, movementType: "OUT", quantity: 1, reason: "Intento directo, SIT CAL-143" })).status;
  await openInventory(kitchen.page);
  await ev.capture(kitchen.page, "C02-P04-cocina-inventario-final");
  await ev.apiSave(admin.page, "C02-existencia-final.txt", "Insumos tras los intentos", "GET", "/api/v1/inventory/items?includeInactive=true");
  const final = (await items(admin.page)).find((i) => i.id === state.itemId);
  state.c02Stock = final?.stock;
  state.c02Active = final?.active;
  log(`C02 · stock final ${state.c02Stock}, activo ${state.c02Active}`);

  await ev.writeCalls("peticiones-de-la-interfaz.txt", "Peticiones de la interfaz", calls, () => true);
  await admin.context.close();
  await kitchen.context.close();
  state.finishedAt = localTime();
  await ev.saveState(state);
  log(`listo: ${JSON.stringify(state)}`);
}

await ev.run({ casos });
