import * as React from "react"
import { cva, type VariantProps } from "class-variance-authority"
import { Slot } from "radix-ui"

import { cn } from "@/lib/utils"

/**
 * Alturas del sistema Sala. En piso nada baja de 44px, asi que `lg` (52px) y
 * `xl` (56px) son las medidas de los botones primarios de pantalla, y `sm`
 * (34px) queda para filtros y acciones secundarias de escritorio.
 */
const buttonVariants = cva(
  "focus-sala group/button inline-flex shrink-0 items-center justify-center rounded-md border border-transparent bg-clip-padding text-sm font-medium whitespace-nowrap outline-none select-none disabled:pointer-events-none disabled:opacity-50 aria-invalid:border-destructive [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4",
  {
    variants: {
      variant: {
        /** Tinta: el boton primario de pantalla (ingresar, enviar a cocina). */
        default: "bg-primary text-primary-foreground hover:bg-primary/90",
        /** Color de marca: altas comerciales y confirmaciones de cobro. */
        brand: "bg-brand text-brand-foreground hover:bg-brand/90",
        outline: "border-input bg-transparent hover:bg-accent hover:text-accent-foreground",
        secondary: "bg-secondary text-secondary-foreground hover:bg-accent",
        ghost: "hover:bg-accent hover:text-accent-foreground",
        destructive:
          "border-destructive/40 bg-transparent text-destructive hover:bg-destructive/10",
        link: "text-brand underline decoration-from-font underline-offset-[3px] hover:text-brand/80",
      },
      size: {
        default: "h-10 gap-2 px-4",
        xs: "h-8 gap-1 rounded-sm px-2 text-xs [&_svg:not([class*='size-'])]:size-3",
        sm: "h-[34px] gap-1.5 rounded-sm px-3 text-[13px] [&_svg:not([class*='size-'])]:size-3.5",
        lg: "h-[52px] gap-2 px-5 text-base",
        xl: "h-14 gap-2 px-6 text-base",
        icon: "size-10",
        "icon-xs": "size-8 rounded-sm [&_svg:not([class*='size-'])]:size-3",
        "icon-sm": "size-[34px] rounded-sm [&_svg:not([class*='size-'])]:size-3.5",
        "icon-lg": "size-[52px]",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  }
)

function Button({
  className,
  variant = "default",
  size = "default",
  asChild = false,
  ...props
}: React.ComponentProps<"button"> &
  VariantProps<typeof buttonVariants> & {
    asChild?: boolean
  }) {
  const Comp = asChild ? Slot.Root : "button"

  return (
    <Comp
      data-slot="button"
      data-variant={variant}
      data-size={size}
      className={cn(buttonVariants({ variant, size, className }))}
      {...props}
    />
  )
}

export { Button, buttonVariants }
