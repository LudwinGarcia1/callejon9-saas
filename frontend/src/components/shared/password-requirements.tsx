import { CheckIcon, CircleIcon } from "lucide-react";

import { passwordRules } from "@/lib/password-policy";
import { cn } from "@/lib/utils";

interface PasswordRequirementsProps {
  id: string;
  password: string;
  /** Datos de la cuenta que la contrasena no debe contener. */
  personalData?: readonly string[];
}

/**
 * Lista de requisitos de una contrasena nueva que se marca mientras se
 * escribe. Se enlaza al campo con `aria-describedby={id}`. La lista de
 * contrasenas comunes solo la revisa el servidor, asi que una contrasena con
 * todo marcado todavia puede volver con error.
 */
export function PasswordRequirements({ id, password, personalData = [] }: PasswordRequirementsProps) {
  const rules = passwordRules(password, personalData);

  return (
    <ul id={id} className="flex flex-col gap-1 text-xs">
      {rules.map((rule) => (
        <li
          key={rule.id}
          className={cn(
            "flex items-center gap-1.5",
            rule.met ? "text-foreground" : "text-muted-foreground",
          )}
        >
          {rule.met ? (
            <CheckIcon aria-hidden className="size-3.5 text-brand" />
          ) : (
            <CircleIcon aria-hidden className="size-3" />
          )}
          <span>
            {rule.label}
            <span className="sr-only">{rule.met ? " (cumplido)" : " (pendiente)"}</span>
          </span>
        </li>
      ))}
    </ul>
  );
}
