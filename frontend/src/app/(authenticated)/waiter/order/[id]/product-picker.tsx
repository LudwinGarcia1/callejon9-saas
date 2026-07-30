"use client";

import { MinusIcon, PlusIcon, XIcon } from "lucide-react";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Money } from "@/components/shared/money";
import type { CategoryResponse, ProductResponse } from "@/lib/types";

/** Sentinela para agrupar productos sin categoria asignada. */
const NO_CATEGORY_TAB = "sin-categoria";

/** Una linea del carrito local: un producto y cuantas unidades lleva. El
 * carrito es solo una vista previa; nada se envia al backend hasta que el
 * mesero confirma con "Agregar a la orden", en una sola peticion. */
export interface CartLine {
  product: ProductResponse;
  quantity: number;
}

interface ProductPickerProps {
  categories: CategoryResponse[];
  products: ProductResponse[];
  isLoading: boolean;
  cart: CartLine[];
  onAddProduct: (product: ProductResponse) => void;
  onIncrement: (productId: string) => void;
  onDecrement: (productId: string) => void;
  onRemove: (productId: string) => void;
  onCommit: () => void;
  isCommitting: boolean;
  /** La orden esta PAID o CANCELED: el backend rechaza agregar productos. */
  disabled: boolean;
  /** La orden ya esta SENT o READY: agregar sigue permitido, pero hay que
   * dejarlo claro para que el mesero no piense que reenvia a cocina solo. */
  alreadySentToKitchen: boolean;
}

/**
 * Selector de productos por categoria (tabs) mas el carrito local en
 * construccion. Tocar un producto lo agrega al carrito; nada llega al
 * servidor hasta que se confirma con un solo POST por lote.
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
  onCommit,
  isCommitting,
  disabled,
  alreadySentToKitchen,
}: ProductPickerProps) {
  if (isLoading) {
    return (
      <div className="flex flex-col gap-2">
        <Skeleton className="h-8 w-full" />
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-24 w-full" />
      </div>
    );
  }

  if (disabled) {
    return (
      <Alert variant="destructive">
        <AlertTitle>Esta orden ya esta cerrada</AlertTitle>
        <AlertDescription>
          No se pueden agregar mas productos a una orden pagada o cancelada.
        </AlertDescription>
      </Alert>
    );
  }

  const uncategorized = products.filter((product) => product.categoryId === null);
  const defaultTab = categories[0]?.id ?? (uncategorized.length > 0 ? NO_CATEGORY_TAB : undefined);
  const cartSubtotal = cart.reduce(
    (sum, line) => sum + line.product.price * line.quantity,
    0,
  );

  return (
    <div className="flex flex-col gap-4">
      {alreadySentToKitchen && (
        <Alert>
          <AlertTitle>Esta orden ya esta en cocina</AlertTitle>
          <AlertDescription>
            Lo que agregues ahora se sumara a la cuenta, pero no se reenviara
            solo: avisa a cocina si hace falta prepararlo de inmediato.
          </AlertDescription>
        </Alert>
      )}

      {defaultTab && (
        <Tabs defaultValue={defaultTab}>
          <TabsList>
            {categories.map((category) => (
              <TabsTrigger key={category.id} value={category.id}>
                {category.name}
              </TabsTrigger>
            ))}
            {uncategorized.length > 0 && (
              <TabsTrigger value={NO_CATEGORY_TAB}>Sin categoria</TabsTrigger>
            )}
          </TabsList>

          {categories.map((category) => (
            <TabsContent key={category.id} value={category.id}>
              <ProductGrid
                products={products.filter((product) => product.categoryId === category.id)}
                onAddProduct={onAddProduct}
              />
            </TabsContent>
          ))}

          {uncategorized.length > 0 && (
            <TabsContent value={NO_CATEGORY_TAB}>
              <ProductGrid products={uncategorized} onAddProduct={onAddProduct} />
            </TabsContent>
          )}
        </Tabs>
      )}

      <div className="flex flex-col gap-2">
        <h3 className="text-sm font-medium">Carrito</h3>
        {cart.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            El carrito esta vacio. Toca un producto para agregarlo.
          </p>
        ) : (
          <div className="flex flex-col gap-2">
            {cart.map((line) => (
              <div
                key={line.product.id}
                className="flex items-center justify-between gap-2 rounded-lg border p-2"
              >
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium">{line.product.name}</p>
                  <Money
                    amount={line.product.price * line.quantity}
                    className="text-xs text-muted-foreground"
                  />
                </div>
                <div className="flex items-center gap-1">
                  <Button
                    type="button"
                    variant="outline"
                    size="icon-sm"
                    onClick={() => onDecrement(line.product.id)}
                    aria-label="Quitar una unidad"
                  >
                    <MinusIcon />
                  </Button>
                  <span className="w-6 text-center text-sm">{line.quantity}</span>
                  <Button
                    type="button"
                    variant="outline"
                    size="icon-sm"
                    onClick={() => onIncrement(line.product.id)}
                    aria-label="Agregar una unidad"
                  >
                    <PlusIcon />
                  </Button>
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon-sm"
                    onClick={() => onRemove(line.product.id)}
                    aria-label="Quitar del carrito"
                  >
                    <XIcon />
                  </Button>
                </div>
              </div>
            ))}
            <div className="flex items-center justify-between border-t pt-2 text-sm">
              <span className="text-muted-foreground">Subtotal del carrito</span>
              <Money amount={cartSubtotal} className="font-medium" />
            </div>
          </div>
        )}

        <Button
          type="button"
          onClick={onCommit}
          disabled={cart.length === 0 || isCommitting}
        >
          {isCommitting ? "Agregando..." : "Agregar a la orden"}
        </Button>
      </div>
    </div>
  );
}

interface ProductGridProps {
  products: ProductResponse[];
  onAddProduct: (product: ProductResponse) => void;
}

function ProductGrid({ products, onAddProduct }: ProductGridProps) {
  if (products.length === 0) {
    return (
      <p className="py-4 text-sm text-muted-foreground">
        No hay productos en esta categoria.
      </p>
    );
  }

  return (
    <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
      {products.map((product) => (
        <Card
          key={product.id}
          onClick={() => onAddProduct(product)}
          className="cursor-pointer transition-colors hover:bg-muted/50"
        >
          <CardContent className="flex items-center justify-between gap-2">
            <div className="min-w-0">
              <p className="truncate text-sm font-medium">{product.name}</p>
              {product.description && (
                <p className="truncate text-xs text-muted-foreground">
                  {product.description}
                </p>
              )}
            </div>
            <Money amount={product.price} className="shrink-0 text-sm font-medium" />
          </CardContent>
        </Card>
      ))}
    </div>
  );
}
