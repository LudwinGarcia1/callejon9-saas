// Comprueba los resultados esperados de las SIT a partir de las respuestas que
// guardan los scripts de evidencia. Los scripts registran lo que pasa; este paso
// decide si la corrida pasó, para que Jenkins marque en rojo una regresión.
//
// Uso: node verificar.mjs <carpeta de salida con CAL-138/ y CAL-139/>
import { readFile } from "node:fs/promises";
import { join } from "node:path";

const out = process.argv[2];
if (!out) throw new Error("Uso: node verificar.mjs <carpeta de salida>");

const count = (text, pattern) => (text.match(new RegExp(pattern.source, "gm")) ?? []).length;
const read = async (...parts) => {
  try { return await readFile(join(out, ...parts), "utf8"); } catch { return null; }
};

const checks = [
  ["CAL-138 C01", "La API abre la orden (201) y acepta productos y envío a cocina (200)",
    "CAL-138/respuestas/C01-abrir-agregar-enviar.txt", (t) => count(t, /^HTTP 201/m) >= 1 && count(t, /^HTTP 200/m) >= 2],
  ["CAL-138 C02", "Otro mesero no puede agregar ni cancelar desde la interfaz (403)",
    "CAL-138/respuestas/C02-mesero-2-desde-la-interfaz.txt", (t) => count(t, /^HTTP 403/m) >= 2 && count(t, /^HTTP 20\d/m) === 0],
  ["CAL-138 C02", "Otro mesero no puede enviar a cocina por la API (403)",
    "CAL-138/respuestas/C02-mesero-2-enviar-a-cocina.txt", (t) => /^HTTP 403/m.test(t)],
  ["CAL-138 C03", "Una línea con cantidad 100 se rechaza (400)",
    "CAL-138/respuestas/C03-cantidad-100.txt", (t) => /^HTTP 400/m.test(t)],
  ["CAL-139 C02", "La web consulta las mesas (200)",
    "CAL-139/respuestas/C02-web-mesas.json.txt", (t) => /^# HTTP 200$/m.test(t)],
  ["CAL-139 C02", "La web consulta los productos (200)",
    "CAL-139/respuestas/C02-web-productos.json.txt", (t) => /^# HTTP 200$/m.test(t)],
];

let failed = 0;
for (const [id, description, file, ok] of checks) {
  const text = await read(file);
  const pass = text !== null && ok(text);
  if (!pass) failed += 1;
  console.log(`${pass ? "PASA " : "FALLA"}  ${id}  ${description}${text === null ? `  (no existe ${file})` : ""}`);
}
console.log(`\n${checks.length - failed}/${checks.length} comprobaciones en verde`);
if (failed > 0) process.exitCode = 1;
