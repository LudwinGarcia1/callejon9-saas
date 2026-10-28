// Genera los dos entregables de pruebas a partir de la evidencia de docs/pruebas/:
//
//   1. «Informe de pruebas»: estrategia, estado del plan, resultados resumidos,
//      defectos y métricas. Se lee de corrido.
//   2. «Anexos de evidencia»: la evidencia completa de cada issue, con todas sus
//      capturas y respuestas HTTP. Se consulta por código de anexo (p. ej. SIT-138).
//
// Uso, desde esta carpeta (pnpm install la primera vez):
//   node generar.mjs --salida <carpeta> [--docs ../../docs/pruebas] [--estado estado.json]
//                    [--version 1.0] [--repo https://github.com/<org>/<repo>]
//
// --estado es opcional: un JSON exportado de Linear con el estado de cada issue
// del plan ({ fuente, fecha, issues: [{ id, title, status, assignee }] }). Sin él,
// el informe solo lista las pruebas que ya tienen evidencia.

import { readdir, readFile, writeFile, mkdir, stat } from "node:fs/promises";
import { existsSync } from "node:fs";
import { join, resolve, dirname, basename } from "node:path";
import { fileURLToPath } from "node:url";
import { lexer } from "marked";
import QRCode from "qrcode";
import {
  Document, Packer, Paragraph, TextRun, ImageRun, Table, TableRow, TableCell, WidthType, AlignmentType,
  HeadingLevel, BorderStyle, ShadingType, PageBreak, Header, Footer, PageNumber, TableOfContents,
  LevelFormat, VerticalAlign, TabStopType, ExternalHyperlink,
} from "docx";

// ---------------------------------------------------------------- argumentos
const here = dirname(fileURLToPath(import.meta.url));
const args = Object.fromEntries(process.argv.slice(2).reduce((acc, a, i, all) =>
  (a.startsWith("--") ? [...acc, [a.slice(2), all[i + 1]]] : acc), []));
const DOCS = resolve(args.docs ?? join(here, "..", "..", "docs", "pruebas"));
const OUT = resolve(args.salida ?? join(here, "salida"));
const VERSION = args.version ?? "1.0";
const REPO = args.repo ?? "https://github.com/LudwinGarcia1/callejon9-saas";
const LOGO = join(here, "..", "..", "frontend", "public", "brand", "restohub-logo-320.png");
const ESTADO = args.estado ? JSON.parse(await readFile(resolve(args.estado), "utf8")) : null;
const HOY = new Intl.DateTimeFormat("sv-SE", { timeZone: "America/Mexico_City" }).format(new Date());

// ---------------------------------------------------------------- estilo
const INK = "2B2622", MUTED = "6B635B", ACCENT = "B4472E", LINE = "D9D2C7", FILL = "F3EEE6", OK = "2E7D4F", BAD = "B42318";
const FONT = "Calibri";
const W = 9360; // ancho útil: carta con márgenes de 1"
const FASES = { ut: "UT", sit: "SIT", uat: "UAT" };
const FASE_NOMBRE = { UT: "Pruebas unitarias (UT/UIT)", SIT: "Integración de sistemas (SIT)", UAT: "Aceptación de usuario (UAT)" };

// ---------------------------------------------------------------- utilidades de documento
const t = (text, o = {}) => new TextRun({ text, font: o.font ?? FONT, ...o });
const p = (children, o = {}) => new Paragraph({ spacing: { after: 120, line: 276 }, ...o,
  children: Array.isArray(children) ? children : [t(children)] });
const heading = (level, text, newPage = false) => new Paragraph({ heading: level, pageBreakBefore: newPage, children: [t(text)] });
const h1 = (x, np) => heading(HeadingLevel.HEADING_1, x, np);
const h2 = (x, np) => heading(HeadingLevel.HEADING_2, x, np);
const h3 = (x, np) => heading(HeadingLevel.HEADING_3, x, np);
const bullet = (children, ref = "bullets") => new Paragraph({ numbering: { reference: ref, level: 0 }, spacing: { after: 60 },
  children: Array.isArray(children) ? children : [t(children)] });
const border = { style: BorderStyle.SINGLE, size: 4, color: LINE };
const borders = { top: border, bottom: border, left: border, right: border };
const none = { style: BorderStyle.NONE, size: 0, color: "FFFFFF" };
const noBorders = { top: none, bottom: none, left: none, right: none };

