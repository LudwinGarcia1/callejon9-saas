// Observador nativo del canal en tiempo real (/ws) de la API, para la SIT.
//
// Ni el frontend web ni la app móvil abren el WebSocket del backend, así que
// el canal no se puede observar desde DevTools. Este script hace lo que haría
// un cliente nativo: inicia sesión contra la API, abre /ws con la cookie de
// esa sesión, habla STOMP y registra con hora cada trama y el cierre.
//
//   CAL-140  Caso 1: con --suscribir-cocina, recibe el evento del pedido enviado.
//   CAL-140  Caso 3: con --handshake-origen, solo hace el handshake con sesión
//            y ese Origin, y guarda la respuesta HTTP (101 o 403).
//   CAL-135  Caso 2: la conexión se cierra (1008) al desactivar al usuario.
//
// Uso (Node 22 o posterior, sin dependencias):
//   $env:C9_TEST_PASSWORD = "<contraseña de la cuenta>"
//   node observador-ws.mjs --email cocina@pruebas.com --suscribir-cocina --minutos 10 --salida ..\respuestas\C01-observador-ws.txt
//   node observador-ws.mjs --email cocina@pruebas.com --handshake-origen https://restohub.azurewebsites.net --salida ..\respuestas\C03-handshake.txt
//
// Opciones: --slug (pruebas), --password-env (C9_TEST_PASSWORD), --api (API de Azure).
// La contraseña solo se lee de la variable de entorno y los valores de las
// cookies nunca se imprimen ni se escriben en disco.

import { randomBytes } from "node:crypto";
import { writeFile } from "node:fs/promises";
import { request as httpsRequest } from "node:https";
import { parseArgs } from "node:util";

const { values: opts } = parseArgs({
  options: {
    email: { type: "string" },
    slug: { type: "string", default: "pruebas" },
    "password-env": { type: "string", default: "C9_TEST_PASSWORD" },
    api: { type: "string", default: "https://app-callejon9-api-prod-3878c6.azurewebsites.net" },
    "suscribir-cocina": { type: "boolean", default: false },
    "handshake-origen": { type: "string" },
    minutos: { type: "string", default: "10" },
    salida: { type: "string" },
  },
});

const PASSWORD = process.env[opts["password-env"]];
if (!opts.email) throw new Error("Falta --email.");
if (!PASSWORD) throw new Error(`Falta la variable de entorno ${opts["password-env"]}.`);

const API = opts.api.replace(/\/$/, "");
const WS_URL = `${API.replace(/^http/, "ws")}/ws`;

const localTime = (date = new Date()) =>
  new Intl.DateTimeFormat("sv-SE", {
    timeZone: "America/Mexico_City",
    dateStyle: "short",
    timeStyle: "medium",
  }).format(date);

const log = [];
const note = (line) => {
  const l = `${localTime()}  ${line}`;
  log.push(l);
  console.log(l);
};

async function save() {
  if (!opts.salida) return;
  const header = [
    `# Observador del canal en tiempo real · ${WS_URL}`,
    `# Cuenta: ${opts.email} (restaurante ${opts.slug}) · horas en America/Mexico_City`,
    `# Valores de cookies omitidos; el script nunca los imprime`,
  ];
  await writeFile(opts.salida, [...header, ...log].join("\n") + "\n");
}

