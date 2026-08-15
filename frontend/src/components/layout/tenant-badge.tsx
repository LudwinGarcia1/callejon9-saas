import { Skeleton } from "@/components/ui/skeleton";
import { monogramOf } from "@/lib/tenant-theme";
import { cn } from "@/lib/utils";

interface TenantBadgeProps {
  restaurantName: string | undefined;
  slug?: string | undefined;
  isLoading?: boolean;
  /** Version compacta para el header horizontal de tablet. */
  size?: "default" | "sm";
  className?: string;
}

/**
 * Identidad del restaurante: monograma sobre el color de marca mas nombre y
 * slug. El monograma hace de logo hasta que exista subida de imagen, que
 * ocupara exactamente esta caja con `object-fit: contain`.
 */
export function TenantBadge({
  restaurantName,
  slug,
  isLoading = false,
  size = "default",
  className,
}: TenantBadgeProps) {
  const isSmall = size === "sm";

  if (isLoading) {
    return (
      <div className={cn("flex items-center gap-2.5", className)}>
        <Skeleton className={isSmall ? "size-[34px] rounded-md" : "size-9 rounded-md"} />
        <div className="flex flex-col gap-1.5">
          <Skeleton className="h-3.5 w-32" />
          <Skeleton className="h-2.5 w-20" />
        </div>
      </div>
    );
  }

  return (
    <div className={cn("flex min-w-0 items-center gap-2.5", className)}>
      <div
        className={cn(
          "flex shrink-0 items-center justify-center rounded-md bg-brand font-display text-brand-foreground",
          isSmall ? "size-[34px] text-[18px]" : "size-9 text-[20px]",
        )}
        aria-hidden
      >
        {monogramOf(restaurantName)}
      </div>
      <div className="min-w-0">
        <p
          className={cn(
            "truncate font-display leading-[1.15]",
            isSmall ? "text-[16px]" : "text-[17px]",
          )}
        >
          {restaurantName ?? "Callejón 9"}
        </p>
        {slug && <p className="eyebrow truncate tracking-[0.08em]">{slug}</p>}
      </div>
    </div>
  );
}