function cell(content, width, o = {}) {
  const list = Array.isArray(content) ? content : [content];
  const isRun = (x) => x != null && typeof x === "object" && !(x instanceof Paragraph);
  const paras = list.length && list.every(isRun)
    ? [new Paragraph({ spacing: { after: 40 }, children: list })]
    : list.map((c) => c instanceof Paragraph ? c : new Paragraph({ spacing: { after: 40 },
        children: [t(String(c ?? ""), { size: o.size ?? 19, bold: o.bold, color: o.color })] }));
  return new TableCell({ width: { size: width, type: WidthType.DXA }, borders: o.borders ?? borders,
    shading: o.fill ? { fill: o.fill, type: ShadingType.CLEAR, color: "auto" } : undefined,
    margins: { top: 70, bottom: 70, left: 110, right: 110 }, verticalAlign: o.vAlign ?? VerticalAlign.TOP, children: paras });
}

function table(widths, header, rows, o = {}) {
  const trs = [];
  if (header) trs.push(new TableRow({ tableHeader: true, children: header.map((h, i) => cell(h, widths[i], { bold: true, fill: FILL, size: o.size ?? 19 })) }));
  for (const r of rows) trs.push(new TableRow({ children: r.map((c, i) => cell(c, widths[i], { size: o.size ?? 19, bold: o.firstBold && i === 0 })) }));
  return new Table({ width: { size: widths.reduce((a, b) => a + b, 0), type: WidthType.DXA }, columnWidths: widths, rows: trs });
}

/** Reparte el ancho útil en proporción a la longitud del contenido de cada columna. */
function autoWidths(header, rows) {
  const n = header.length;
  const weight = Array.from({ length: n }, (_, i) => {
    const lens = [header[i], ...rows.map((r) => r[i] ?? "")].map((s) => Math.min(String(s).length, 140));
    return Math.max(6, lens.reduce((a, b) => a + b, 0) / lens.length);
  });
  const total = weight.reduce((a, b) => a + b, 0);
  const widths = weight.map((w) => Math.max(700, Math.floor((W * w) / total)));
  const sum = widths.reduce((a, b) => a + b, 0);
  widths[widths.indexOf(Math.max(...widths))] += W - sum;
  return widths;
}

const mono = (line) => new Paragraph({ spacing: { after: 0, line: 240 }, shading: { fill: "F6F4F0", type: ShadingType.CLEAR, color: "auto" },
  children: [t(line || " ", { font: "Consolas", size: 15 })] });

const isPng = (buf) => buf.readUInt32BE(0) === 0x89504e47;

/** Ancho y alto de un PNG o de un JPEG (una captura reenviada por mensajería puede ser JPEG aunque diga .png). */
function pngSize(buf) {
  if (isPng(buf)) return { w: buf.readUInt32BE(16), h: buf.readUInt32BE(20) };
  for (let i = 2; i < buf.length - 9;) {
    if (buf[i] !== 0xff) { i += 1; continue; }
    const marker = buf[i + 1];
    if (marker >= 0xc0 && marker <= 0xcf && ![0xc4, 0xc8, 0xcc].includes(marker)) return { w: buf.readUInt16BE(i + 7), h: buf.readUInt16BE(i + 5) };
    i += 2 + buf.readUInt16BE(i + 2);
  }
  throw new Error("Formato de imagen no reconocido");
}
function imageRun(buf, widthPx, name) {
  const { w, h } = pngSize(buf);
  const maxH = 820; // que una captura alta del teléfono quepa en la página
  let width = widthPx, height = Math.round((widthPx * h) / w);
  if (height > maxH) { height = maxH; width = Math.round((maxH * w) / h); }
  return new ImageRun({ type: isPng(buf) ? "png" : "jpg", data: buf, transformation: { width, height }, altText: { title: name, description: name, name } });
}

function numbering() {
  return { config: [
    { reference: "bullets", levels: [{ level: 0, format: LevelFormat.BULLET, text: "•", alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 540, hanging: 270 } } } }] },
    { reference: "checks", levels: [{ level: 0, format: LevelFormat.BULLET, text: "☐", alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 540, hanging: 300 } } } }] },
    { reference: "checked", levels: [{ level: 0, format: LevelFormat.BULLET, text: "☑", alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 540, hanging: 300 } } } }] },
  ] };
}

const styles = {
  default: { document: { run: { font: FONT, size: 21, color: INK } } },
  paragraphStyles: [
    { id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true, run: { size: 32, bold: true, font: FONT, color: INK },
      paragraph: { spacing: { before: 360, after: 160 }, outlineLevel: 0, keepNext: true, border: { bottom: { style: BorderStyle.SINGLE, size: 6, color: ACCENT, space: 4 } } } },
    { id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true, run: { size: 26, bold: true, font: FONT, color: ACCENT },
      paragraph: { spacing: { before: 280, after: 120 }, outlineLevel: 1, keepNext: true } },
    { id: "Heading3", name: "Heading 3", basedOn: "Normal", next: "Normal", quickFormat: true, run: { size: 22, bold: true, font: FONT, color: INK },
      paragraph: { spacing: { before: 200, after: 80 }, outlineLevel: 2, keepNext: true } },
  ],
};