/** Inicia sesión como lo hace la app móvil y devuelve la cabecera Cookie (sin imprimir sus valores). */
async function login() {
  const res = await fetch(`${API}/api/v1/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify({ slug: opts.slug, email: opts.email, password: PASSWORD }),
  });
  if (!res.ok) {
    note(`login            POST /api/v1/auth/login -> HTTP ${res.status}`);
    throw new Error("No se pudo iniciar sesión.");
  }
  const body = await res.json();
  const cookies = res.headers.getSetCookie().map((c) => c.split(";")[0]);
  note(`login            POST /api/v1/auth/login -> HTTP ${res.status}, rol ${body.role}, cookies: ${cookies.map((c) => c.split("=")[0]).join(", ")}`);
  if (body.twoFactorRequired) throw new Error("La cuenta pide 2FA; usa una sin 2FA.");
  return cookies.join("; ");
}

async function me(cookie) {
  const res = await fetch(`${API}/api/v1/auth/me`, { headers: { Cookie: cookie, Accept: "application/json" } });
  const body = await res.json();
  note(`sesión           GET /api/v1/auth/me -> HTTP ${res.status}, ${body.fullName} (${body.role}), restaurante ${body.slug}`);
  return body;
}

const frame = (command, headers = {}) =>
  `${command}\n${Object.entries(headers).map(([k, v]) => `${k}:${v}`).join("\n")}\n\n\0`;

/** Separa una trama STOMP en comando, cabeceras y cuerpo. Los latidos ("\n") se ignoran. */
function parseFrames(data) {
  return String(data).split("\0").map((raw) => raw.replace(/^\n+/, "")).filter(Boolean).map((raw) => {
    const [head, ...rest] = raw.split("\n\n");
    const [command, ...headerLines] = head.split("\n");
    const headers = Object.fromEntries(headerLines.map((l) => [l.slice(0, l.indexOf(":")), l.slice(l.indexOf(":") + 1)]));
    return { command, headers, body: rest.join("\n\n") };
  });
}

/**
 * Handshake de WebSocket con la sesión y el Origin indicados; registra la
 * respuesta HTTP completa con las cookies como <redactado>, y no abre STOMP.
 */
function handshakeOnly(cookie, origin) {
  const url = `${API}/ws`;
  note(`handshake        GET ${url} con Origin ${origin} y la cookie de la sesión`);
  return new Promise((resolve, reject) => {
    const req = httpsRequest(url, {
      headers: {
        Connection: "Upgrade",
        Upgrade: "websocket",
        "Sec-WebSocket-Version": "13",
        "Sec-WebSocket-Key": randomBytes(16).toString("base64"),
        Origin: origin,
        Cookie: cookie,
      },
    });
    const record = (res, body = "") => {
      const lines = [`HTTP/${res.httpVersion} ${res.statusCode} ${res.statusMessage}`];
      for (let i = 0; i < res.rawHeaders.length; i += 2) {
        const name = res.rawHeaders[i];
        const value = /^set-cookie$/i.test(name) ? res.rawHeaders[i + 1].replace(/=[^;]*/, "=<redactado>") : res.rawHeaders[i + 1];
        lines.push(`${name}: ${value}`);
      }
      note(`respuesta        HTTP ${res.statusCode} ${res.statusMessage}`);
      log.push("", ...lines, ...(body ? ["", body] : []));
      resolve();
    };
    req.on("upgrade", (res, socket) => {
      record(res);
      socket.destroy();
    });
    req.on("response", (res) => {
      let body = "";
      res.setEncoding("utf8");
      res.on("data", (chunk) => (body += chunk));
      res.on("end", () => record(res, body));
    });
    req.on("error", reject);
    req.end();
  });
}

const cookie = await login();
const session = await me(cookie);

if (opts["handshake-origen"]) {
  await handshakeOnly(cookie, opts["handshake-origen"]);
  await save();
  process.exit(0);
}

const deadline = Date.now() + Number(opts.minutos) * 60_000;

await new Promise((resolve) => {
  note(`ws abriendo      ${WS_URL}`);
  const ws = new WebSocket(WS_URL, { headers: { Cookie: cookie } });
  let keepAlive;
  let timer;

  ws.onopen = () => {
    note("ws abierto       handshake aceptado (101 Switching Protocols)");
    ws.send(frame("CONNECT", { "accept-version": "1.2", host: new URL(API).host, "heart-beat": "0,0" }));
    // Azure corta conexiones inactivas; un salto de línea es un latido válido en STOMP.
    keepAlive = setInterval(() => ws.readyState === WebSocket.OPEN && ws.send("\n"), 20_000);
    timer = setTimeout(() => {
      note(`fin              se cumplieron ${opts.minutos} min; el observador cierra la conexión`);
      ws.send(frame("DISCONNECT"));
      ws.close(1000, "Fin de la observacion");
    }, deadline - Date.now());
  };

  ws.onmessage = (event) => {
    for (const f of parseFrames(event.data)) {
      if (f.command === "CONNECTED") {
        note(`stomp CONNECTED  versión ${f.headers.version ?? "?"}`);
        if (opts["suscribir-cocina"]) {
          const destination = `/topic/tenant.${session.tenantId}.kitchen`;
          ws.send(frame("SUBSCRIBE", { id: "sub-0", destination }));
          note(`stomp SUBSCRIBE  ${destination}`);
        }
      } else if (f.command === "MESSAGE") {
        note(`stomp MESSAGE    ${f.headers.destination} ${f.body.replace(/\s+/g, " ").slice(0, 400)}`);
      } else {
        note(`stomp ${f.command.padEnd(10)} ${f.headers.message ?? ""} ${f.body.replace(/\s+/g, " ").slice(0, 200)}`);
      }
    }
  };

  ws.onerror = () => note("ws error         el handshake o la conexión fallaron (ver el cierre)");

  ws.onclose = (event) => {
    clearInterval(keepAlive);
    clearTimeout(timer);
    note(`ws cerrado       código ${event.code}${event.reason ? `, motivo «${event.reason}»` : ""}`);
    resolve();
  };
});

await save();
