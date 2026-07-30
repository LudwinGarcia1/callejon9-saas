/**
 * Tipos del contrato de API conocido hasta ahora. Los endpoints todavia estan
 * en construccion en el backend; este archivo solo describe las formas de
 * datos, no invoca nada.
 */

/** Roles exactos que reconoce el backend. */
export type UserRole = "SUPER_ADMIN" | "ADMIN" | "WAITER" | "KITCHEN" | "CASHIER";

/**
 * RFC 7807 Problem Details devuelto por el backend en respuestas de error.
 * `errors` mapea el nombre de un campo a su mensaje de validacion.
 */
export interface ProblemDetail {
  title?: string;
  detail?: string;
  status?: number;
  errors?: Record<string, string>;
}

/** POST /api/v1/signup */
export interface SignupRequest {
  restaurantName: string;
  /** Debe cumplir ^[a-z0-9-]{3,80}$ */
  slug: string;
  adminEmail: string;
  adminFullName: string;
  password: string;
  planCode: string;
}

export interface SignupResponse {
  tenantId: string;
  slug: string;
  adminEmail: string;
}

/**
 * POST /api/v1/auth/login
 * El slug es obligatorio porque el email solo es unico dentro de un
 * restaurante, no en toda la plataforma.
 */
export interface LoginRequest {
  slug: string;
  email: string;
  password: string;
}

export interface LoginResponse {
  userId: string;
  fullName: string;
  role: UserRole;
  twoFactorRequired: boolean;
}

/** GET /api/v1/platform/plans (requiere rol SUPER_ADMIN) */
export interface Plan {
  code: string;
  name: string;
  priceMonthly: number;
  maxUsers: number;
  maxTables: number;
}