function section(children, headerRight) {
  return {
    properties: { page: { size: { width: 12240, height: 15840 }, margin: { top: 1440, right: 1440, bottom: 1440, left: 1440 } }, titlePage: true },
    headers: {
      first: new Header({ children: [] }),
      default: new Header({ children: [new Paragraph({ border: { bottom: { style: BorderStyle.SINGLE, size: 4, color: LINE, space: 4 } },
        tabStops: [{ type: TabStopType.RIGHT, position: W }],
        children: [t("RestoHub · Callejón 9 SaaS", { size: 17, color: MUTED }), t(`\t${headerRight}`, { size: 17, color: MUTED })] })] }),
    },
    footers: {
      first: new Footer({ children: [] }),
      default: new Footer({ children: [new Paragraph({ alignment: AlignmentType.RIGHT, children: [
        t("Página ", { size: 17, color: MUTED }), new TextRun({ children: [PageNumber.CURRENT], font: FONT, size: 17, color: MUTED }),
        t(" de ", { size: 17, color: MUTED }), new TextRun({ children: [PageNumber.TOTAL_PAGES], font: FONT, size: 17, color: MUTED })] })] }),
    },
    children,
  };
}

async function cover(title, subtitle, rows) {
  const logo = existsSync(LOGO) ? await readFile(LOGO) : null;
  return [
    new Paragraph({ spacing: { before: 1200, after: 360 }, children: logo ? [imageRun(logo, 96, "RestoHub")] : [] }),
    new Paragraph({ spacing: { after: 80 }, children: [t("RESTOHUB · CALLEJÓN 9 SAAS", { size: 20, bold: true, color: ACCENT, characterSpacing: 40 })] }),
    new Paragraph({ spacing: { after: 240 }, border: { bottom: { style: BorderStyle.SINGLE, size: 12, color: ACCENT, space: 8 } },
      children: [t(title, { size: 52, bold: true, color: INK })] }),
    new Paragraph({ spacing: { after: 1200 }, children: [t(subtitle, { size: 26, color: MUTED })] }),
    table([2600, W - 2600], null, rows, { firstBold: true }),
    new Paragraph({ children: [new PageBreak()] }),
  ];
}

function tocBlock() {
  return [
    new Paragraph({ pageBreakBefore: true, spacing: { after: 160 }, children: [t("Contenido", { size: 30, bold: true, color: INK })] }),
    new TableOfContents("Contenido", { hyperlink: true, headingStyleRange: "1-2" }),
  ];
}

