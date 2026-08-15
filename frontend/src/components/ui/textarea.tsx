import * as React from "react"

import { cn } from "@/lib/utils"

/** Mismo chasis que `Input`: radio de 4px, texto de 15px y el foco del sistema. */
function Textarea({ className, ...props }: React.ComponentProps<"textarea">) {
  return (
    <textarea
      data-slot="textarea"
      className={cn(
        "focus-sala field-sizing-content flex min-h-[92px] w-full rounded-md border border-input bg-transparent px-3 py-2.5 text-[15px] leading-[1.5] outline-none placeholder:text-muted-foreground disabled:cursor-not-allowed disabled:opacity-50 aria-invalid:border-destructive",
        className
      )}
      {...props}
    />
  )
}

export { Textarea }
