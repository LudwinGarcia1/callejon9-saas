"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useMutation, useQueryClient } from "@tanstack/react-query";

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
const LOGIN_FAILED_MESSAGE = "Restaurante, correo o contrasena incorrectos.";

export function LoginView() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const searchParams = useSearchParams();

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
    <div className="flex min-h-screen items-center justify-center p-6">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>Iniciar sesion</CardTitle>
          <CardDescription>
            Tu correo solo es unico dentro de tu restaurante, no en toda la
            plataforma. Por eso pedimos el identificador antes.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex flex-col gap-4">
            {expired && (
              <Alert>
                <AlertTitle>Sesion expirada</AlertTitle>
                <AlertDescription>Tu sesion expiro por inactividad.</AlertDescription>
              </Alert>
            )}

            {apiError && (isUnauthorized || !hasFieldErrors) && (
              <Alert variant="destructive">
                <AlertTitle>No se pudo iniciar sesion</AlertTitle>
                <AlertDescription>
                  {isUnauthorized ? LOGIN_FAILED_MESSAGE : apiError.message}
                </AlertDescription>
              </Alert>
            )}

            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="slug">Restaurante (slug)</Label>
                <Input
                  id="slug"
                  name="slug"
                  required
                  defaultValue={prefilledSlug}
                  disabled={loginMutation.isPending}
                />
                <FieldError error={apiError} field="slug" />
              </div>

              <div className="flex flex-col gap-1.5">
                <Label htmlFor="email">Correo</Label>
                <Input
                  id="email"
                  name="email"
                  type="email"
                  required
                  disabled={loginMutation.isPending}
                />
                <FieldError error={apiError} field="email" />
              </div>

              <div className="flex flex-col gap-1.5">
                <Label htmlFor="password">Contrasena</Label>
                <Input
                  id="password"
                  name="password"
                  type="password"
                  required
                  disabled={loginMutation.isPending}
                />
                <FieldError error={apiError} field="password" />
              </div>

              <Button type="submit" disabled={loginMutation.isPending} className="mt-2">
                {loginMutation.isPending ? "Ingresando..." : "Ingresar"}
              </Button>

              <p className="text-center text-sm text-muted-foreground">
                No tienes cuenta?{" "}
                <Link href="/signup" className="text-primary underline underline-offset-4">
                  Registra tu restaurante
                </Link>
              </p>
            </form>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
