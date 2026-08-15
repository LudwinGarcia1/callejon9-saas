import * as React from "react"

import { cn } from "@/lib/utils"

/**
 * Input del sistema Sala: 46px de alto, radio de 4px y foco en color de marca
 * con un halo de 3px al 12%. Es la unica sombra de todo el sistema.
 */
function Input({ className, type, ...props }: React.ComponentProps<"input">) {
  return (
    <input
      type={type}
      data-slot="input"
      className={cn(
        "focus-sala h-[46px] w-full min-w-0 rounded-md border border-input bg-transparent px-3 text-[15px] outline-none file:inline-flex file:h-8 file:border-0 file:bg-transparent file:text-sm file:font-medium file:text-foreground placeholder:text-muted-foreground disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-50 aria-invalid:border-destructive",
        className
      )}
      {...props}
    />
  )
}

export { Input }
