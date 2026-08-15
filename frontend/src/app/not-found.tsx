import Link from "next/link";

import { BrandShell } from "@/components/layout/brand-shell";
import { Button } from "@/components/ui/button";

export default function NotFound() {
  return (
    <BrandShell eyebrow="Error 404" title="Esta página no existe">
      <p className="mt-2 text-sm leading-[1.6] text-muted-foreground text-pretty">
        La dirección que abriste no corresponde a ninguna pantalla del sistema. Puede que la
        orden ya se haya cobrado o que el enlace esté incompleto.
      </p>
      <Button asChild size="lg" className="mt-6">
        <Link href="/">Volver al inicio</Link>
      </Button>
    </BrandShell>
  );
}
