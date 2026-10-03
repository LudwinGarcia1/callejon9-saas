/**
 * Politica de contrasenas para toda contrasena nueva (registro de restaurante
 * y alta de usuario). Replica `PasswordPolicy` del backend, salvo la lista de
 * contrasenas comunes, que solo vive en el servidor: el servidor sigue siendo
 * la autoridad y esta capa solo da retroalimentacion inmediata.
 *
 * El login no la aplica; ver `login-validation.ts`.
 */
export const PASSWORD_MIN_LENGTH = 10;
/** bcrypt ignora en silencio lo que excede 72 bytes. */
export const PASSWORD_MAX_BYTES = 72;
const MIN_DISTINCT_CHARS = 5;
const MIN_PERSONAL_TERM_LENGTH = 4;

export type PasswordRuleId = "length" | "letterAndDigit" | "variety" | "personalData";

export interface PasswordRuleStatus {
  id: PasswordRuleId;
  label: string;
  met: boolean;
}

const MESSAGES = {
  length: `La contraseña debe tener al menos ${PASSWORD_MIN_LENGTH} caracteres.`,
  tooLong: `La contraseña no puede exceder ${PASSWORD_MAX_BYTES} caracteres (las letras acentuadas cuentan doble).`,
  letterAndDigit: "La contraseña debe combinar letras y números.",
  variety: "La contraseña repite demasiado los mismos caracteres.",
  personalData:
    "La contraseña no debe incluir tu nombre, tu correo ni el identificador del restaurante.",
} as const;

/** Minusculas y sin acentos, igual que `PasswordPolicy.normalize`. */
function normalize(value: string): string {
  return value.normalize("NFD").replace(/\p{M}/gu, "").toLowerCase();
}

function byteLength(value: string): number {
  return new TextEncoder().encode(value).length;
}

function containsPersonalData(password: string, personalData: readonly string[]): boolean {
  const normalizedPassword = normalize(password);
  return personalData.some((value) => {
    let normalizedValue = normalize(value);
    const at = normalizedValue.indexOf("@");
    if (at >= 0) {
      normalizedValue = normalizedValue.slice(0, at);
    }
    return normalizedValue
      .split(/[^\p{L}\p{N}]+/u)
      .some((term) => term.length >= MIN_PERSONAL_TERM_LENGTH && normalizedPassword.includes(term));
  });
}

/** Estado de cada regla visible, para la lista de requisitos del formulario. */
export function passwordRules(
  password: string,
  personalData: readonly string[] = [],
): PasswordRuleStatus[] {
  const codePoints = [...password];
  return [
    {
      id: "length",
      label: `Al menos ${PASSWORD_MIN_LENGTH} caracteres`,
      met: codePoints.length >= PASSWORD_MIN_LENGTH && byteLength(password) <= PASSWORD_MAX_BYTES,
    },
    {
      id: "letterAndDigit",
      label: "Letras y números",
      met: /\p{L}/u.test(password) && /\p{Nd}/u.test(password),
    },
    {
      id: "variety",
      label: "Sin repetir los mismos caracteres",
      met: new Set(codePoints.map((char) => char.toLowerCase())).size >= MIN_DISTINCT_CHARS,
    },
    {
      id: "personalData",
      label: "Sin tu nombre, correo ni identificador",
      met: password.length > 0 && !containsPersonalData(password, personalData),
    },
  ];
}

/**
 * Mensaje de la primera regla incumplida, en el mismo orden que el backend, o
 * `null` si la contrasena pasa las reglas locales.
 */
export function validateNewPassword(
  password: string,
  personalData: readonly string[] = [],
): string | null {
  if (!password) {
    return "Ingresa una contraseña.";
  }
  if ([...password].length < PASSWORD_MIN_LENGTH) {
    return MESSAGES.length;
  }
  if (byteLength(password) > PASSWORD_MAX_BYTES) {
    return MESSAGES.tooLong;
  }
  if (!/\p{L}/u.test(password) || !/\p{Nd}/u.test(password)) {
    return MESSAGES.letterAndDigit;
  }
  if (new Set([...password].map((char) => char.toLowerCase())).size < MIN_DISTINCT_CHARS) {
    return MESSAGES.variety;
  }
  if (containsPersonalData(password, personalData)) {
    return MESSAGES.personalData;
  }
  return null;
}
