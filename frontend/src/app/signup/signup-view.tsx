"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMutation } from "@tanstack/react-query";
import { useState } from "react";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { FieldError } from "@/components/shared/field-error";
import { ApiError, api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";
import { formatCurrency } from "@/lib/format";
import type { SignupRequest, SignupResponse } from "@/lib/types";

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
    <div className="flex min-h-screen items-center justify-center p-6">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle>Registra tu restaurante</CardTitle>
          <CardDescription>
            Crea tu cuenta de administrador y empieza a usar Callejon 9.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
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
              <Label htmlFor="slug">Identificador (slug)</Label>
              <Input
                id="slug"
                name="slug"
                required
                pattern="[a-z0-9-]{3,80}"
                title="Solo minusculas, numeros y guiones, entre 3 y 80 caracteres."
                placeholder="mi-restaurante"
                disabled={signupMutation.isPending}
              />
              <p className="text-xs text-muted-foreground">
                Lo usaras para iniciar sesion despues, junto con tu correo.
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
              <Label htmlFor="password">Contrasena</Label>
              <Input
                id="password"
                name="password"
                type="password"
                required
                minLength={8}
                maxLength={100}
                disabled={signupMutation.isPending}
              />
              <FieldError error={apiError} field="password" />
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="planCode">Plan</Label>
              <Select value={planCode} onValueChange={setPlanCode} disabled={signupMutation.isPending}>
                <SelectTrigger id="planCode" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {PLANS.map((plan) => (
                    <SelectItem key={plan.code} value={plan.code}>
                      {plan.name} — {formatCurrency(plan.priceMonthly)}/mes ({plan.maxUsers}{" "}
                      usuarios, {plan.maxTables} mesas)
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <Button type="submit" disabled={signupMutation.isPending} className="mt-2">
              {signupMutation.isPending ? "Creando cuenta..." : "Crear cuenta"}
            </Button>

            <p className="text-center text-sm text-muted-foreground">
              Ya tienes cuenta?{" "}
              <Link href="/login" className="text-primary underline underline-offset-4">
                Inicia sesion
              </Link>
            </p>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
