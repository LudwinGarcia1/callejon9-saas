"use client";

import { useState } from "react";
import { MinusIcon, PlusIcon, XIcon } from "lucide-react";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Money } from "@/components/shared/money";
import { getFoodVisual } from "@/lib/food-images";
import { cn } from "@/lib/utils";
import type { CategoryResponse, OrderStatus, ProductResponse } from "@/lib/types";

/** Sentinela para agrupar productos sin categoria asignada. */
const NO_CATEGORY = "sin-categoria";

/** Mismo tope que {@code AddOrderItemRequest.notes} en el backend. */
export const MAX_NOTE_LENGTH = 500;

/** Una linea del carrito local: un producto, cuantas unidades lleva y su
 * nota para cocina. El carrito es solo una vista previa; nada se envia al
 * backend hasta que el mesero confirma con "Agregar a la orden", en una sola
 * peticion. Un mismo producto puede ocupar varias lineas si llevan notas
 * distintas (una sopa "sin crema" y otra normal), por eso cada linea tiene su
 * propia llave y no se identifica por el producto. */
export interface CartLine {
  key: string;
  product: ProductResponse;
  quantity: number;
  notes: string;
}

interface ProductPickerProps {
  categories: CategoryResponse[];
  products: ProductResponse[];
  isLoading: boolean;
  cart: CartLine[];
  onAddProduct: (product: ProductResponse) => void;
  onIncrement: (lineKey: string) => void;
  onDecrement: (lineKey: string) => void;
  onRemove: (lineKey: string) => void;
  onNotesChange: (lineKey: string, notes: string) => void;
  onCommit: () => void;
  isCommitting: boolean;
  /** La orden esta PAID o CANCELED: el backend rechaza agregar productos. */
  disabled: boolean;
  /** La orden ya esta SENT o READY: agregar sigue permitido, pero hay que
   * dejarlo claro para que el mesero no piense que reenvia a cocina solo. */
  alreadySentToKitchen: boolean;
  /** Estado de la orden para adaptar el mensaje de aviso dinamicamente. */
  orderStatus?: OrderStatus;
}

/**
 * Catalogo de apoyo de la comanda: chips de categoria arriba, lista de
 * productos en medio y el carrito en construccion anclado al pie. Va sobre
 * superficie alterna para que se lea como panel secundario frente a la orden.
 */
