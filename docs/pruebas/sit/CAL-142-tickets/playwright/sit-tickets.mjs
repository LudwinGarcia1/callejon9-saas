// SIT de CAL-142 (Tickets), ejecutada con Playwright contra el frontend publicado
// y la API de Azure. Solo cliente web.
//
//   preparacion  Da de alta el restaurante B con una venta propia (../../comun/restaurante-b.mjs).
//   casos        C01 caja busca por folio el ticket cobrado en la SIT de CAL-141 y
//                descarga su PDF: application/pdf con folio, partidas, propina y total.
//                C02 caja de `pruebas` busca el folio de la venta del restaurante B: 404;
//                el administrador de B sí lo encuentra.
//
// Uso, desde esta carpeta con Playwright y pdftotext instalados:
//   $env:C9_TEST_PASSWORD = "<contraseña de las cuentas de prueba>"
//   node sit-tickets.mjs preparacion|casos

import { execFileSync } from "node:child_process";
import { existsSync } from "node:fs";
import { readFile, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { BASE_URL, evidencia, log, localTime } from "../../comun/evidencia-web.mjs";
import { loginB, prepararRestauranteB } from "../../comun/restaurante-b.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const ev = evidencia({ issue: "CAL-142", outDir: join(here, "..") });
const CASHIER = "caja@pruebas.com";
const stateB = join(ev.respuestas, "estado-restaurante-b.json");
const sale141 = join(here, "..", "..", "CAL-141-cobro-pagos", "respuestas", "estado-ejecucion.json");

async function searchFolio(page, folio) {
  await page.goto(`${BASE_URL}/history`, { waitUntil: "networkidle" });
  await page.locator("#folio").fill(folio);
  const [res] = await Promise.all([
    page.waitForResponse((r) => r.url().includes("/api/v1/tickets?folio=")),
    page.locator("form", { has: page.locator("#folio") }).getByRole("button", { name: "Buscar" }).click(),
  ]);
  await page.waitForTimeout(600);
  return res.status();
}

async function preparacion(browser) {
  await ev.health();
  await prepararRestauranteB(ev, browser, stateB);
}

async function casos(browser) {
  const calls = [];
  const state = { startedAt: localTime() };
  await ev.health();
  const sale = JSON.parse(await readFile(sale141, "utf8"));
  state.folio = sale.ticketFolio;
  const cashier = await ev.login(browser, CASHIER, calls);

  // ---------- C01
  log(`C01 · caja busca ${state.folio} y descarga el PDF`);
  state.c01Search = await searchFolio(cashier.page, state.folio);
  await ev.capture(cashier.page, "C01-P01-ticket-encontrado");
  const byFolio = await ev.apiSave(cashier.page, "C01-ticket-por-folio.txt", "Ticket por folio desde la sesión de caja",
    "GET", `/api/v1/tickets?folio=${state.folio}`);
  const ticket = JSON.parse(byFolio.text);
  state.ticketId = ticket.id;
  const [download, pdfRes] = await Promise.all([
    cashier.page.waitForEvent("download"),
    cashier.page.waitForResponse((r) => r.url().includes(`/api/v1/tickets/${ticket.id}/pdf`)),
    cashier.page.getByRole("button", { name: "Descargar ticket en PDF" }).click(),
  ]);
  const pdfPath = join(ev.respuestas, `C01-${download.suggestedFilename()}`);
  await download.saveAs(pdfPath);
  state.pdfStatus = pdfRes.status();
  state.pdfContentType = pdfRes.headers()["content-type"];
  state.pdfDisposition = pdfRes.headers()["content-disposition"] ?? "";
  state.pdfFile = download.suggestedFilename();
  await writeFile(join(ev.respuestas, "C01-descarga-pdf.txt"), [
    "# Descarga del PDF desde «Descargar ticket en PDF» (sesión de caja)",
    `# ${localTime()} America/Mexico_City · por el proxy ${BASE_URL}`,
    `GET /api/v1/tickets/${ticket.id}/pdf`, "",
    `HTTP ${pdfRes.status()} ${pdfRes.statusText()}`.trim(),
    `Content-Type: ${state.pdfContentType}`,
    state.pdfDisposition ? `Content-Disposition: ${state.pdfDisposition}` : "", "",
    `<${(await readFile(pdfPath)).length} bytes; guardado como ${state.pdfFile}>`, "",
  ].join("\n"));
  const text = execFileSync("pdftotext", ["-layout", pdfPath, "-"], { encoding: "utf8" });
  await writeFile(join(ev.respuestas, "C01-texto-del-pdf.txt"), `# Texto extraído con «pdftotext -layout ${state.pdfFile} -»\n\n${text}`);
  const has = (s) => text.includes(s);
  state.pdfChecks = { folio: has(state.folio), guacamole: has("Guacamole con totopos"), margarita: has("Margarita"),
    propina: /propina/i.test(text), tip: has("32.50"), total: has("357.50") };
  log(`C01 · PDF ${state.pdfStatus} ${state.pdfContentType}; ${JSON.stringify(state.pdfChecks)}`);
  await ev.capture(cashier.page, "C01-P02-pdf-descargado");

  // ---------- C02 (solo si el restaurante B ya existe; ver «preparacion»)
  if (!existsSync(stateB)) {
    log("C02 · sin restaurante B: no se ejecuta");
    state.c02 = "no ejecutado: falta el restaurante B";
  } else {
  const b = JSON.parse(await readFile(stateB, "utf8"));
  log(`C02 · caja de pruebas busca el folio ${b.ticketFolio} del restaurante B`);
  state.c02Search = await searchFolio(cashier.page, b.ticketFolio);
  await ev.capture(cashier.page, "C02-P01-folio-de-otro-restaurante");
  state.c02ByFolio = (await ev.apiSave(cashier.page, "C02-folio-de-otro-restaurante.txt",
    "Caja de `pruebas` pide el ticket del restaurante B por folio", "GET", `/api/v1/tickets?folio=${b.ticketFolio}`)).status;
  state.c02ById = (await ev.apiSave(cashier.page, "C02-ticket-de-otro-restaurante-por-id.txt",
    "Caja de `pruebas` pide el ticket del restaurante B por su id", "GET", `/api/v1/tickets/${b.ticketId}`)).status;
  state.c02Pdf = (await ev.apiSave(cashier.page, "C02-pdf-de-otro-restaurante.txt",
    "Caja de `pruebas` pide el PDF del ticket del restaurante B", "GET", `/api/v1/tickets/${b.ticketId}/pdf`)).status;
  const adminB = await loginB(ev, browser, b);
  state.c02OwnSearch = await searchFolio(adminB.page, b.ticketFolio);
  await ev.capture(adminB.page, "C02-P02-restaurante-b-encuentra-su-folio");
  state.c02Own = (await ev.apiSave(adminB.page, "C02-restaurante-b-su-propio-folio.txt",
    "El administrador del restaurante B pide su propio ticket por folio", "GET", `/api/v1/tickets?folio=${b.ticketFolio}`)).status;
  await adminB.context.close();
  }

  await ev.writeCalls("peticiones-de-la-interfaz.txt", "Peticiones de la interfaz", calls, () => true);
  await cashier.context.close();
  state.finishedAt = localTime();
  await ev.saveState(state);
  log(`listo: ${JSON.stringify(state)}`);
}

await ev.run({ preparacion, casos });
