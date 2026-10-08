import type { ApiError } from "@/lib/api";

interface FieldErrorProps {
  error?: ApiError | null;
  field: string;
  /** Mensaje de validacion local; tiene prioridad sobre el del backend. */
  message?: string;
}

/**
 * Muestra el mensaje de validacion de un campo especifico si `ApiError`
 * trae uno para ese nombre de campo (el backend los envia en `errors` como
 * mapa de campo a mensaje). No renderiza nada si no hay mensaje.
 */
export function FieldError({ error, field, message }: FieldErrorProps) {
  const text = message ?? error?.errors?.[field];

  if (!text) {
    return null;
  }

  return <p className="text-sm text-destructive">{text}</p>;
}
