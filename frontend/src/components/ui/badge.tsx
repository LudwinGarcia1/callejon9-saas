import * as React from "react"
import { cva, type VariantProps } from "class-variance-authority"
import { Slot } from "radix-ui"

import { cn } from "@/lib/utils"

/**
 * Chip del sistema Sala: 26px de alto, radio de 3px, mono de 10px en
 * mayusculas con tracking abierto. Nunca es una pastilla redonda: es una
 * etiqueta de dato.
 *
 * La variante `tone` se pinta con las variables que publica un contenedor con
 * `data-tone` (verde, marca, ambar o neutro), asi que el mismo chip sirve para
 * las cuatro familias de estado sin repetir la tabla de colores.
 */
const badgeVariants = cva(
  "group/badge inline-flex h-[26px] w-fit shrink-0 items-center justify-center gap-1.5 overflow-hidden rounded-sm border border-transparent px-2.5 font-mono text-[10px] font-medium tracking-[0.12em] whitespace-nowrap uppercase [&>svg]:pointer-events-none [&>svg]:size-3!",
  {
    variants: {
      variant: {
        tone: "border-[var(--tone-border)] bg-[var(--tone-bg)] text-[var(--tone-text)]",
        default: "bg-primary text-primary-foreground",
        brand: "bg-brand text-brand-foreground",
        secondary: "bg-secondary text-secondary-foreground",
        destructive: "border-destructive/40 text-destructive",
        outline: "border-border text-muted-foreground",
      },
    },
    defaultVariants: {
      variant: "tone",
    },
  }
)

function Badge({
  className,
  variant = "tone",
  asChild = false,
  ...props
}: React.ComponentProps<"span"> &
  VariantProps<typeof badgeVariants> & { asChild?: boolean }) {
  const Comp = asChild ? Slot.Root : "span"

  return (
    <Comp
      data-slot="badge"
      data-variant={variant}
      className={cn(badgeVariants({ variant }), className)}
      {...props}
    />
  )
}

export { Badge, badgeVariants }
