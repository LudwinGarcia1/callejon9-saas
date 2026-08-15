"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { FieldError } from "@/components/shared/field-error";
import { ROLE_LANDING_PATH } from "@/hooks/use-session";
import { ApiError, api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";
import { queryKeys } from "@/lib/query-keys";
import type { LoginRequest, LoginResponse } from "@/lib/types";

/**
 * Un 401 de login llega con cuerpo vacio (ver AuthController.onBadCredentials):
 * a proposito no dice si fallo el restaurante, el correo o la contrasena. El
 * mensaje neutral es responsabilidad del frontend.
 */
const LOGIN_FAILED_MESSAGE = "Restaurante, correo o contraseña incorrectos.";

export function LoginView() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const searchParams = useSearchParams();
  const [showPassword, setShowPassword] = useState(false);

  const expired = searchParams.get("expired") === "1";
  const nextPath = searchParams.get("next");
  const prefilledSlug = searchParams.get("slug") ?? "";

  const loginMutation = useMutation({
    mutationFn: (payload: LoginRequest) =>
      api.post<LoginResponse>(endpoints.auth.login(), payload),
    onSuccess: async (data) => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.session.me() });
      router.push(nextPath ?? ROLE_LANDING_PATH[data.role]);
    },
  });

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);

    loginMutation.mutate({
      slug: String(formData.get("slug") ?? ""),
      email: String(formData.get("email") ?? ""),
      password: String(formData.get("password") ?? ""),
    });
  }

  const apiError = loginMutation.error instanceof ApiError ? loginMutation.error : null;
  const isUnauthorized = apiError?.status === 401;
  const hasFieldErrors = Boolean(apiError?.errors && Object.keys(apiError.errors).length > 0);

  return (
    <div className="grid min-h-screen grid-cols-1 bg-card lg:grid-cols-[340px_minmax(0,1fr)]">
      {/* Panel de marca de la plataforma: es lo unico de esta pantalla que no
          pertenece al restaurante, asi que va en tinta y no en color de marca. */}
      <aside className="flex flex-col justify-between gap-10 bg-primary px-8 py-9 text-primary-foreground">
        <div>
          <p className="font-display text-[26px] leading-none">Callejón 9</p>
          <p className="eyebrow mt-1.5 tracking-[0.14em] text-primary-foreground/70">
            Operación de restaurantes
          </p>
        </div>
        <div className="flex flex-col gap-[18px]">
          <p className="font-display text-[26px] leading-[1.15] italic lg:text-[32px]">
            Mesas, comandas, cocina y caja en un solo turno.
          </p>
          <div className="h-px bg-primary-foreground/25" />
          <p className="text-[13px] leading-[1.6] text-primary-foreground/80">
            Cada restaurante trabaja aislado: sus datos no se cruzan con los de nadie más, y
            la base de datos lo garantiza.
          </p>
        </div>
      </aside>

      <div className="flex flex-col justify-center px-6 py-12 sm:px-14">
        <div className="w-full max-w-[420px]">
          <p className="eyebrow tracking-[0.14em]">Iniciar sesión</p>
          <h1 className="mt-1.5 font-display text-[34px] leading-none font-normal sm:text-[40px]">
            Bienvenido de vuelta
          </h1>
          <p className="mt-2 mb-7 text-sm leading-[1.6] text-muted-foreground text-pretty">
            Tu correo es único dentro de tu restaurante, no en toda la plataforma: por eso
            pedimos el identificador antes.
          </p>

          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            {expired && (
              <Alert>
                <AlertTitle>Sesión expirada</AlertTitle>
                <AlertDescription>Tu sesión expiró por inactividad.</AlertDescription>
              </Alert>
            )}

            {apiError && (isUnauthorized || !hasFieldErrors) && (
              <Alert variant="destructive">
                <AlertTitle>No se pudo iniciar sesión</AlertTitle>
                <AlertDescription>
                  {isUnauthorized ? LOGIN_FAILED_MESSAGE : apiError.message}
                </AlertDescription>
              </Alert>
            )}

            <div className="flex flex-col gap-[7px]">
              <Label htmlFor="slug">Restaurante</Label>
              {/* Input compuesto: el prefijo del dominio explica que es el
                  "slug" sin tener que explicarlo con un hint debajo. */}
              <div className="flex h-12 items-center overflow-hidden rounded-md border border-input focus-within:border-brand focus-within:shadow-[0_0_0_3px_color-mix(in_oklch,var(--brand)_12%,transparent)]">
                <span className="flex h-full shrink-0 items-center border-r border-border px-3 font-mono text-[13px] text-muted-foreground">
                  c9.mx /
                </span>
                <input
                  id="slug"
                  name="slug"
                  required
                  autoComplete="organization"
                  placeholder="mi-restaurante"
                  defaultValue={prefilledSlug}
                  disabled={loginMutation.isPending}
                  className="h-full min-w-0 flex-1 bg-transparent px-3 text-[15px] outline-none placeholder:text-muted-foreground disabled:opacity-50"
                />
              </div>
              <FieldError error={apiError} field="slug" />
            </div>

            <div className="flex flex-col gap-[7px]">
              <Label htmlFor="email">Correo</Label>
              <Input
                id="email"
                name="email"
                type="email"
                required
                autoComplete="email"
                className="h-12"
                disabled={loginMutation.isPending}
              />
              <FieldError error={apiError} field="email" />
            </div>

            <div className="flex flex-col gap-[7px]">
              <Label htmlFor="password">Contraseña</Label>
              <div className="relative">
                <Input
                  id="password"
                  name="password"
                  type={showPassword ? "text" : "password"}
                  required
                  autoComplete="current-password"
                  className="h-12 pr-20"
                  disabled={loginMutation.isPending}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((visible) => !visible)}
                  className="absolute inset-y-0 right-3 flex items-center text-xs text-brand hover:text-brand/80"
                >
                  {showPassword ? "Ocultar" : "Mostrar"}
                </button>
              </div>
              <FieldError error={apiError} field="password" />
            </div>

            <Button type="submit" size="lg" disabled={loginMutation.isPending} className="mt-1.5">
              {loginMutation.isPending ? "Ingresando…" : "Ingresar"}
            </Button>

            <p className="text-sm text-muted-foreground">
              ¿No tienes cuenta?{" "}
              <Link
                href="/signup"
                className="text-brand underline underline-offset-[3px] hover:text-brand/80"
              >
                Registra tu restaurante
              </Link>
            </p>
          </form>
        </div>
      </div>
    </div>
  );
}
