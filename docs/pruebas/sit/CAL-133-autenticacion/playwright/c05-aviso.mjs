import { chromium } from "playwright";
import { writeFile } from "node:fs/promises";
const D = process.env.OUT_DIR, BASE = "https://restohub.azurewebsites.net";
const lt = () => new Intl.DateTimeFormat("sv-SE", { timeZone: "America/Mexico_City", dateStyle: "short", timeStyle: "medium" }).format(new Date());
const b = await chromium.launch({ channel: "chrome" });
const page = await (await b.newContext({ viewport: { width: 1440, height: 900 }, locale: "es-MX" })).newPage();
await page.goto(`${BASE}/login?slug=pruebas`, { waitUntil: "networkidle" });
await page.locator("#email").fill("mesero2@pruebas.com");
await page.locator("#password").fill("Clave-Erronea-7-2026");
const [res] = await Promise.all([page.waitForResponse((r) => r.url().endsWith("/api/v1/auth/login")), page.locator('button[type="submit"]').click()]);
console.log(`${lt()}  intento 7 -> HTTP ${res.status()} (Retry-After ${res.headers()["retry-after"]})`);
await page.getByText(/Demasiados intentos|Intenta de nuevo/).first().waitFor({ timeout: 15000 }).catch(() => console.log("no apareció un texto de bloqueo"));
await page.waitForTimeout(500);
await page.evaluate((t) => { const d = document.createElement("div"); d.textContent = t; Object.assign(d.style, { position: "fixed", top: 0, left: 0, right: 0, zIndex: 2147483647, padding: "6px 12px", font: "13px/1.4 monospace", color: "#fff", background: "#b91c1c" }); document.body.appendChild(d); document.body.style.paddingTop = "30px"; },
  `Captura automatizada · Playwright · CAL-133 · ${page.url()} · ${lt()} America/Mexico_City`);
await page.screenshot({ path: `${D}/capturas/C05-P03-aviso-de-bloqueo.png`, fullPage: true });
console.log(`${lt()}  captura C05-P03-aviso-de-bloqueo.png (${page.url()})`);
await writeFile(`${D}/respuestas/C05-intento-7.txt`, `# C05 · intento 7, solo para capturar el aviso que ve el usuario bloqueado\n# ${lt()} America/Mexico_City\nPOST /api/v1/auth/login\n\nHTTP ${res.status()}\nretry-after: ${res.headers()["retry-after"]}\n\n${await res.text()}\n`);
await b.close();
