"use client";

import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
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
import { queryKeys } from "@/lib/query-keys";
import { USER_ROLE_LABELS, type CreateUserRequest, type UserResponse, type UserRole } from "@/lib/types";

/** Roles que se pueden asignar desde el restaurante. SUPER_ADMIN pertenece al
 * tenant tecnico de la plataforma y el backend lo rechaza con 400, asi que
 * nunca se ofrece como opcion aqui. */
const ASSIGNABLE_ROLES: UserRole[] = ["ADMIN", "WAITER", "KITCHEN", "CASHIER"];

/** Formulario de alta de usuario. `role` viene de un Select de shadcn, que no
 * participa de FormData, asi que se guarda en estado y se combina con el
 * resto del payload al enviar. */
export function CreateUserDialog() {
  const [open, setOpen] = useState(false);
  const [role, setRole] = useState<UserRole>("WAITER");
  const queryClient = useQueryClient();

  const createMutation = useMutation({
    mutationFn: (payload: CreateUserRequest) =>
      api.post<UserResponse>(endpoints.users.create(), payload),
    onSuccess: (user) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.users.all() });
      toast.success(`Usuario "${user.fullName}" creado.`);
      setOpen(false);
      setRole("WAITER");
    },
  });

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);

    createMutation.mutate({
      email: String(formData.get("email") ?? ""),
      fullName: String(formData.get("fullName") ?? ""),
      role,
      password: String(formData.get("password") ?? ""),
    });
  }

  const apiError = createMutation.error instanceof ApiError ? createMutation.error : null;
  const hasFieldErrors = Boolean(apiError?.errors && Object.keys(apiError.errors).length > 0);

  return (
    <Dialog
      open={open}
      onOpenChange={(nextOpen) => {
        setOpen(nextOpen);
        if (!nextOpen) {
          createMutation.reset();
          setRole("WAITER");
        }
      }}
    >
      <DialogTrigger asChild>
        <Button>Nuevo usuario</Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Nuevo usuario</DialogTitle>
          <DialogDescription>Da de alta a un miembro del equipo del restaurante.</DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          {apiError && !hasFieldErrors && (
            <Alert variant="destructive">
              <AlertTitle>No se pudo crear el usuario</AlertTitle>
              <AlertDescription>{apiError.message}</AlertDescription>
            </Alert>
          )}

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="fullName">Nombre completo</Label>
            <Input
              id="fullName"
              name="fullName"
              required
              maxLength={160}
              disabled={createMutation.isPending}
            />
            <FieldError error={apiError} field="fullName" />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="email">Correo</Label>
            <Input
              id="email"
              name="email"
              type="email"
              required
              maxLength={180}
              disabled={createMutation.isPending}
            />
            <FieldError error={apiError} field="email" />
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
              disabled={createMutation.isPending}
            />
            <FieldError error={apiError} field="password" />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="role">Rol</Label>
            <Select
              value={role}
              onValueChange={(value) => setRole(value as UserRole)}
              disabled={createMutation.isPending}
            >
              <SelectTrigger id="role" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {ASSIGNABLE_ROLES.map((assignableRole) => (
                  <SelectItem key={assignableRole} value={assignableRole}>
                    {USER_ROLE_LABELS[assignableRole]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <FieldError error={apiError} field="role" />
          </div>

          <DialogFooter>
            <Button type="submit" size="lg" disabled={createMutation.isPending}>
              {createMutation.isPending ? "Guardando…" : "Crear usuario"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
