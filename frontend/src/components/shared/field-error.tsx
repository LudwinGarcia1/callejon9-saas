import type { ApiError } from "@/lib/api";

interface FieldErrorProps {
  error?: ApiError | null;
  field: string;
}

/**
 * Muestra el mensaje de validacion de un campo especifico si `ApiError`
 * trae uno para ese nombre de campo (el backend los envia en `errors` como
 * mapa de campo a mensaje). No renderiza nada si no hay mensaje.
 */
export function FieldError({ error, field }: FieldErrorProps) {
  const message = error?.errors?.[field];

  if (!message) {
    return null;
  }

  return <p className="text-sm text-destructive">{message}</p>;
}
