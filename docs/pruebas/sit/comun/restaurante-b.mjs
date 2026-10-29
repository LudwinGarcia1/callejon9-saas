// Segundo restaurante para las pruebas de aislamiento entre restaurantes (CAL-142
// C02 y CAL-145 C02). Se da de alta por el registro público, igual que en la SIT
// de CAL-134, y su administrador crea una mesa, un producto y una venta cobrada.
//
// La contraseña de su administrador se genera al azar la primera vez y se guarda
// fuera del repositorio, en ~/.config/callejon9-sit-restaurante-b-password
// (permisos 600). La evidencia solo registra slug, correo e IDs.

import { randomBytes } from "node:crypto";
import { existsSync } from "node:fs";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { homedir } from "node:os";
import { join } from "node:path";
import { BASE_URL, httpLiteral, localTime, log } from "./evidencia-web.mjs";

const PASSWORD_FILE = join(homedir(), ".config", "callejon9-sit-restaurante-b-password");

async function passwordB() {
  if (existsSync(PASSWORD_FILE)) return (await readFile(PASSWORD_FILE, "utf8")).trim();
  const pwd = `Sit-${randomBytes(18).toString("base64url")}-9q!`;
  await mkdir(join(homedir(), ".config"), { recursive: true });
  await writeFile(PASSWORD_FILE, pwd, { mode: 0o600 });
  return pwd;
}

export async function loginB(ev, browser, state, sink) {
  return ev.login(browser, state.adminEmail, sink, { slug: state.slug, password: await passwordB() });
}

/** Crea el restaurante B y su venta, o reutiliza el estado guardado en `statePath`. */
export async function prepararRestauranteB(ev, browser, statePath) {
  if (existsSync(statePath)) {
    const state = JSON.parse(await readFile(statePath, "utf8"));
    log(`restaurante B ya preparado: ${state.slug} (${state.ticketFolio})`);
    return state;
  }
  const password = await passwordB();
  const stamp = new Intl.DateTimeFormat("sv-SE", { timeZone: "America/Mexico_City", month: "2-digit", day: "2-digit",
    hour: "2-digit", minute: "2-digit" }).format(new Date()).replace(/\D/g, "");
  const state = { preparedAt: localTime(), slug: `sit-restaurante-b-${stamp}` };
  state.adminEmail = `admin@${state.slug}.test`;

  // Alta pública desde un navegador sin sesión, por el proxy del frontend.
  const anon = await browser.newContext({ locale: "es-MX" });
  const page = await anon.newPage();
  await page.goto(`${BASE_URL}/signup`, { waitUntil: "networkidle" });
  const body = { restaurantName: "SIT Restaurante B", slug: state.slug, adminEmail: state.adminEmail,
    adminFullName: "Administrador SIT B", password, planCode: "FREE" };
  const signup = await ev.api(page, "POST", "/api/v1/signup", body);
  await writeFile(join(ev.respuestas, "PREP-B-01-alta-restaurante-b.txt"), httpLiteral(
    "Alta pública del restaurante B", "POST", "/api/v1/signup",
    JSON.stringify({ ...body, password: "<redactado>" }, null, 2), signup));
  log(`alta del restaurante B -> HTTP ${signup.status}`);
  if (signup.status !== 201) throw new Error(`El alta del restaurante B devolvió ${signup.status}: ${signup.text}`);
  state.tenantId = JSON.parse(signup.text).tenantId;
  await anon.close();

  const admin = await ev.login(browser, state.adminEmail, null, { slug: state.slug, password });
  const step = async (file, title, method, path, payload) => {
    const r = await ev.apiSave(admin.page, file, title, method, path, payload);
    if (r.status >= 300) throw new Error(`${title}: HTTP ${r.status} ${r.text}`);
    return JSON.parse(r.text);
  };
  state.tableId = (await step("PREP-B-02-mesa.txt", "Restaurante B: el administrador crea la mesa 1", "POST", "/api/v1/tables",
    { number: 1, capacity: 4 })).id;
  const product = await step("PREP-B-03-producto.txt", "Restaurante B: el administrador crea un producto", "POST", "/api/v1/products",
    { name: "Producto del restaurante B", description: "SIT de aislamiento", price: 50 });
  state.productId = product.id;
  const order = await step("PREP-B-04-orden.txt", "Restaurante B: el administrador abre una orden en la mesa 1", "POST", "/api/v1/orders",
    { tableId: state.tableId, guestCount: 1 });
  state.orderId = order.id;
  state.orderFolio = order.folio;
  await step("PREP-B-05-partida.txt", "Restaurante B: agrega el producto", "POST", `/api/v1/orders/${state.orderId}/items`,
    { items: [{ productId: state.productId, quantity: 1 }] });
  const ticket = await step("PREP-B-06-cobro.txt", "Restaurante B: el administrador cobra la orden", "POST",
    `/api/v1/orders/${state.orderId}/checkout`, { payments: [{ method: "CASH", amount: 50 }], tipPercent: 0 });
  state.ticketId = ticket.id;
  state.ticketFolio = ticket.folio;
  await admin.context.close();
  await writeFile(statePath, JSON.stringify(state, null, 2) + "\n");
  log(`restaurante B listo: ${state.slug}, ticket ${state.ticketFolio}`);
  return state;
}
