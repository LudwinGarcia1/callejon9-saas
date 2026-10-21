"use client";

import { useState, useMemo } from "react";
import {
  BellRingIcon,
  CreditCardIcon,
  SearchIcon,
  PlusIcon,
  MinusIcon,
  ShoppingBagIcon,
  XIcon,
  FlameIcon,
  SparklesIcon,
  UtensilsCrossedIcon,
  ChevronRightIcon,
} from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

interface DinerMenuViewProps {
  slug: string;
  tableNumber: string;
}

interface MenuItem {
  id: string;
  categoryId: string;
  categoryName: string;
  name: string;
  description: string;
  price: number;
  popular?: boolean;
  spicy?: boolean;
  vegetarian?: boolean;
}

interface CartItem {
  item: MenuItem;
  quantity: number;
  notes?: string;
}

const DEFAULT_MENU_ITEMS: MenuItem[] = [
  // Entradas
  {
    id: "prod-1",
    categoryId: "cat-entradas",
    categoryName: "Entradas",
    name: "Guacamole Tradicional con Totopos",
    description: "Aguacate fresco machacado al momento con jitomate, cebolla morada, cilantro y chile serrano.",
    price: 95,
    popular: true,
    vegetarian: true,
  },
  {
    id: "prod-2",
    categoryId: "cat-entradas",
    categoryName: "Entradas",
    name: "Queso Fundido con Chistorra",
    description: "Mezcla de queso Oaxaca y Gouda fundido al comal con chistorra dorada. Acompañado de tortillas de harina.",
    price: 110,
    popular: true,
  },
  {
    id: "prod-3",
    categoryId: "cat-entradas",
    categoryName: "Entradas",
    name: "Sopa Azteca de Tortilla",
    description: "Caldo de jitomate sazonado con pasilla, tiras de tortilla frita, crema fresca, queso panela y aguacate.",
    price: 85,
  },

  // Platos Fuertes
  {
    id: "prod-4",
    categoryId: "cat-fuertes",
    categoryName: "Platos fuertes",
    name: "Arrachera Norteña 300g",
    description: "Corte premium a la parrilla con cebollitas cambray, chile toreado, frijoles charros y guacamole.",
    price: 320,
    popular: true,
  },
  {
    id: "prod-5",
    categoryId: "cat-fuertes",
    categoryName: "Platos fuertes",
    name: "Enchiladas de Mole Poblano",
    description: "Tres enchiladas rellenas de pollo deshebrado, bañadas en mole artesanal con ajonjolí tostado y crema.",
    price: 185,
    spicy: true,
  },
  {
    id: "prod-6",
    categoryId: "cat-fuertes",
    categoryName: "Platos fuertes",
    name: "Pescado a la Talla Estilo Barra Vieja",
    description: "Filete de robalo asado a las brasas con adobo tradicional de chiles secos y mayonesa con especias.",
    price: 290,
  },
  {
    id: "prod-7",
    categoryId: "cat-fuertes",
    categoryName: "Platos fuertes",
    name: "Chiles en Nogada Tradicionales",
    description: "Chile poblano relleno de picadillo dulce y salado, bañado en nogada fresca de nuez de Castilla y granada.",
    price: 265,
    popular: true,
  },

  // Bebidas
  {
    id: "prod-8",
    categoryId: "cat-bebidas",
    categoryName: "Bebidas",
    name: "Agua Fresca de Horchata Artesanal",
    description: "Receta de la casa con arroz remojado, canela en raja y leche condensada (500 ml).",
    price: 45,
    vegetarian: true,
  },
  {
    id: "prod-9",
    categoryId: "cat-bebidas",
    categoryName: "Bebidas",
    name: "Cerveza Artesanal IPA Mexicana",
    description: "Cerveza de cuerpo medio con notas cítricas y amargor balanceado (355 ml).",
    price: 85,
    popular: true,
  },
  {
    id: "prod-10",
    categoryId: "cat-bebidas",
    categoryName: "Bebidas",
    name: "Margarita Clásica de Tequila",
    description: "Tequila reposado 100% agave, licor de naranja, jugo de limón fresco y escarchado de sal de grano.",
    price: 135,
  },

  // Postres
  {
    id: "prod-11",
    categoryId: "cat-postres",
    categoryName: "Postres",
    name: "Flan Napolitano al Caramelo",
    description: "Cremoso flan con caramelo oscuro y un toque de vainilla de Papantla.",
    price: 75,
    vegetarian: true,
  },
  {
    id: "prod-12",
    categoryId: "cat-postres",
    categoryName: "Postres",
    name: "Pastel de Tres Leches Tradicional",
    description: "Bizcocho esponjoso bañado en tres leches con canela y fresa fresca.",
    price: 90,
    popular: true,
    vegetarian: true,
  },
];