export function ProductPicker({
  categories,
  products,
  isLoading,
  cart,
  onAddProduct,
  onIncrement,
  onDecrement,
  onRemove,
  onNotesChange,
  onCommit,
  isCommitting,
  disabled,
  alreadySentToKitchen,
  orderStatus,
}: ProductPickerProps) {
  const [activeCategory, setActiveCategory] = useState<string | null>(null);

  if (isLoading) {
    return (
      <aside className="flex flex-col gap-2 bg-surface-alt p-6 xl:border-l">
        <Skeleton className="h-[34px] w-full" />
        {Array.from({ length: 5 }).map((_, index) => (
          <Skeleton key={index} className="h-[58px] w-full" />
        ))}
      </aside>
    );
  }

  if (disabled) {
    return (
      <aside className="bg-surface-alt p-6 xl:border-l">
        <Alert variant="destructive">
          <AlertTitle>Esta orden ya está cerrada</AlertTitle>
          <AlertDescription>
            No se pueden agregar más productos a una orden pagada o cancelada.
          </AlertDescription>
        </Alert>
      </aside>
    );
  }

  const uncategorized = products.filter((product) => product.categoryId === null);
  const tabs = [
    ...categories.map((category) => ({ id: category.id, label: category.name })),
    ...(uncategorized.length > 0 ? [{ id: NO_CATEGORY, label: "Sin categoría" }] : []),
  ];
  const selected = activeCategory ?? tabs[0]?.id ?? null;
  const visibleProducts =
    selected === NO_CATEGORY
      ? uncategorized
      : products.filter((product) => product.categoryId === selected);

  const cartSubtotal = cart.reduce(
    (sum, line) => sum + line.product.price * line.quantity,
    0,
  );

  return (
    <aside className="flex flex-col bg-surface-alt xl:border-l">
      <div className="border-b px-[18px] pt-5 pb-4 sm:px-6">
        <p className="eyebrow">Agregar productos</p>
        {orderStatus === "READY" ? (
          <Alert className="mt-3 border-emerald-500/30 bg-emerald-500/10">
            <AlertTitle className="text-emerald-700 dark:text-emerald-400 font-semibold">
              Comanda lista para servir
            </AlertTitle>
            <AlertDescription className="text-emerald-800/90 dark:text-emerald-300/90">
              Los productos actuales ya salieron de cocina. Si agregas más productos, se enviarán como una nueva tanda.
            </AlertDescription>
          </Alert>
        ) : alreadySentToKitchen ? (
          <Alert className="mt-3">
            <AlertTitle>Esta orden ya está en cocina</AlertTitle>
            <AlertDescription>
              Lo que agregues ahora se sumará a la cuenta y aparecerá en cocina como pendiente.
            </AlertDescription>
          </Alert>
        ) : null}
        <div className="mt-3 flex flex-wrap gap-1.5">
          {tabs.map((tab) => (
            <button
              key={tab.id}
              type="button"
              onClick={() => setActiveCategory(tab.id)}
              aria-pressed={selected === tab.id}
              className={cn(
                "focus-sala inline-flex h-11 items-center rounded-sm border px-3 text-[13px] lg:h-[34px]",
                selected === tab.id
                  ? "border-primary bg-primary font-medium text-primary-foreground"
                  : "border-border text-muted-foreground hover:text-foreground",
              )}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      <div className="flex flex-1 flex-col gap-2 px-[18px] py-4 sm:px-6">
        {visibleProducts.length === 0 ? (
          <div className="py-2">
            <p className="eyebrow">Sin productos</p>
            <p className="mt-1 text-sm text-muted-foreground">
              No hay productos en esta categoría.
            </p>
          </div>
        ) : (
          visibleProducts.map((product) => {
            const visual = getFoodVisual(product.name);
            return (
              <div
                key={product.id}
                className="flex items-center justify-between gap-3 rounded-xl border bg-card p-3 shadow-xs hover:border-amber-500/40 transition-all"
              >
                <div className="flex items-center gap-3 min-w-0">
                  <div className="relative w-11 h-11 rounded-lg overflow-hidden bg-muted flex-shrink-0 border border-border/60">
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    <img
                      src={visual.image}
                      alt={product.name}
                      className="w-full h-full object-cover"
                      loading="lazy"
                    />
                    <span className="absolute bottom-0 right-0.5 text-[10px] filter drop-shadow">
                      {visual.emoji}
                    </span>
                  </div>
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium">{product.name}</p>
                    {product.description && (
                      <p className="truncate text-xs text-muted-foreground">{product.description}</p>
                    )}
                  </div>
                </div>
                <div className="flex shrink-0 items-center gap-3">
                  <Money amount={product.price} className="font-mono text-sm font-semibold" />
                  <Button
                    type="button"
                    variant="outline"
                    size="icon-sm"
                    className="text-brand hover:bg-amber-500 hover:text-neutral-950 active:scale-95 transition-all"
                    onClick={() => onAddProduct(product)}
                    aria-label={`Agregar ${product.name}`}
                  >
                    <PlusIcon />
                  </Button>
                </div>
              </div>
            );
          })
        )}
      </div>

      {/* Pie del carrito: lo que se va a agregar, con su propio subtotal, para
          que el mesero confirme un lote y no producto por producto. */}
      <div className="border-t bg-card px-[18px] py-[18px] sm:px-6">
        <div className="mb-3 flex items-baseline justify-between">
          <p className="eyebrow">Por agregar</p>
          <p className="font-mono text-xs text-muted-foreground">
            {cart.length} {cart.length === 1 ? "línea" : "líneas"}
          </p>
        </div>

        {cart.length === 0 ? (
          <p className="text-[13px] text-muted-foreground">
            Toca el <span aria-hidden>+</span> de un producto para agregarlo. Cada producto lleva
            su propia nota para cocina.
          </p>
        ) : (
          <div className="flex flex-col gap-3">
            {cart.map((line) => {
              const visual = getFoodVisual(line.product.name);
              return (
                <div key={line.key} className="flex flex-col gap-1.5 p-2 rounded-xl bg-surface border border-border/60">
                  <div className="flex items-center gap-2.5">
                    <div className="w-8 h-8 rounded-lg overflow-hidden bg-muted shrink-0 border border-border/50">
                      {/* eslint-disable-next-line @next/next/no-img-element */}
                      <img src={visual.image} alt={line.product.name} className="w-full h-full object-cover" />
                    </div>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium">{line.product.name}</p>
                      <Money
                        amount={line.product.price * line.quantity}
                        className="font-mono text-xs text-muted-foreground"
                      />
                    </div>
                    <div className="flex items-center gap-1.5">
                      <Button
                        type="button"
                        variant="outline"
                        size="icon-xs"
                        onClick={() => onDecrement(line.key)}
                        aria-label="Quitar una unidad"
                      >
                        <MinusIcon />
                      </Button>
                      <span className="w-6 text-center font-mono text-sm tabular-nums font-bold">
                        {line.quantity}
                      </span>
                      <Button
                        type="button"
                        variant="outline"
                        size="icon-xs"
                        onClick={() => onIncrement(line.key)}
                        aria-label="Agregar una unidad"
                      >
                        <PlusIcon />
                      </Button>
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon-xs"
                        onClick={() => onRemove(line.key)}
                        aria-label="Quitar del carrito"
                      >
                        <XIcon />
                      </Button>
                    </div>
                  </div>
                  <Input
                    value={line.notes}
                    onChange={(event) => onNotesChange(line.key, event.target.value)}
                    maxLength={MAX_NOTE_LENGTH}
                    placeholder="Nota para cocina (opcional)"
                    aria-label={`Nota para ${line.product.name}`}
                    className="h-9 text-[13px] rounded-lg"
                  />
                </div>
              );
            })}
          </div>
        )}

        <div className="mt-3.5 flex items-baseline justify-between border-t pt-3">
          <span className="text-sm text-muted-foreground">Subtotal por agregar</span>
          <Money amount={cartSubtotal} className="font-display text-[24px]" />
        </div>

        <Button
          type="button"
          variant="brand"
          size="lg"
          className="mt-3.5 w-full"
          onClick={onCommit}
          disabled={cart.length === 0 || isCommitting}
        >
          {isCommitting ? "Agregando…" : "Agregar a la orden"}
        </Button>
      </div>
    </aside>
  );
}