// ---------------------------------------------------------------- lectura de la evidencia
const plain = (s) => String(s ?? "").replace(/\*\*|__|`/g, "").replace(/\[([^\]]+)\]\([^)]*\)/g, "$1").replace(/<[^>]+>/g, "").trim();

async function loadEvidence() {
  const out = [];
  for (const [dir, fase] of Object.entries(FASES)) {
    const base = join(DOCS, dir);
    if (!existsSync(base)) continue;
    for (const name of (await readdir(base)).sort()) {
      const folder = join(base, name);
      if (!(await stat(folder)).isDirectory() || !existsSync(join(folder, "README.md"))) continue;
      const md = await readFile(join(folder, "README.md"), "utf8");
      const tokens = lexer(md);
      const issue = (name.match(/CAL-\d+/) ?? [name])[0];
      out.push({ fase, issue, code: `${fase}-${issue.replace("CAL-", "")}`, folder, rel: `docs/pruebas/${dir}/${name}`, tokens, ...summarize(tokens) });
    }
  }
  return out;
}

/** Extrae del README lo que el informe necesita, sin depender de la redacción libre. */
function summarize(tokens) {
  const titleTok = tokens.find((x) => x.type === "heading" && x.depth === 1);
  const title = plain(titleTok?.text ?? "").replace(/^Evidencia de prueba · /, "").replace(/^CAL-\d+\s+—\s+/, "").replace(/^(UT|SIT|UAT) · /, "");
  const sectionOf = (prefix) => {
    const i = tokens.findIndex((x) => x.type === "heading" && x.depth === 2 && plain(x.text).startsWith(prefix));
    if (i < 0) return [];
    const j = tokens.findIndex((x, k) => k > i && x.type === "heading" && x.depth <= 2);
    return tokens.slice(i + 1, j < 0 ? undefined : j);
  };
  const kvFrom = (toks) => Object.fromEntries((toks.find((x) => x.type === "table")?.rows ?? []).map((r) => [plain(r[0].text), plain(r[1].text)]));
  const ident = kvFrom(sectionOf("1."));
  const cases = [];
  const casesSec = sectionOf("6.");
  casesSec.forEach((x, i) => {
    if (x.type !== "heading" || x.depth !== 3 || !/^C\d+/.test(plain(x.text))) return;
    const next = casesSec.slice(i + 1);
    const end = next.findIndex((y) => y.type === "heading" && y.depth <= 3);
    const body = end < 0 ? next : next.slice(0, end);
    const kv = kvFrom(body);
    const [id, ...rest] = plain(x.text).split(/\s+—\s+|\s+·\s+/);
    cases.push({ id, title: rest.join(" — "), criterio: kv["Criterio cubierto"] ?? "", ejecucion: kv["Inicio / fin de ejecución"] ?? "",
      estado: kv["Estado"] ?? "", defecto: kv["Defecto vinculado"] ?? "—", images: imagesIn(body) });
  });
  const defects = (sectionOf("7.").find((x) => x.type === "table")?.rows ?? [])
    .map((r) => r.map((c) => plain(c.text))).filter((r) => r[0] && r[0] !== "—");
  const resumen = sectionOf("8.");
  const counts = resumen.find((x) => x.type === "table")?.rows?.[0]?.map((c) => plain(c.text)) ?? [];
  const concl = resumen.find((x) => x.type === "paragraph" && plain(x.text).startsWith("Conclusión del ejecutor"));
  return { title, ident, cases, defects, counts, conclusion: plain(concl?.text ?? "").replace(/^Conclusión del ejecutor\.\s*/, "") };
}

function imagesIn(tokens) {
  const found = [];
  const walk = (s) => { for (const m of String(s).matchAll(/\]\((capturas\/[^)]+\.png)\)/g)) if (!found.includes(m[1])) found.push(m[1]); };
  for (const x of tokens) walk(x.raw ?? "");
  return found;
}

// ---------------------------------------------------------------- markdown → docx (anexos)
/** Convierte tokens inline de marked en runs, resolviendo los enlaces a capturas y respuestas. */
function inline(tokens, ctx, o = {}) {
  const runs = [];
  for (const tk of tokens ?? []) {
    const style = { size: o.size, bold: o.bold, italics: o.italics, color: o.color };
    switch (tk.type) {
      case "text": case "escape":
        if (tk.tokens) runs.push(...inline(tk.tokens, ctx, o)); else runs.push(t(decode(tk.text), style)); break;
      case "strong": runs.push(...inline(tk.tokens, ctx, { ...o, bold: true })); break;
      case "em": runs.push(...inline(tk.tokens, ctx, { ...o, italics: true })); break;
      case "codespan": runs.push(t(decode(tk.text), { ...style, font: "Consolas", size: (o.size ?? 21) - 2 })); break;
      case "br": runs.push(new TextRun({ break: 1 })); break;
      case "del": runs.push(...inline(tk.tokens, ctx, o)); break;
      case "link": {
        const label = plain(tk.text);
        if (/^capturas\//.test(tk.href) && ctx.figures.has(tk.href)) {
          runs.push(t(`Figura ${ctx.figures.get(tk.href)}`, { ...style, bold: true, color: ACCENT }));
        } else if (/^respuestas\//.test(tk.href)) {
          runs.push(t(`${label} (${ctx.code}, respuesta ${ctx.responseIndex(tk.href)})`, { ...style, color: ACCENT }));
        } else if (/^https?:/.test(tk.href)) {
          runs.push(new ExternalHyperlink({ link: tk.href, children: [t(label, { ...style, color: "1F5FAD", underline: {} })] }));
        } else {
          runs.push(t(label, style));
        }
        break;
      }
      case "html": break;
      default: if (tk.text) runs.push(t(decode(tk.text), style));
    }
  }
  return runs;
}
const decode = (s) => String(s).replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/&amp;/g, "&").replace(/&lt;/g, "<").replace(/&gt;/g, ">");

async function figureBlock(rel, ctx) {
  const buf = await readFile(join(ctx.folder, rel));
  const { w, h } = pngSize(buf);
  const widthPx = w > h ? 600 : 260;
  const n = ctx.figures.get(rel);
  const label = basename(rel, ".png");
  return [
    new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 120 }, keepNext: true, children: [imageRun(buf, widthPx, label)] }),
    new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 60, after: 200 },
      children: [t(`Figura ${n}. `, { bold: true, size: 17, color: MUTED }), t(label, { italics: true, size: 17, color: MUTED })] }),
  ];
}

async function phoneRow(rels, ctx) {
  const colW = Math.floor(W / 3);
  const widths = rels.map(() => colW);
  const cells = [];
  for (const rel of rels) {
    const buf = await readFile(join(ctx.folder, rel));
    const label = basename(rel, ".png");
    cells.push(new TableCell({ width: { size: colW, type: WidthType.DXA }, borders: noBorders, margins: { top: 40, bottom: 40, left: 60, right: 60 },
      children: [new Paragraph({ alignment: AlignmentType.CENTER, children: [imageRun(buf, 176, label)] }),
        new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 60, after: 160 },
          children: [t(`Figura ${ctx.figures.get(rel)}. `, { bold: true, size: 16, color: MUTED }), t(label, { italics: true, size: 16, color: MUTED })] })] }));
  }
  return new Table({ width: { size: colW * rels.length, type: WidthType.DXA }, columnWidths: widths, alignment: AlignmentType.CENTER,
    rows: [new TableRow({ cantSplit: true, children: cells })] });
}

async function annexFromEvidence(ev, figureStart) {
  const ctx = { folder: ev.folder, code: ev.code, figures: new Map(), responses: [] };
  // numera las figuras en el orden en que el README las cita por primera vez
  let n = figureStart;
  for (const m of ev.tokens.map((x) => x.raw).join("\n").matchAll(/\]\((capturas\/[^)]+\.png)\)/g)) {
    if (!ctx.figures.has(m[1]) && existsSync(join(ev.folder, m[1]))) ctx.figures.set(m[1], n++);
  }
  const respDir = join(ev.folder, "respuestas");
  ctx.responses = existsSync(respDir) ? (await readdir(respDir)).filter((f) => /\.(txt|json)$/.test(f)).sort() : [];
  ctx.responseIndex = (href) => `R${ctx.responses.indexOf(basename(href)) + 1}`;

  const out = [h1(`Anexo ${ev.code} · ${ev.title}`, true),
    p([t("Evidencia completa de ", { color: MUTED }), t(ev.issue, { bold: true }), t(", tomada de ", { color: MUTED }), t(ev.rel + "/README.md", { font: "Consolas", size: 18 }), t(".", { color: MUTED })])];
  const pending = [];
  // Las capturas horizontales (web) van solas a lo ancho; las verticales (teléfono)
  // se agrupan de tres en tres en una fila para no gastar una página por captura.
  const flush = async () => {
    let row = [];
    const flushRow = async () => { if (row.length) out.push(await phoneRow(row, ctx)); row = []; };
    for (const rel of pending.splice(0)) {
      const { w, h } = pngSize(await readFile(join(ctx.folder, rel)));
      if (w > h) { await flushRow(); out.push(...await figureBlock(rel, ctx)); }
      else { row.push(rel); if (row.length === 3) await flushRow(); }
    }
    await flushRow();
  };
  const placed = new Set();

  for (const tk of ev.tokens) {
    if (tk.type === "heading" && tk.depth <= 3) await flush();
    switch (tk.type) {
      case "heading":
        if (tk.depth === 1) break; // el título del anexo ya lo dice
        out.push(tk.depth === 2 ? h2(plain(tk.text)) : h3(plain(tk.text)));
        break;
      case "paragraph": out.push(p(inline(tk.tokens, ctx))); break;
      case "blockquote":
        for (const b of tk.tokens) if (b.type === "paragraph")
          out.push(p(inline(b.tokens, ctx, { italics: true, color: MUTED }), { indent: { left: 360 }, border: { left: { style: BorderStyle.SINGLE, size: 12, color: LINE, space: 8 } } }));
        break;
      case "list":
        for (const item of tk.items) {
          const toks = item.tokens.flatMap((x) => (x.type === "text" || x.type === "paragraph") ? (x.tokens ?? [x]) : []);
          out.push(bullet(inline(toks, ctx), item.task ? (item.checked ? "checked" : "checks") : "bullets"));
        }
        break;
      case "table": {
        const header = tk.header.map((c) => plain(c.text));
        const rows = tk.rows.map((r) => r.map((c) => plain(c.text)));
        const widths = autoWidths(header, rows);
        const size = header.length >= 5 ? 16 : 18;
        out.push(table(widths, header.map((h) => [t(h, { bold: true, size })]),
          tk.rows.map((r) => r.map((c) => [new Paragraph({ spacing: { after: 40 }, children: inline(c.tokens, ctx, { size }) })])), { size }));
        out.push(p(""));
        break;
      }
      case "code": out.push(...decode(tk.text).split("\n").map(mono)); out.push(p("")); break;
      default: break;
    }
    for (const rel of imagesIn([tk])) if (ctx.figures.has(rel) && !placed.has(rel)) { placed.add(rel); pending.push(rel); }
  }
  await flush();

  if (ctx.responses.length) {
    out.push(h2(`Respuestas HTTP literales de ${ev.code}`, true));
    for (const [i, f] of ctx.responses.entries()) {
      out.push(h3(`R${i + 1} · ${f}`));
      const text = (await readFile(join(respDir, f), "utf8")).replace(/\r/g, "").trimEnd();
      out.push(...text.split("\n").map(mono), p(""));
    }
  }
  return { children: out, next: n };
}

// ---------------------------------------------------------------- informe
const isOk = (s) => /aprobado/i.test(s);
const statusRun = (s) => t(plain(s).replace(/[✅❌⛔⏭️]/gu, "").trim() || "—", { bold: true, size: 18, color: isOk(s) ? OK : /fallido/i.test(s) ? BAD : MUTED });
const ESTADO_ES = { "Todo": "Por hacer", "In Progress": "En curso", "In review": "En revisión", "For Test": "Por probar", "Done": "Terminado", "Backlog": "Pendiente", "Canceled": "Cancelado" };

async function qrFor(url) { return imageRun(await QRCode.toBuffer(url, { margin: 1, width: 220 }), 84, "QR"); }

async function buildReport(evidence) {
  const plan = ESTADO?.issues ?? evidence.map((e) => ({ id: e.issue, title: `${e.fase} · ${e.title.split(" · ").pop()}`, status: "In review", assignee: e.ident["Ejecutor de la prueba"] ?? "" }));
  const byIssue = new Map(evidence.map((e) => [e.issue, e]));
  const fase = (x) => (x.title.match(/^(UT|SIT|UAT)\b/) ?? [, "Otro"])[1];
  const allCases = evidence.flatMap((e) => e.cases.map((c) => ({ ...c, ev: e })));
  const allDefects = evidence.flatMap((e) => e.defects.map((d) => ({ d, ev: e })));
  const aprob = allCases.filter((c) => isOk(c.estado)).length;

  const children = [...await cover("Informe de pruebas",
    "Plan de pruebas por fases (UT, SIT y UAT): estado, resultados, defectos y métricas", [
      ["Proyecto", "Callejón 9 SaaS — Ruta a producción y entrega académica"],
      ["Alcance", `${plan.length} issues de prueba del plan · ${evidence.length} con evidencia ejecutada`],
      ["Resultados", `${aprob} de ${allCases.length} casos aprobados · ${allDefects.length} defecto(s) registrado(s)`],
      ["Corte de información", ESTADO ? `${ESTADO.fecha} (${ESTADO.fuente})` : HOY],
      ["Documento complementario", "Anexos de evidencia (archivo aparte)"],
      ["Versión del documento", `${VERSION} · ${HOY}`],
    ])];

  children.push(new Paragraph({ spacing: { after: 160 }, children: [t("Control del documento", { size: 30, bold: true, color: INK })] }),
    table([1300, 1900, 6160], ["Versión", "Fecha", "Descripción"], [[VERSION, HOY, "Informe generado desde la evidencia versionada en docs/pruebas/."]]),
    p(""),
    p([t("Cómo leer este informe. ", { bold: true }), t("El informe resume; la evidencia completa —todas las capturas, pasos y respuestas HTTP literales— está en el archivo «Anexos de evidencia». Cada prueba ejecutada tiene un código de anexo (por ejemplo SIT-138) y un código QR que lleva a su carpeta en el repositorio.")]),
    p([t("Fuente única. ", { bold: true }), t("Ambos archivos se generan con scripts/informe-pruebas a partir de los README de docs/pruebas/; ningún dato se redacta a mano en estos documentos.")]),
    ...tocBlock());

  // 1. Introducción
  children.push(h1("1. Introducción", true),
    p("Este informe presenta el estado y los resultados del plan de pruebas de RestoHub, el SaaS multi-restaurante de Callejón 9. Cubre las tres fases del plan —pruebas unitarias, integración de sistemas sobre Azure y aceptación de usuario— y consolida los defectos encontrados."),
    p("La garantía central del sistema es el aislamiento entre restaurantes, que PostgreSQL impone con Row Level Security; por eso varias pruebas verifican reglas de seguridad además de la funcionalidad."));

  // 2. Estrategia
  children.push(h1("2. Estrategia de pruebas"),
    table([1400, 3800, 4160], ["Fase", "Qué demuestra", "Qué confirma la revisión"], [
      ["UT / UIT", "Que cada regla de negocio tiene casos válidos, inválidos y de borde en pruebas automatizadas", "Que las pruebas fallan sin el comportamiento que protegen y que la puerta del módulo pasa en CI"],
      ["SIT", "Que los sistemas desplegados en Azure funcionan juntos: frontend, API, PostgreSQL, Key Vault, tiempo real y app móvil", "Que la evidencia corresponde a una ejecución real, con fecha, commit y resultado de cada caso"],
      ["UAT", "Que cada rol resuelve su trabajo en un turno real con el sistema desplegado", "Que el registro de aceptación está completo por rol y caso y que las observaciones tienen seguimiento"],
    ]),
    p(""),
    h2("2.1 Entornos"),
    table([2600, W - 2600], null, [
      ["Frontend", "https://restohub.azurewebsites.net (Azure App Service)"],
      ["API", "https://app-callejon9-api-prod-3878c6.azurewebsites.net (Azure App Service, Java 21)"],
      ["Base de datos", "Azure Database for PostgreSQL con Row Level Security"],
      ["Datos de prueba", "Restaurante «pruebas» con una cuenta por rol; sin datos reales"],
    ], { firstBold: true }),
    h2("2.2 Reglas de evidencia"),
    bullet("Solo cuenta una ejecución real: capturas con URL y hora visibles, y respuestas HTTP copiadas sin editar."),
    bullet("El commit probado en SIT y UAT sale del registro «Despliegue» de Linear."),
    bullet("Sin contraseñas, tokens, cookies ni datos personales."),
    bullet("Quien revisa no es quien ejecutó; un issue pasa a terminado solo con revisión aprobada."));

  // 3. Estado del plan
  children.push(h1("3. Estado del plan", true),
    p(ESTADO ? `Estado de cada issue de prueba según ${ESTADO.fuente}, con corte al ${ESTADO.fecha}. La columna «Anexo» indica las pruebas con evidencia en el formato estándar.` : "Pruebas con evidencia en el formato estándar."));
  for (const F of ["UT", "SIT", "UAT"]) {
    const rows = plan.filter((x) => fase(x) === F);
    if (!rows.length) continue;
    const conEv = rows.filter((x) => byIssue.has(x.id)).length;
    children.push(h2(`3.${["UT", "SIT", "UAT"].indexOf(F) + 1} ${FASE_NOMBRE[F]} · ${rows.length} issues · ${conEv} con evidencia`),
      table([1100, 3800, 2460, 1200, 800], ["Issue", "Módulo", "Responsable", "Estado", "Anexo"],
        rows.map((x) => [x.id, x.title.replace(/^(UT|SIT|UAT) · /, ""), x.assignee ?? "—", ESTADO_ES[x.status] ?? x.status, byIssue.get(x.id)?.code ?? "—"]), { size: 17 }));
  }

  // 4. Resultados
  children.push(h1("4. Resultados de las pruebas ejecutadas", true),
    p("Para cada prueba con evidencia: identificación, resultado por caso, defectos y conclusión del ejecutor. Las figuras son las más representativas; el resto está en el anexo indicado."));
  for (const ev of evidence) {
    const url = `${REPO}/tree/main/${ev.rel}`;
    children.push(h2(`${ev.code} · ${ev.title}`, ev !== evidence[0]),
      new Table({ width: { size: W, type: WidthType.DXA }, columnWidths: [W - 1800, 1800], rows: [new TableRow({ children: [
        cell([
          p([t("Commit probado: ", { bold: true }), t(ev.ident["Commit probado (API)"] ?? ev.ident["Commit probado"] ?? "—")], { spacing: { after: 40 } }),
          p([t("Entorno: ", { bold: true }), t(ev.ident["Entorno"] ?? "—"), t("  ·  Dispositivo: ", { bold: true }), t((ev.ident["Navegador / dispositivo"] ?? "—").split(". ")[0])], { spacing: { after: 40 } }),
          p([t("Evidencia completa: ", { bold: true }), t(`Anexo ${ev.code}`, { color: ACCENT, bold: true }), t(" · "), new ExternalHyperlink({ link: url, children: [t(ev.rel, { color: "1F5FAD", underline: {}, size: 18 })] })], { spacing: { after: 0 } }),
        ], W - 1800, { borders: noBorders }),
        cell([new Paragraph({ alignment: AlignmentType.CENTER, children: [await qrFor(url)] })], 1800, { borders: noBorders }),
      ] })] }),
      p(""),
      table([800, 4500, 1700, 1100, 1260], ["Caso", "Qué verifica", "Ejecución", "Resultado", "Defecto"],
        ev.cases.map((c) => [c.id, c.title, c.ejecucion, [new Paragraph({ children: [statusRun(c.estado)] })], c.defecto.split(" (")[0]]), { size: 17 }),
      p(""));
    if (ev.defects.length) children.push(p([t("Defectos: ", { bold: true }), t(ev.defects.map((d) => `${d[0]} (${d[2]}) — ${d[3]}`).join("; "))]));
    if (ev.conclusion) children.push(p([t("Conclusión del ejecutor. ", { bold: true }), t(ev.conclusion)]));
    // hasta dos figuras representativas: la última captura de cada uno de los dos primeros casos
    const reps = ev.cases.slice(0, 2).map((c) => c.images.at(-1)).filter(Boolean);
    for (const rel of reps) {
      const buf = await readFile(join(ev.folder, rel));
      const { w, h } = pngSize(buf);
      children.push(new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 120 }, keepNext: true, children: [imageRun(buf, w > h ? 520 : 200, rel)] }),
        new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 160 }, children: [t(`${ev.code} · ${basename(rel, ".png")} (ver Anexo ${ev.code})`, { italics: true, size: 17, color: MUTED })] }));
    }
  }

  // 5. Defectos
  children.push(h1("5. Defectos", true));
  children.push(allDefects.length
    ? table([1200, 1300, 900, 1100, 3460, 1400], ["ID", "Prueba", "Caso", "Severidad", "Descripción", "Estado"],
        allDefects.map(({ d, ev }) => [d[0], ev.code, d[1], d[2], d[3], d[4]]), { size: 17 })
    : p("No se registraron defectos en las pruebas ejecutadas."));

  // 6. Métricas
  const sev = ["Crítica", "Alta", "Media", "Baja"].map((s) => allDefects.filter(({ d }) => d[2] === s).length);
  const count = (re) => allCases.filter((c) => re.test(c.estado)).length;
  children.push(h1("6. Métricas"),
    h2("6.1 Casos ejecutados"),
    table([1872, 1872, 1872, 1872, 1872], ["Total", "Aprobados", "Fallidos", "Bloqueados", "No ejecutados"],
      [[String(allCases.length), String(aprob), String(count(/fallido/i)), String(count(/bloqueado/i)), String(count(/no ejecutado/i))]]),
    h2("6.2 Defectos por severidad"),
    table([2340, 2340, 2340, 2340], ["Crítica", "Alta", "Media", "Baja"], [sev.map(String)]),
    h2("6.3 Avance del plan"),
    table([2340, 2340, 2340, 2340], ["Fase", "Issues", "Con evidencia estándar", "Terminados en Linear"],
      ["UT", "SIT", "UAT"].map((F) => { const r = plan.filter((x) => fase(x) === F);
        return [F, String(r.length), String(r.filter((x) => byIssue.has(x.id)).length), String(r.filter((x) => x.status === "Done").length)]; })));

  // 7. Conclusiones
  const pend = plan.filter((x) => !byIssue.has(x.id) && x.status !== "Done");
  children.push(h1("7. Conclusiones", true),
    p(`Con corte al ${ESTADO?.fecha ?? HOY}, ${evidence.length} de ${plan.length} issues del plan tienen evidencia ejecutada en el formato estándar, con ${aprob} de ${allCases.length} casos aprobados y ${allDefects.length} defecto(s) registrado(s).`),
    p(`Quedan ${pend.length} issues de prueba sin evidencia estándar y sin terminar. La UAT depende de que la SIT del módulo esté aprobada, así que el orden recomendado es cerrar primero la SIT de cada módulo.`),
    h1("8. Aprobación"),
    table([3120, 3120, 3120], ["Rol", "Nombre y firma", "Fecha"], [["Elaboró", "", ""], ["Revisó", "", ""], ["Aprobó", "", ""]]));

  return new Document({ creator: "Callejón 9", title: "Informe de pruebas · RestoHub", features: { updateFields: true }, styles, numbering: numbering(),
    sections: [section(children, "Informe de pruebas")] });
}

async function buildAnnexes(evidence) {
  const children = [...await cover("Anexos de evidencia",
    "Evidencia completa de cada prueba ejecutada: pasos, capturas y respuestas HTTP literales", [
      ["Proyecto", "Callejón 9 SaaS — Ruta a producción y entrega académica"],
      ["Anexos", evidence.map((e) => e.code).join(", ") || "—"],
      ["Documento principal", "Informe de pruebas (archivo aparte)"],
      ["Versión del documento", `${VERSION} · ${HOY}`],
    ]),
    new Paragraph({ spacing: { after: 160 }, children: [t("Índice de anexos", { size: 30, bold: true, color: INK })] }),
    table([1400, 1300, 6660], ["Anexo", "Issue", "Prueba"], evidence.map((e) => [e.code, e.issue, e.title])),
    p(""),
    p("Cada anexo reproduce la evidencia del repositorio sin resumir. Las figuras se numeran de forma continua en todo el documento, y las respuestas HTTP se identifican por anexo (R1, R2, …) al final de cada uno."),
    ...tocBlock()];
  let fig = 1;
  for (const ev of evidence) {
    const { children: c, next } = await annexFromEvidence(ev, fig);
    children.push(...c);
    fig = next;
  }
  return new Document({ creator: "Callejón 9", title: "Anexos de evidencia · RestoHub", features: { updateFields: true }, styles, numbering: numbering(),
    sections: [section(children, "Anexos de evidencia")] });
}

// ---------------------------------------------------------------- main
const evidence = await loadEvidence();
if (!evidence.length) throw new Error(`No hay evidencia en ${DOCS}`);
await mkdir(OUT, { recursive: true });
const report = join(OUT, "Informe de pruebas.docx");
const annexes = join(OUT, "Anexos de evidencia.docx");
await writeFile(report, await Packer.toBuffer(await buildReport(evidence)));
await writeFile(annexes, await Packer.toBuffer(await buildAnnexes(evidence)));
console.log(`evidencia: ${evidence.map((e) => e.code).join(", ")}`);
console.log(`listo: ${report}`);
console.log(`listo: ${annexes}`);
