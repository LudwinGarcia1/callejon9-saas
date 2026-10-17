/**
 * Paleta tomada de los tokens claros de frontend/src/app/globals.css
 * (convertidos de oklch a hex) para que la app se vea como el SaaS web.
 */
export const colors = {
  background: '#F1EEE6',
  card: '#FBF9F5',
  foreground: '#2B2723',
  mutedForeground: '#776F66',
  muted: '#E9E5DC',
  border: '#E0DBD1',
  primary: '#2B2723',
  primaryForeground: '#F7F4EE',
  destructive: '#C8282E',
  destructiveSoft: '#FBE9E8',
  success: '#2E7D4F',
  successSoft: '#E5F3EA',
  warning: '#9A6A10',
  warningSoft: '#F8EFD9',
};

export const radius = 10;

export function formatMoney(value: number): string {
  return `$${Number(value).toFixed(2)}`;
}
