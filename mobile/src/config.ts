/**
 * URL base del backend de Callejón 9. Se lee de EXPO_PUBLIC_API_URL (archivo
 * .env) y debe apuntar a la raíz del servidor, sin /api/v1.
 *
 * Por defecto solo se aceptan URLs https: las credenciales y la cookie de
 * sesión nunca deben viajar en claro. EXPO_PUBLIC_ALLOW_INSECURE_HTTP=true
 * relaja la regla únicamente en desarrollo (__DEV__) para probar contra la
 * red local; en una build de producción se ignora.
 */
const rawUrl = (process.env.EXPO_PUBLIC_API_URL ?? '').trim().replace(/\/+$/, '');
const allowInsecure = __DEV__ && process.env.EXPO_PUBLIC_ALLOW_INSECURE_HTTP === 'true';

function resolveApiUrl(): { url: string; error: string | null } {
  if (!rawUrl) {
    return { url: '', error: 'Falta EXPO_PUBLIC_API_URL en el archivo .env.' };
  }
  if (rawUrl.startsWith('https://')) {
    return { url: rawUrl, error: null };
  }
  if (rawUrl.startsWith('http://') && allowInsecure) {
    return { url: rawUrl, error: null };
  }
  return {
    url: '',
    error: 'La URL del servidor debe usar https:// para proteger las credenciales.',
  };
}

const resolved = resolveApiUrl();

export const API_URL = resolved.url;
export const CONFIG_ERROR = resolved.error;
export const IS_SECURE_TRANSPORT = API_URL.startsWith('https://');

/** Tiempo máximo de espera por petición antes de darla por fallida. */
export const REQUEST_TIMEOUT_MS = 15_000;
