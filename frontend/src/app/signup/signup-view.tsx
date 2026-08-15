"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMutation } from "@tanstack/react-query";
import { useState } from "react";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { FieldError } from "@/components/shared/field-error";
import { ApiError, api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";
import { formatCurrency } from "@/lib/format";
import type { SignupRequest, SignupResponse } from "@/lib/types";
import { cn } from "@/lib/utils";

/**
 * Los tres planes sembrados en el backend. GET /api/v1/platform/plans exige
 * rol SUPER_ADMIN y el alta de restaurante es anonima, asi que esta lista no
 * se puede obtener del servidor: queda fija aqui. El plan PRO es el
 * predeterminado porque el limite de 5 mesas del plan gratuito estorba en una
 * demo.
 */
const PLANS = [
  { code: "FREE", name: "Gratis", priceMonthly: 0, maxUsers: 3, maxTables: 5 },
  { code: "PRO", name: "Profesional", priceMonthly: 499, maxUsers: 15, maxTables: 30 },
  { code: "PREMIUM", name: "Premium", priceMonthly: 999, maxUsers: 60, maxTables: 120 },
] as const;

export function SignupView() {
  const router = useRouter();
  const [planCode, setPlanCode] = useState<string>("PRO");

  const signupMutation = useMutation({
    mutationFn: (payload: SignupRequest) =>
      api.post<SignupResponse>(endpoints.auth.signup(), payload),
    onSuccess: (data) => {
      router.push(`/login?slug=${encodeURIComponent(data.slug)}`);
    },
  });

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);

    signupMutation.mutate({
      restaurantName: String(formData.get("restaurantName") ?? ""),
      slug: String(formData.get("slug") ?? ""),
      adminEmail: String(formData.get("adminEmail") ?? ""),
      adminFullName: String(formData.get("adminFullName") ?? ""),
      password: String(formData.get("password") ?? ""),
      planCode,
    });
  }

  const apiError = signupMutation.error instanceof ApiError ? signupMutation.error : null;
  const hasFieldErrors = Boolean(apiError?.errors && Object.keys(apiError.errors).length > 0);

  return (
    <div className="flex min-h-screen items-center justify-center px-4 py-10">
      <div className="w-full max-w-[520px] rounded-2xl border border-border-strong bg-card px-6 pt-10 pb-9 sm:px-10">
        <p className="eyebrow tracking-[0.14em]">Registro · paso 1 de 2</p>
        <h1 className="mt-1.5 font-display text-[30px] leading-[1.05] font-normal sm:text-[34px]">
          Registra tu restaurante
        </h1>
        <p className="mt-1.5 mb-6 text-sm leading-[1.6] text-muted-foreground text-pretty">
          Creas tu cuenta de administrador y el restaurante queda listo para dar de alta
          mesas, catálogo y personal.
        </p>

        <form onSubmit={handleSubmit} className="flex flex-col gap-3.5">
          {apiError && !hasFieldErrors && (
            <Alert variant="destructive">
              <AlertTitle>No se pudo completar el registro</AlertTitle>
              <AlertDescription>{apiError.message}</AlertDescription>
            </Alert>
          )}

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="restaurantName">Nombre del restaurante</Label>
            <Input
              id="restaurantName"
              name="restaurantName"
              required
              maxLength={160}
              disabled={signupMutation.isPending}
            />
            <FieldError error={apiError} field="restaurantName" />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="slug">Identificador</Label>
            <Input
              id="slug"
              name="slug"
              required
              pattern="[a-z0-9-]{3,80}"
              title="Solo minúsculas, números y guiones, entre 3 y 80 caracteres."
              placeholder="mi-restaurante"
              disabled={signupMutation.isPending}
            />
            <p className="text-xs text-muted-foreground">
              Lo usarás para iniciar sesión, junto con tu correo.
            </p>
            <FieldError error={apiError} field="slug" />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="adminFullName">Tu nombre completo</Label>
            <Input
              id="adminFullName"
              name="adminFullName"
              required
              maxLength={160}
              disabled={signupMutation.isPending}
            />
            <FieldError error={apiError} field="adminFullName" />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="adminEmail">Correo del administrador</Label>
            <Input
              id="adminEmail"
              name="adminEmail"
              type="email"
              required
              maxLength={180}
              disabled={signupMutation.isPending}
            />
            <FieldError error={apiError} field="adminEmail" />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="password">Contraseña</Label>
            <Input
              id="password"
              name="password"
              type="password"
              required
              minLength={8}
              maxLength={100}
              placeholder="Mínimo 8 caracteres"
              disabled={signupMutation.isPending}
            />
            <FieldError error={apiError} field="password" />
          </div>

          {/* Tres tarjetas apiladas en vez de un <select>: el plan es una
              decision comercial y sus limites tienen que verse sin desplegar. */}
          <fieldset className="flex flex-col gap-2">
            <legend className="eyebrow mb-2">Plan</legend>
            {PLANS.map((plan) => {
              const isSelected = planCode === plan.code;
              return (
                <label
                  key={plan.code}
                  className={cn(
                    "flex cursor-pointer items-center justify-between gap-3 rounded-md border px-4 py-3.5 focus-within:border-brand focus-within:shadow-[0_0_0_3px_color-mix(in_oklch,var(--brand)_12%,transparent)]",
                    isSelected
                      ? "border-brand bg-brand-tint"
                      : "border-border-strong hover:bg-accent/50",
                  )}
                >
                  <input
                    type="radio"
                    name="planCode"
                    value={plan.code}
                    checked={isSelected}
                    onChange={() => setPlanCode(plan.code)}
                    disabled={signupMutation.isPending}
                    className="sr-only"
                  />
                  <span>
                    <span className="block font-display text-[19px] leading-tight">
                      {plan.name}
                    </span>
                    <span className="block text-xs text-muted-foreground">
                      {plan.maxUsers} usuarios · {plan.maxTables} mesas
                    </span>
                  </span>
                  <span className="font-mono text-[15px] tabular-nums">
                    {plan.priceMonthly === 0
                      ? formatCurrency(0)
                      : `${formatCurrency(plan.priceMonthly)} / mes`}
                  </span>
                </label>
              );
            })}
          </fieldset>

          <Button
            type="submit"
            variant="brand"
            size="lg"
            disabled={signupMutation.isPending}
            className="mt-1"
          >
            {signupMutation.isPending ? "Creando cuenta…" : "Crear cuenta"}
          </Button>

          <p className="text-center text-sm text-muted-foreground">
            ¿Ya tienes cuenta?{" "}
            <Link
              href="/login"
              className="text-brand underline underline-offset-[3px] hover:text-brand/80"
            >
              Inicia sesión
            </Link>
          </p>
        </form>
      </div>
    </div>
  );
}