export function DinerMenuView({ slug, tableNumber }: DinerMenuViewProps) {
  const [selectedCategory, setSelectedCategory] = useState<string>("ALL");
  const [searchQuery, setSearchQuery] = useState<string>("");
  const [cart, setCart] = useState<CartItem[]>([]);
  const [isCartOpen, setIsCartOpen] = useState<boolean>(false);
  const [activeOrder, setActiveOrder] = useState<{
    folio: string;
    items: CartItem[];
    createdAt: Date;
    status: "SENT" | "PREPARING" | "READY";
  } | null>(null);

  const formattedSlug = slug.replace(/-/g, " ");

  const categories = useMemo(() => {
    const map = new Map<string, string>();
    DEFAULT_MENU_ITEMS.forEach((item) => {
      if (!map.has(item.categoryId)) {
        map.set(item.categoryId, item.categoryName);
      }
    });
    return Array.from(map.entries()).map(([id, name]) => ({ id, name }));
  }, []);

  const filteredItems = useMemo(() => {
    return DEFAULT_MENU_ITEMS.filter((item) => {
      const matchesCategory =
        selectedCategory === "ALL" || item.categoryId === selectedCategory;
      const matchesQuery =
        item.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
        item.description.toLowerCase().includes(searchQuery.toLowerCase());
      return matchesCategory && matchesQuery;
    });
  }, [selectedCategory, searchQuery]);

  const cartTotalAmount = useMemo(() => {
    return cart.reduce((acc, line) => acc + line.item.price * line.quantity, 0);
  }, [cart]);

  const cartTotalItems = useMemo(() => {
    return cart.reduce((acc, line) => acc + line.quantity, 0);
  }, [cart]);

  const handleAddToCart = (item: MenuItem) => {
    setCart((prev) => {
      const existing = prev.find((line) => line.item.id === item.id);
      if (existing) {
        return prev.map((line) =>
          line.item.id === item.id
            ? { ...line, quantity: line.quantity + 1 }
            : line
        );
      }
      return [...prev, { item, quantity: 1 }];
    });
    toast.success(`Agregaste 1x ${item.name}`);
  };

  const handleDecrement = (itemId: string) => {
    setCart((prev) => {
      const existing = prev.find((line) => line.item.id === itemId);
      if (!existing) return prev;
      if (existing.quantity <= 1) {
        return prev.filter((line) => line.item.id !== itemId);
      }
      return prev.map((line) =>
        line.item.id === itemId
          ? { ...line, quantity: line.quantity - 1 }
          : line
      );
    });
  };

  const handleUpdateNotes = (itemId: string, notes: string) => {
    setCart((prev) =>
      prev.map((line) =>
        line.item.id === itemId ? { ...line, notes } : line
      )
    );
  };

  const handleSendOrder = () => {
    if (cart.length === 0) return;
    const randomFolio = `FOL-M${tableNumber}-${Math.floor(1000 + Math.random() * 9000)}`;
    setActiveOrder({
      folio: randomFolio,
      items: [...cart],
      createdAt: new Date(),
      status: "SENT",
    });
    setCart([]);
    setIsCartOpen(false);
    toast.success("¡Tu comanda fue enviada a cocina con éxito!", {
      description: `Folio asignado: ${randomFolio}`,
    });
  };

  const handleCallWaiter = () => {
    toast.info("🛎️ Mesero notificado", {
      description: `Un mesero se dirige a la Mesa ${tableNumber}.`,
    });
  };

  const handleAskBill = () => {
    toast.info("💳 Solicitud de cuenta enviada", {
      description: `Caja y mesero preparan el ticket de la Mesa ${tableNumber}.`,
    });
  };

  return (
    <div className="min-h-screen bg-neutral-950 text-neutral-100 flex flex-col pb-24 max-w-lg mx-auto shadow-2xl">
      {/* HEADER SUPERIOR */}
      <header className="sticky top-0 z-30 bg-neutral-900/95 backdrop-blur-md border-b border-neutral-800 px-4 py-3">
        <div className="flex items-center justify-between">
          <div>
            <div className="flex items-center gap-2">
              <span className="text-xs uppercase font-bold tracking-wider text-amber-500">
                RestoHub
              </span>
              <span className="text-neutral-600 text-xs">•</span>
              <Badge className="bg-emerald-500/10 text-emerald-400 border-emerald-500/20 font-bold px-2 py-0.5 text-xs">
                Mesa {tableNumber}
              </Badge>
            </div>
            <h1 className="text-lg font-bold capitalize text-neutral-100 leading-tight truncate">
              {formattedSlug}
            </h1>
          </div>

          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={handleCallWaiter}
              className="h-8 px-2.5 bg-neutral-800/80 border-neutral-700 text-xs text-neutral-300 hover:text-amber-400 hover:border-amber-500 active:scale-95 transition-all"
            >
              <BellRingIcon className="w-3.5 h-3.5 mr-1 text-amber-400" />
              Mesero
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={handleAskBill}
              className="h-8 px-2.5 bg-neutral-800/80 border-neutral-700 text-xs text-neutral-300 hover:text-emerald-400 hover:border-emerald-500 active:scale-95 transition-all"
            >
              <CreditCardIcon className="w-3.5 h-3.5 mr-1 text-emerald-400" />
              Cuenta
            </Button>
          </div>
        </div>

        {/* BUSCADOR */}
        <div className="mt-3 relative">
          <SearchIcon className="w-4 h-4 text-neutral-400 absolute left-3 top-1/2 -translate-y-1/2" />
          <Input
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Buscar platillos, bebidas, postres..."
            className="pl-9 bg-neutral-900 border-neutral-800 text-xs text-neutral-100 placeholder:text-neutral-500 h-9 rounded-xl focus:border-amber-500"
          />
        </div>

        {/* CHIPS DE CATEGORÍA */}
        <div className="mt-3 flex gap-2 overflow-x-auto no-scrollbar pb-1">
          <button
            onClick={() => setSelectedCategory("ALL")}
            className={cn(
              "px-3 py-1.5 rounded-full text-xs font-medium whitespace-nowrap transition-all",
              selectedCategory === "ALL"
                ? "bg-amber-500 text-neutral-950 font-bold shadow-md shadow-amber-500/20"
                : "bg-neutral-800 text-neutral-400 hover:text-neutral-200"
            )}
          >
            Todos ({DEFAULT_MENU_ITEMS.length})
          </button>
          {categories.map((cat) => {
            const count = DEFAULT_MENU_ITEMS.filter(
              (i) => i.categoryId === cat.id
            ).length;
            const isSelected = selectedCategory === cat.id;
            return (
              <button
                key={cat.id}
                onClick={() => setSelectedCategory(cat.id)}
                className={cn(
                  "px-3 py-1.5 rounded-full text-xs font-medium whitespace-nowrap transition-all",
                  isSelected
                    ? "bg-amber-500 text-neutral-950 font-bold shadow-md shadow-amber-500/20"
                    : "bg-neutral-800 text-neutral-400 hover:text-neutral-200"
                )}
              >
                {cat.name} ({count})
              </button>
            );
          })}
        </div>
      </header>

      {/* ESTADO DE ORDEN ACTIVA EN COCINA */}
      {activeOrder && (
        <div className="mx-4 mt-4 p-4 rounded-2xl bg-gradient-to-r from-amber-500/15 via-emerald-500/10 to-neutral-900 border border-amber-500/30">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="flex h-2.5 w-2.5 relative">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-emerald-500"></span>
              </span>
              <span className="text-xs font-bold text-amber-400 uppercase tracking-wide">
                Comanda Activa
              </span>
            </div>
            <span className="text-xs font-mono font-bold text-neutral-400">
              {activeOrder.folio}
            </span>
          </div>

          <p className="text-sm font-semibold text-neutral-200 mt-1">
            Tu pedido está siendo preparado por cocina
          </p>
          <p className="text-xs text-neutral-400 mt-0.5">
            {activeOrder.items.length} productos ordenados a la Mesa {tableNumber}.
          </p>
        </div>
      )}

      {/* LISTADO DE PLATILLOS */}
      <main className="p-4 space-y-3">
        {filteredItems.length === 0 ? (
          <div className="text-center py-12 text-neutral-500">
            <UtensilsCrossedIcon className="w-10 h-10 mx-auto text-neutral-600 mb-2" />
            <p className="text-sm">No encontramos platillos con ese criterio.</p>
          </div>
        ) : (
          filteredItems.map((item) => {
            const inCart = cart.find((l) => l.item.id === item.id);
            const qty = inCart ? inCart.quantity : 0;

            return (
              <div
                key={item.id}
                className="p-3.5 rounded-2xl bg-neutral-900/70 border border-neutral-800/80 hover:border-neutral-700 transition-all flex flex-col justify-between"
              >
                <div>
                  <div className="flex items-start justify-between gap-2">
                    <div className="flex-1">
                      <div className="flex items-center gap-1.5 flex-wrap">
                        <h3 className="text-sm font-bold text-neutral-100">
                          {item.name}
                        </h3>
                        {item.popular && (
                          <Badge className="bg-amber-500/20 text-amber-400 border-none px-1.5 py-0 text-[10px] font-semibold flex items-center gap-1">
                            <SparklesIcon className="w-2.5 h-2.5" /> Popular
                          </Badge>
                        )}
                        {item.spicy && (
                          <Badge className="bg-red-500/20 text-red-400 border-none px-1.5 py-0 text-[10px] font-semibold flex items-center gap-1">
                            <FlameIcon className="w-2.5 h-2.5" /> Picante
                          </Badge>
                        )}
                      </div>
                      <p className="text-xs text-neutral-400 mt-1 line-clamp-2 leading-relaxed">
                        {item.description}
                      </p>
                    </div>

                    <div className="text-right">
                      <span className="text-sm font-extrabold text-amber-400">
                        ${item.price.toFixed(2)}
                      </span>
                    </div>
                  </div>
                </div>

                <div className="mt-3 pt-2.5 border-t border-neutral-800/60 flex items-center justify-between">
                  <span className="text-[11px] text-neutral-500">
                    IVA incluido
                  </span>

                  {qty === 0 ? (
                    <Button
                      size="sm"
                      onClick={() => handleAddToCart(item)}
                      className="h-8 px-3 rounded-xl bg-amber-500 hover:bg-amber-400 text-neutral-950 font-bold text-xs active:scale-95 transition-all"
                    >
                      <PlusIcon className="w-3.5 h-3.5 mr-1" /> Agregar
                    </Button>
                  ) : (
                    <div className="flex items-center gap-2 bg-neutral-800/80 p-1 rounded-xl border border-neutral-700">
                      <button
                        onClick={() => handleDecrement(item.id)}
                        className="w-6 h-6 flex items-center justify-center rounded-lg bg-neutral-700 text-neutral-100 active:scale-90"
                      >
                        <MinusIcon className="w-3 h-3" />
                      </button>
                      <span className="text-xs font-bold text-neutral-100 px-1">
                        {qty}
                      </span>
                      <button
                        onClick={() => handleAddToCart(item)}
                        className="w-6 h-6 flex items-center justify-center rounded-lg bg-amber-500 text-neutral-950 active:scale-90 font-bold"
                      >
                        <PlusIcon className="w-3 h-3" />
                      </button>
                    </div>
                  )}
                </div>
              </div>
            );
          })
        )}
      </main>

      {/* BARRA FLOTANTE DE COMANDA */}
      {cartTotalItems > 0 && (
        <div className="fixed bottom-0 left-0 right-0 p-4 max-w-lg mx-auto z-40 bg-gradient-to-t from-neutral-950 via-neutral-950/90 to-transparent">
          <button
            onClick={() => setIsCartOpen(true)}
            className="w-full bg-gradient-to-r from-amber-500 to-amber-600 hover:from-amber-400 hover:to-amber-500 text-neutral-950 font-extrabold p-3.5 rounded-2xl shadow-xl shadow-amber-500/25 flex items-center justify-between active:scale-[0.98] transition-all"
          >
            <div className="flex items-center gap-2">
              <span className="flex items-center justify-center w-7 h-7 rounded-full bg-neutral-950 text-amber-400 text-xs font-black">
                {cartTotalItems}
              </span>
              <span className="text-sm font-bold text-neutral-950">
                Ver comanda de la Mesa {tableNumber}
              </span>
            </div>

            <div className="flex items-center gap-1.5 text-sm">
              <span>${cartTotalAmount.toFixed(2)} MXN</span>
              <ChevronRightIcon className="w-4 h-4" />
            </div>
          </button>
        </div>
      )}

      {/* MODAL / DRAWER DE REVISIÓN DE COMANDA */}
      {isCartOpen && (
        <div className="fixed inset-0 z-50 bg-neutral-950/80 backdrop-blur-sm flex flex-col justify-end">
          <div className="bg-neutral-900 border-t border-neutral-800 rounded-t-3xl p-5 max-w-lg w-full mx-auto max-h-[85vh] flex flex-col shadow-2xl animate-in slide-in-from-bottom duration-200">
            <div className="flex items-center justify-between pb-3 border-b border-neutral-800">
              <div>
                <h2 className="text-base font-bold text-neutral-100 flex items-center gap-2">
                  <ShoppingBagIcon className="w-4 h-4 text-amber-400" />
                  Comanda de Mesa {tableNumber}
                </h2>
                <p className="text-xs text-neutral-400">
                  Revisa tus platillos antes de mandarlos a preparación
                </p>
              </div>
              <button
                onClick={() => setIsCartOpen(false)}
                className="w-8 h-8 rounded-full bg-neutral-800 flex items-center justify-center text-neutral-400 hover:text-neutral-100"
              >
                <XIcon className="w-4 h-4" />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto py-3 space-y-3">
              {cart.map((line) => (
                <div
                  key={line.item.id}
                  className="p-3 rounded-xl bg-neutral-950/60 border border-neutral-800"
                >
                  <div className="flex items-center justify-between">
                    <div>
                      <h4 className="text-xs font-bold text-neutral-200">
                        {line.item.name}
                      </h4>
                      <span className="text-xs font-extrabold text-amber-400">
                        ${(line.item.price * line.quantity).toFixed(2)}
                      </span>
                    </div>

                    <div className="flex items-center gap-2 bg-neutral-900 p-1 rounded-xl border border-neutral-800">
                      <button
                        onClick={() => handleDecrement(line.item.id)}
                        className="w-6 h-6 flex items-center justify-center rounded-lg bg-neutral-800 text-neutral-200 active:scale-90"
                      >
                        <MinusIcon className="w-3 h-3" />
                      </button>
                      <span className="text-xs font-bold px-1.5">
                        {line.quantity}
                      </span>
                      <button
                        onClick={() => handleAddToCart(line.item)}
                        className="w-6 h-6 flex items-center justify-center rounded-lg bg-amber-500 text-neutral-950 font-bold active:scale-90"
                      >
                        <PlusIcon className="w-3 h-3" />
                      </button>
                    </div>
                  </div>

                  <div className="mt-2">
                    <input
                      type="text"
                      placeholder="Notas para cocina (ej. sin cebolla, bien cocido)..."
                      value={line.notes || ""}
                      onChange={(e) =>
                        handleUpdateNotes(line.item.id, e.target.value)
                      }
                      className="w-full text-[11px] bg-neutral-900 border border-neutral-800 rounded-lg px-2.5 py-1.5 text-neutral-300 placeholder:text-neutral-600 focus:outline-none focus:border-amber-500"
                    />
                  </div>
                </div>
              ))}
            </div>

            <div className="pt-3 border-t border-neutral-800 space-y-3">
              <div className="flex items-center justify-between text-sm">
                <span className="text-neutral-400 font-medium">Total de la orden:</span>
                <span className="text-lg font-black text-amber-400">
                  ${cartTotalAmount.toFixed(2)} MXN
                </span>
              </div>

              <Button
                onClick={handleSendOrder}
                className="w-full h-12 bg-amber-500 hover:bg-amber-400 text-neutral-950 font-extrabold text-sm rounded-xl shadow-lg shadow-amber-500/20 active:scale-95 transition-all"
              >
                🍳 Enviar Pedido a Cocina
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
