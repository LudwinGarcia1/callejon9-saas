import type { LoginRequest } from "./types";

/**
 * Reglas del formulario de login. Replican las de `LoginRequest` en el
 * backend: el servidor sigue siendo la autoridad, esta capa solo evita enviar
 * al servicio una peticion que de antemano se sabe invalida y da al usuario
 * un mensaje inmediato.
 *
 * La contrasena no se recorta ni se valida por longitud minima: un espacio
 * puede ser parte de ella, y exigir un minimo en el login revelaria la
 * politica de contrasenas.
 */
const SLUG_PATTERN = /^[a-z0-9-]{3,80}$/;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const EMAIL_MAX_LENGTH = 180;
const PASSWORD_MAX_LENGTH = 100;

export type LoginField = keyof LoginRequest;
export type LoginFieldErrors = Partial<Record<LoginField, string>>;

export type LoginValidation =
  | { ok: true; data: LoginRequest }
  | { ok: false; errors: LoginFieldErrors };

export function validateLogin(input: LoginRequest): LoginValidation {
  const slug = input.slug.trim().toLowerCase();
  // El correo no se pasa a minusculas: el backend lo compara tal cual se
  // registro.
  const email = input.email.trim();
  const password = input.password;
  const errors: LoginFieldErrors = {};

  if (!slug) {
    errors.slug = "Ingresa el identificador del restaurante.";
  } else if (!SLUG_PATTERN.test(slug)) {
    errors.slug = "Solo minúsculas, números y guiones, entre 3 y 80 caracteres.";
  }

  if (!email) {
    errors.email = "Ingresa tu correo.";
  } else if (email.length > EMAIL_MAX_LENGTH) {
    errors.email = `El correo no puede exceder ${EMAIL_MAX_LENGTH} caracteres.`;
  } else if (!EMAIL_PATTERN.test(email)) {
    errors.email = "Ingresa un correo válido.";
  }

  if (!password) {
    errors.password = "Ingresa tu contraseña.";
  } else if (password.length > PASSWORD_MAX_LENGTH) {
    errors.password = `La contraseña no puede exceder ${PASSWORD_MAX_LENGTH} caracteres.`;
  }

  if (Object.keys(errors).length > 0) {
    return { ok: false, errors };
  }

  return { ok: true, data: { slug, email, password } };
}
