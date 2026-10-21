import { chromium } from "playwright";
import { writeFile } from "node:fs/promises";
const D = process.env.OUT_DIR, BASE = "https://restohub.azurewebsites.net";
const lt = () => new Intl.DateTimeFormat("sv-SE", { timeZone: "America/Mexico_City", dateStyle: "short", timeStyle: "medium" }).format(new Date());
const b = await chromium.launch({ channel: "chrome" });
const page = await (await b.newContext({ locale: "es-MX" })).newPage();
await page.goto(`${BASE}/login?slug=pruebas`, { waitUntil: "networkidle" });
const lines = [`# C05 · fase A: logins fallidos por el proxy de Next.js con correos inexistentes distintos`,
  `# Inicio ${lt()} America/Mexico_City. Antes de esta fase, 6 fallos contaban para la misma IP (C02 y C05 intentos 1-5).`, ""];
let first429 = null;
for (let i = 1; i <= 30; i++) {
  const r = await page.evaluate(async (i) => {
    const res = await fetch("/api/v1/auth/login", { method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ slug: "pruebas", email: `ip-${i}@noexiste.test`, password: "Clave-Erronea-2026" }) });
    return { status: res.status, retryAfter: res.headers.get("retry-after"), body: await res.text() };
  }, i);
  lines.push(`${lt()}  intento ${String(i).padStart(2)} · ip-${i}@noexiste.test -> HTTP ${r.status}${r.retryAfter ? ` (Retry-After ${r.retryAfter})` : ""}`);
  console.log(lines.at(-1));
  if (r.status === 429) { first429 = { i, ...r }; break; }
}
lines.push("", first429 ? `Primer 429 en el intento ${first429.i} de esta fase (fallo ${6 + first429.i} contando los anteriores).` : "Ningún 429 en 30 intentos.", "");
if (first429) lines.push("Cuerpo del 429:", (() => { try { return JSON.stringify(JSON.parse(first429.body), null, 2); } catch { return first429.body; } })(), "");
await writeFile(`${D}/respuestas/C05-clientip-fase-a-proxy.txt`, lines.join("\n"));
await b.close();
