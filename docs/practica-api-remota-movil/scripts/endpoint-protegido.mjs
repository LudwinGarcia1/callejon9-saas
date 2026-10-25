// Demuestra la protección de POST /api/v1/products con una sesión real de
// mesero: el login funciona, la consulta del catálogo responde 200 y el
// registro de productos responde 403 porque exige el rol ADMIN.
//
// Uso (PowerShell, desde la raíz del repositorio):
//   $s = Read-Host 'Contraseña de mesero@pruebas.com' -AsSecureString
//   $env:C9_TEST_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringAuth([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s))
//   node docs/practica-api-remota-movil/scripts/endpoint-protegido.mjs
//   Remove-Item Env:C9_TEST_PASSWORD
//
// La contraseña solo se lee de C9_TEST_PASSWORD y nunca se escribe. Los
// valores de Set-Cookie y Cookie se guardan como <redactado>.

import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const API = process.env.C9_API_URL ?? 'https://app-callejon9-api-prod-3878c6.azurewebsites.net';
const SLUG = process.env.C9_SLUG ?? 'pruebas';
const EMAIL = process.env.C9_EMAIL ?? 'mesero@pruebas.com';
const PASSWORD = process.env.C9_TEST_PASSWORD;
const OUT = join(dirname(fileURLToPath(import.meta.url)), '..', 'respuestas');

if (!PASSWORD) {
  console.error('Falta C9_TEST_PASSWORD. Revisa el uso al inicio del script.');
  process.exit(1);
}

/** Hora local de Ciudad de México (UTC-6, sin horario de verano). */
function now() {
  const local = new Date(Date.now() - 6 * 3600 * 1000).toISOString();
  return `${local.slice(0, 10)} ${local.slice(11, 19)} America/Mexico_City (UTC-6)`;
}

function literal(response, body) {
  const headers = [...response.headers.entries()]
    .map(([name, value]) => `${name}: ${name === 'set-cookie' ? '<redactado>' : value}`)
    .join('\n');
  return `HTTP/1.1 ${response.status} ${response.statusText}\n${headers}\n\n${body}\n`;
}

function save(file, command, response, body) {
  const text = `# ${now()}\n# $ ${command}\n# Cabeceras tal como las entrega fetch de Node 22.\n\n${literal(response, body)}`;
  writeFileSync(join(OUT, file), text, 'utf8');
  console.log(`${file}: ${response.status}`);
}

// 1. Login del mesero.
const login = await fetch(`${API}/api/v1/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
  body: JSON.stringify({ slug: SLUG, email: EMAIL, password: PASSWORD }),
});
const loginBody = await login.text();
save(
  'P4-login-mesero.txt',
  `POST ${API}/api/v1/auth/login  {"slug":"${SLUG}","email":"${EMAIL}","password":"<redactado>"}`,
  login,
  loginBody,
);
if (!login.ok) {
  console.error('El login no respondió 200; revisa la contraseña. No se hicieron más peticiones.');
  process.exit(1);
}
const cookie = login.headers
  .getSetCookie()
  .map((value) => value.split(';')[0])
  .join('; ');
const auth = { Cookie: cookie, Accept: 'application/json' };

// 2. Consulta autenticada del catálogo.
const list = await fetch(`${API}/api/v1/products`, { headers: auth });
const products = await list.json();
save(
  'P4-get-products-mesero-200.txt',
  `GET ${API}/api/v1/products  (Cookie: <redactado>)`,
  list,
  `[${products.length} productos; se omite el cuerpo, igual al de la captura P1 del teléfono]`,
);

// 3. Registro con el rol equivocado: el backend lo rechaza antes de tocar la base.
const create = await fetch(`${API}/api/v1/products`, {
  method: 'POST',
  headers: { ...auth, 'Content-Type': 'application/json' },
  body: JSON.stringify({ name: 'Intento de mesero', price: 10 }),
});
save(
  'P4-post-products-mesero-403.txt',
  `POST ${API}/api/v1/products  (Cookie: <redactado>)  {"name":"Intento de mesero","price":10}`,
  create,
  await create.text(),
);

// 4. Cerrar la sesión en el servidor.
const logout = await fetch(`${API}/api/v1/auth/logout`, { method: 'POST', headers: auth });
console.log(`logout: ${logout.status}`);
