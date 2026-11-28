/**
 * Interpreta lo que el comensal escanea o pega: el enlace del QR de la mesa
 * (https://…/m/{slug}/{mesa} o restohub://m/{slug}/{mesa}) o solo el código
 * del restaurante.
 */

export interface TableRef {
  slug: string;
  tableNumber: number | null;
}

const SLUG = /^[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?$/;

export function normalizeSlug(value: string): string | null {
  const slug = value.trim().toLowerCase();
  return SLUG.test(slug) ? slug : null;
}

export function parseTableNumber(value: string): number | null {
  const trimmed = value.trim();
  if (!/^\d{1,4}$/.test(trimmed)) {
    return null;
  }
  const number = Number(trimmed);
  return number >= 1 ? number : null;
}

export function parseTableLink(input: string): TableRef | null {
  const text = input.trim();
  const match = text.match(/^(?:https?:\/\/[^/]+\/|restohub:\/\/)m\/([^/?#]+)(?:\/([^/?#]+))?\/?(?:[?#].*)?$/i);
  if (match) {
    const slug = normalizeSlug(decodeURIComponent(match[1]));
    if (!slug) return null;
    const tableNumber = match[2] ? parseTableNumber(match[2]) : null;
    if (match[2] && tableNumber === null) return null;
    return { slug, tableNumber };
  }
  const slug = normalizeSlug(text);
  return slug ? { slug, tableNumber: null } : null;
}
