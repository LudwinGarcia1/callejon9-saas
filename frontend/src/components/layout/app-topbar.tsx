"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { PowerIcon } from "lucide-react";

import { TenantBadge } from "@/components/layout/tenant-badge";
import { ThemeToggle } from "@/components/layout/theme-toggle";
import { NAV_ITEMS_BY_ROLE } from "@/components/layout/nav";
import { useNavCounts } from "@/hooks/use-nav-counts";
import { useSession } from "@/hooks/use-session";
import { USER_ROLE_LABELS } from "@/lib/types";
import { cn } from "@/lib/utils";

/**
 * Header horizontal que sustituye al sidebar por debajo de 1280px: es la
 * navegacion del mesero en tablet y en telefono. Tabs de 44px porque en piso
 * ningun control baja de esa altura.
 */
export function AppTopbar() {
  const { user, isLoading, logout } = useSession();
  const pathname = usePathname();
  const navItems = user ? NAV_ITEMS_BY_ROLE[user.role] : [];
  const counts = useNavCounts(user?.role);

  return (
    <header className="flex flex-wrap items-center justify-between gap-x-5 gap-y-3 border-b bg-sidebar px-5 py-3.5 xl:hidden">
      <div className="flex min-w-0 items-center gap-3">
        <TenantBadge
          restaurantName={user?.restaurantName}
          slug={undefined}
          isLoading={isLoading}
          size="sm"
        />
        {user && (
          <p className="eyebrow hidden truncate tracking-[0.08em] sm:block">
            {user.fullName.split(" ")[0]} · {USER_ROLE_LABELS[user.role].toLowerCase()}
          </p>
        )}
      </div>

      <nav className="order-3 flex w-full gap-2 overflow-x-auto sm:order-none sm:w-auto">
        {navItems.map((item) => {
          const isActive = pathname.startsWith(item.href);
          const count = counts[item.href];

          return (
            <Link
              key={item.href}
              href={item.href}
              aria-current={isActive ? "page" : undefined}
              className={cn(
                "focus-sala inline-flex h-11 shrink-0 items-center gap-2 rounded-md border px-4 text-[15px]",
                isActive
                  ? "border-brand bg-brand font-medium text-brand-foreground"
                  : "border-border text-muted-foreground hover:bg-accent hover:text-foreground",
              )}
            >
              {item.label}
              {count !== undefined && (
                <span className="font-mono text-[12px] opacity-75">{count}</span>
              )}
            </Link>
          );
        })}
      </nav>

      <div className="flex items-center gap-3">
        <Clock />
        <ThemeToggle compact />
        <button
          type="button"
          onClick={() => logout()}
          aria-label="Cerrar sesión"
          className="focus-sala inline-flex size-10 items-center justify-center rounded-md border text-muted-foreground hover:text-foreground"
        >
          <PowerIcon className="size-4" />
        </button>
      </div>
    </header>
  );
}

/**
 * Reloj del turno. Arranca vacio y se llena en el cliente: renderizar la hora
 * en el servidor daria un desajuste de hidratacion garantizado.
 */
function Clock() {
  const [time, setTime] = useState<string | null>(null);

  useEffect(() => {
    const tick = () =>
      setTime(
        new Date().toLocaleTimeString("es-MX", { hour: "2-digit", minute: "2-digit" }),
      );
    tick();
    const interval = setInterval(tick, 30_000);
    return () => clearInterval(interval);
  }, []);

  return (
    <p className="hidden font-mono text-[22px] tabular-nums md:block" suppressHydrationWarning>
      {time ?? ""}
    </p>
  );
}
