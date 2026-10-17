import { ApiError, request } from './client';
import type { LoginRequest, LoginResponse, MeResponse } from './types';

/**
 * El backend responde 401 sin cuerpo tanto si el correo no existe como si la
 * contraseña es incorrecta o el restaurante no coincide. Es deliberado: no
 * revela qué cuentas existen. La app muestra un único mensaje para ese caso,
 * que cubre «credenciales incorrectas» y «usuario no encontrado».
 */
export const LOGIN_FAILED_MESSAGE = 'Restaurante, correo o contraseña incorrectos.';

export async function login(credentials: LoginRequest): Promise<LoginResponse> {
  try {
    return await request<LoginResponse>('POST', '/auth/login', {
      body: credentials,
      skipRefresh: true,
    });
  } catch (error) {
    if (error instanceof ApiError && error.kind === 'unauthorized') {
      throw new ApiError('unauthorized', LOGIN_FAILED_MESSAGE, 401);
    }
    throw error;
  }
}

/** Recupera la sesión guardada en el almacén de cookies al abrir la app. */
export function fetchCurrentUser(): Promise<MeResponse> {
  return request<MeResponse>('GET', '/auth/me');
}

/** Revoca la sesión en el servidor; el backend también borra las cookies. */
export async function logout(): Promise<void> {
  try {
    await request<void>('POST', '/auth/logout', { skipRefresh: true });
  } catch {
    // Si el servidor no responde, la app sale igual: la sesión expira sola.
  }
}
