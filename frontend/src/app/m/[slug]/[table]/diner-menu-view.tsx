"use client";

import { useState, useMemo, useEffect } from "react";
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
import { MercadoPagoQrModal } from "@/components/shared/mercadopago-qr-modal";
import { ApiError, api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";
import type { KitchenItemStatus, OrderResponse, OrderStatus } from "@/lib/types";
import { cn } from "@/lib/utils";

export interface DinerMenuViewProps {
  slug: string;
  tableNumber: string;
  initialMenuItems?: MenuItem[];
}

export interface MenuItem {
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

export interface CartItem {
  item: MenuItem;
  quantity: number;
  notes?: string;
  kitchenStatus?: KitchenItemStatus;
}

interface PublicCatalogApiResponse {
  restaurantName: string;
  slug: string;
  categories: { id: string; name: string }[];
  products: { id: string; name: string; description: string; price: number; categoryId: string }[];
}

export const DEFAULT_MENU_ITEMS: MenuItem[] = [
  // Entradas
  {
    id: "00000000-0000-0000-0000-000000000001",
    categoryId: "cat-entradas",
    categoryName: "Entradas",
    name: "Guacamole Tradicional con Totopos",
    description: "Aguacate fresco machacado al momento con jitomate, cebolla morada, cilantro y chile serrano.",
    price: 95,
    popular: true,
    vegetarian: true,
  },
  {
    id: "00000000-0000-0000-0000-000000000002",
    categoryId: "cat-entradas",
    categoryName: "Entradas",
    name: "Queso Fundido con Chistorra",
    description: "Mezcla de queso Oaxaca y Gouda fundido al comal con chistorra dorada. Acompañado de tortillas de harina.",
    price: 110,
    popular: true,
  },
  {
    id: "00000000-0000-0000-0000-000000000003",
    categoryId: "cat-entradas",
    categoryName: "Entradas",
    name: "Sopa Azteca de Tortilla",
    description: "Caldo de jitomate sazonado con pasilla, tiras de tortilla frita, crema fresca, queso panela y aguacate.",
    price: 85,
  },

  // Platos Fuertes
  {
    id: "00000000-0000-0000-0000-000000000004",
    categoryId: "cat-fuertes",
    categoryName: "Platos fuertes",
    name: "Arrachera Norteña 300g",
    description: "Corte premium a la parrilla con cebollitas cambray, chile toreado, frijoles charros y guacamole.",
    price: 320,
    popular: true,
  },
  {
    id: "00000000-0000-0000-0000-000000000005",
    categoryId: "cat-fuertes",
    categoryName: "Platos fuertes",
    name: "Enchiladas de Mole Poblano",
    description: "Tres enchiladas rellenas de pollo deshebrado, bañadas en mole artesanal con ajonjolí tostado y crema.",
    price: 185,
    spicy: true,
  },
  {
    id: "00000000-0000-0000-0000-000000000006",
    categoryId: "cat-fuertes",
    categoryName: "Platos fuertes",
    name: "Pescado a la Talla Estilo Barra Vieja",
    description: "Filete de robalo asado a las brasas con adobo tradicional de chiles secos y mayonesa con especias.",
    price: 290,
  },
  {
    id: "00000000-0000-0000-0000-000000000007",
    categoryId: "cat-fuertes",
    categoryName: "Platos fuertes",
    name: "Chiles en Nogada Tradicionales",
    description: "Chile poblano relleno de picadillo dulce y salado, bañado en nogada fresca de nuez de Castilla y granada.",
    price: 265,
    popular: true,
  },

  // Bebidas
  {
    id: "00000000-0000-0000-0000-000000000008",
    categoryId: "cat-bebidas",
    categoryName: "Bebidas",
    name: "Agua Fresca de Horchata Artesanal",
    description: "Receta de la casa con arroz remojado, canela en raja y leche condensada (500 ml).",
    price: 45,
    vegetarian: true,
  },
  {
    id: "00000000-0000-0000-0000-000000000009",
    categoryId: "cat-bebidas",
    categoryName: "Bebidas",
    name: "Cerveza Artesanal IPA Mexicana",
    description: "Cerveza de cuerpo medio con notas cítricas y amargor balanceado (355 ml).",
    price: 85,
    popular: true,
  },
  {
    id: "00000000-0000-0000-0000-000000000010",
    categoryId: "cat-bebidas",
    categoryName: "Bebidas",
    name: "Margarita Clásica de Tequila",
    description: "Tequila reposado 100% agave, licor de naranja, jugo de limón fresco y escarchado de sal de grano.",
    price: 135,
  },

  // Postres
  {
    id: "00000000-0000-0000-0000-000000000011",
    categoryId: "cat-postres",
    categoryName: "Postres",
    name: "Flan Napolitano al Caramelo",
    description: "Cremoso flan con caramelo oscuro y un toque de vainilla de Papantla.",
    price: 75,
    vegetarian: true,
  },
  {
    id: "00000000-0000-0000-0000-000000000012",
    categoryId: "cat-postres",
    categoryName: "Postres",
    name: "Pastel de Tres Leches Tradicional",
    description: "Bizcocho esponjoso bañado en tres leches con canela y fresa fresca.",
    price: 90,
    popular: true,
    vegetarian: true,
  },
];

export function DinerMenuView({ slug, tableNumber, initialMenuItems }: DinerMenuViewProps) {
  const [menuItems, setMenuItems] = useState<MenuItem[]>(() => initialMenuItems ?? []);
  const [isLoadingCatalog, setIsLoadingCatalog] = useState<boolean>(
    () => !initialMenuItems || initialMenuItems.length === 0,
  );
  const [restaurantTitle, setRestaurantTitle] = useState<string>(slug.replace(/-/g, " "));
  const [selectedCategory, setSelectedCategory] = useState<string>("ALL");
  const [searchQuery, setSearchQuery] = useState<string>("");
  const [cart, setCart] = useState<CartItem[]>([]);
  const [isCartOpen, setIsCartOpen] = useState<boolean>(false);
  const [activeOrder, setActiveOrder] = useState<{
    folio: string;
    items: CartItem[];
    createdAt: Date;
    status: OrderStatus;
  } | null>(null);
  const [isBillOpen, setIsBillOpen] = useState<boolean>(false);
  const [isMpModalOpen, setIsMpModalOpen] = useState<boolean>(false);
  const [billTipPercent, setBillTipPercent] = useState<number>(10);
  const [loadingBill, setLoadingBill] = useState<boolean>(false);
  const [tableOrderData, setTableOrderData] = useState<OrderResponse | null>(null);

  // Carga inicial del catálogo desde la API pública
  useEffect(() => {
    let active = true;
    async function loadCatalog() {
      try {
        if (!initialMenuItems || initialMenuItems.length === 0) {
          setIsLoadingCatalog(true);
        }
        const data = await api.get<PublicCatalogApiResponse>(endpoints.publicMenu.catalog(slug));
        if (active && data) {
          if (data.restaurantName) setRestaurantTitle(data.restaurantName);
          const catMap = new Map((data.categories || []).map((c) => [c.id, c.name]));
          const mapped: MenuItem[] = (data.products || []).map((p) => ({
            id: p.id,
            categoryId: p.categoryId,
            categoryName: catMap.get(p.categoryId) || "General",
            name: p.name,
            description: p.description || "",
            price: Number(p.price),
          }));
          setMenuItems(mapped);
        }
      } catch {
        // En caso de error o restaurante sin menú, deja el catálogo vacío (sin productos falsos)
        if (active && (!initialMenuItems || initialMenuItems.length === 0)) {
          setMenuItems([]);
        }
      } finally {
        if (active) {
          setIsLoadingCatalog(false);
        }
      }
    }
    loadCatalog();
    return () => {
      active = false;
    };
  }, [slug, initialMenuItems]);

  // Sincronización periódica de la comanda activa de la mesa con el backend
  useEffect(() => {
    let active = true;
    async function syncActiveOrder() {
      try {
        const order = await api.get<OrderResponse>(endpoints.publicMenu.tableOrder(slug, tableNumber));
        if (!active) return;
        // Solo consideramos comanda activa si tiene platillos y ya fue enviada o está lista
        if (
          order &&
          order.items &&
          order.items.length > 0 &&
          (order.status === "SENT" || order.status === "READY")
        ) {
          setActiveOrder({
            folio: order.folio,
            items: order.items.map((it) => ({
              item: {
                id: it.productId ?? it.id,
                name: it.productName,
                price: it.unitPrice,
                description: "",
                categoryId: "",
                categoryName: "",
              },
              quantity: it.quantity,
              notes: it.notes ?? undefined,
              kitchenStatus: it.kitchenStatus,
            })),
            createdAt: new Date(order.openedAt),
            status: order.status,
          });
          setTableOrderData(order);
        } else if (!order || order.status === "PAID" || order.status === "CANCELED" || (order.items && order.items.length === 0)) {
          setActiveOrder(null);
          setTableOrderData(order && order.items && order.items.length > 0 ? order : null);
        }
      } catch {
        // En caso de que no haya orden abierta en la mesa o error de red, no rompe
      }
    }

    syncActiveOrder();
    const intervalId = setInterval(syncActiveOrder, 5000);
    return () => {
      active = false;
      clearInterval(intervalId);
    };
  }, [slug, tableNumber]);

  const categories = useMemo(() => {
    const map = new Map<string, string>();
    menuItems.forEach((item) => {
      if (!map.has(item.categoryId)) {
        map.set(item.categoryId, item.categoryName);
      }
    });
    return Array.from(map.entries()).map(([id, name]) => ({ id, name }));
  }, [menuItems]);

  const filteredItems = useMemo(() => {
    return menuItems.filter((item) => {
      const matchesCategory =
        selectedCategory === "ALL" || item.categoryId === selectedCategory;
      const matchesQuery =
        item.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
        item.description.toLowerCase().includes(searchQuery.toLowerCase());
      return matchesCategory && matchesQuery;
    });
  }, [menuItems, selectedCategory, searchQuery]);

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

  const handleSendOrder = async () => {
    if (cart.length === 0) return;
    try {
      const payload = {
        items: cart.map((line) => ({
          productId: line.item.id,
          quantity: line.quantity,
          notes: line.notes || null,
        })),
      };
      const response = await api.post<{ folio: string; status: OrderStatus }>(
        endpoints.publicMenu.sendOrder(slug, tableNumber),
        payload
      );
      setActiveOrder({
        folio: response.folio,
        items: cart.map((line) => ({
          ...line,
          kitchenStatus: "PENDING" as KitchenItemStatus,
        })),
        createdAt: new Date(),
        status: response.status || "SENT",
      });
      setCart([]);
      setIsCartOpen(false);
      toast.success("¡Tu comanda fue enviada a cocina con éxito!", {
        description: `Folio asignado: ${response.folio}`,
      });
    } catch (err) {
      toast.error(
        err instanceof Error
          ? err.message
          : "No se pudo enviar la orden a cocina. Inténtalo de nuevo."
      );
    }
  };

  // Confirma solo cuando el servidor recibio la llamada: si falla, el
  // comensal debe saberlo para volver a intentar o llamar en persona.
  const handleCallWaiter = async () => {
    try {
      await api.post(endpoints.publicMenu.callWaiter(slug, tableNumber), {});
      toast.info("🛎️ Mesero notificado", {
        description: `Avisamos al personal: un mesero irá a la Mesa ${tableNumber}.`,
      });
    } catch (error) {
      toast.error(
        error instanceof ApiError ? error.message : "No se pudo avisar al mesero. Inténtalo de nuevo.",
      );
    }
  };

  const handleAskBill = async () => {
    toast.info("💳 Solicitud de cuenta enviada", {
      description: `Caja y mesero preparan el ticket de la Mesa ${tableNumber}.`,
    });
    setIsBillOpen(true);
    setLoadingBill(true);
    try {
      const order = await api.get<OrderResponse>(endpoints.publicMenu.tableOrder(slug, tableNumber));
      setTableOrderData(order);
    } catch {
      if (activeOrder) {
        setTableOrderData({
          id: "00000000-0000-0000-0000-000000000000",
          folio: activeOrder.folio,
          tableId: null,
          waiterId: null,
          guestCount: 1,
          status: activeOrder.status,
          total: activeOrder.items.reduce((acc, it) => acc + it.item.price * it.quantity, 0),
          openedAt: activeOrder.createdAt.toISOString(),
          sentToKitchenAt: activeOrder.createdAt.toISOString(),
          closedAt: null,
          items: activeOrder.items.map((it, idx) => ({
            id: `item-${idx}`,
            productId: it.item.id,
            productName: it.item.name,
            unitPrice: it.item.price,
            quantity: it.quantity,
            kitchenStatus: "PENDING",
            notes: it.notes || null,
            orderedAt: activeOrder.createdAt.toISOString(),
          })),
        });
      } else {
        setTableOrderData(null);
      }
    } finally {
      setLoadingBill(false);
    }
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
              {restaurantTitle}
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
            Todos ({menuItems.length})
          </button>
          {categories.map((cat) => {
            const count = menuItems.filter(
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
      {activeOrder && activeOrder.items.length > 0 && (activeOrder.status === "SENT" || activeOrder.status === "READY") && (() => {
        const allDelivered = activeOrder.items.every((it) => it.kitchenStatus === "DELIVERED");
        const isReady = activeOrder.status === "READY" && !allDelivered;

        return (
          <div
            className={cn(
              "mx-4 mt-4 p-4 rounded-2xl border transition-all",
              allDelivered
                ? "bg-gradient-to-r from-emerald-500/20 via-teal-600/10 to-neutral-900 border-emerald-500/40"
                : isReady
                ? "bg-gradient-to-r from-emerald-500/20 via-emerald-600/10 to-neutral-900 border-emerald-500/40"
                : "bg-gradient-to-r from-amber-500/15 via-emerald-500/10 to-neutral-900 border-amber-500/30",
            )}
          >
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="flex h-2.5 w-2.5 relative">
                  <span
                    className={cn(
                      "animate-ping absolute inline-flex h-full w-full rounded-full opacity-75",
                      allDelivered
                        ? "hidden"
                        : isReady
                        ? "bg-emerald-400"
                        : "bg-amber-400",
                    )}
                  ></span>
                  <span
                    className={cn(
                      "relative inline-flex rounded-full h-2.5 w-2.5",
                      allDelivered
                        ? "bg-emerald-500"
                        : isReady
                        ? "bg-emerald-500"
                        : "bg-amber-500",
                    )}
                  ></span>
                </span>
                <span
                  className={cn(
                    "text-xs font-bold uppercase tracking-wide",
                    allDelivered
                      ? "text-emerald-400"
                      : isReady
                      ? "text-emerald-400"
                      : "text-amber-400",
                  )}
                >
                  {allDelivered
                    ? "¡Pedido Entregado!"
                    : isReady
                    ? "¡Pedido Listo!"
                    : "En Preparación"}
                </span>
              </div>
              <span className="text-xs font-mono font-bold text-neutral-400">
                {activeOrder.folio}
              </span>
            </div>

            <p className="text-sm font-semibold text-neutral-200 mt-1.5 flex items-center gap-1.5">
              {allDelivered ? (
                <>
                  <span>✅</span>
                  <span>¡Buen provecho! Tu pedido ya fue entregado en tu mesa.</span>
                </>
              ) : isReady ? (
                <>
                  <span>🍽️</span>
                  <span>¡Tu pedido está listo! El mesero lo está llevando a tu mesa.</span>
                </>
              ) : (
                <>
                  <span>🍳</span>
                  <span>Tu pedido está siendo preparado por cocina</span>
                </>
              )}
            </p>
            <p className="text-xs text-neutral-400 mt-0.5">
              {allDelivered
                ? "Si deseas ordenar algo más o pedir tu cuenta, puedes hacerlo desde aquí."
                : `${activeOrder.items.length} ${
                    activeOrder.items.length === 1 ? "producto ordenado" : "productos ordenados"
                  } a la Mesa ${tableNumber}.`}
            </p>
          </div>
        );
      })()}

      {/* LISTADO DE PLATILLOS */}
      <main className="p-4 space-y-3">
        {isLoadingCatalog ? (
          <div className="space-y-3">
            {[1, 2, 3].map((n) => (
              <div
                key={n}
                className="p-3.5 rounded-2xl bg-neutral-900/40 border border-neutral-800/50 animate-pulse space-y-2.5"
              >
                <div className="h-4 bg-neutral-800 rounded w-1/3" />
                <div className="h-3 bg-neutral-800/60 rounded w-2/3" />
                <div className="h-5 bg-neutral-800/40 rounded w-16" />
              </div>
            ))}
          </div>
        ) : filteredItems.length === 0 ? (
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

      {/* MODAL / DRAWER DE CUENTA Y PAGO MERCADO PAGO */}
      {isBillOpen && (
        <div className="fixed inset-0 z-50 bg-neutral-950/80 backdrop-blur-sm flex flex-col justify-end">
          <div className="bg-neutral-900 border-t border-neutral-800 rounded-t-3xl p-5 max-w-lg w-full mx-auto max-h-[85vh] flex flex-col shadow-2xl animate-in slide-in-from-bottom duration-200">
            <div className="flex items-center justify-between pb-3 border-b border-neutral-800">
              <div>
                <h2 className="text-base font-bold text-neutral-100 flex items-center gap-2">
                  <CreditCardIcon className="w-4 h-4 text-[#009ee3]" />
                  Cuenta de Mesa {tableNumber}
                </h2>
                <p className="text-xs text-neutral-400">
                  {tableOrderData ? `Comanda ${tableOrderData.folio}` : "Revisión de consumo"}
                </p>
              </div>
              <button
                onClick={() => setIsBillOpen(false)}
                className="w-8 h-8 rounded-full bg-neutral-800 flex items-center justify-center text-neutral-400 hover:text-neutral-100"
              >
                <XIcon className="w-4 h-4" />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto py-3 space-y-3">
              {loadingBill ? (
                <div className="py-12 text-center text-sm text-neutral-400">
                  Consultando cuenta de la mesa...
                </div>
              ) : !tableOrderData || tableOrderData.items.length === 0 ? (
                <div className="py-10 text-center space-y-2">
                  <p className="text-sm text-neutral-300 font-semibold">
                    No hay comanda activa para esta mesa
                  </p>
                  <p className="text-xs text-neutral-500 max-w-xs mx-auto">
                    Selecciona platillos del menú y envíalos a cocina para iniciar tu cuenta.
                  </p>
                </div>
              ) : (
                <>
                  <div className="space-y-2">
                    {tableOrderData.items.map((item) => (
                      <div
                        key={item.id}
                        className="flex items-center justify-between p-2.5 rounded-xl bg-neutral-950/60 border border-neutral-800/80 text-xs"
                      >
                        <div>
                          <p className="font-bold text-neutral-200">
                            {item.quantity}× {item.productName}
                          </p>
                          {item.notes && (
                            <p className="text-[10px] text-neutral-400 italic">
                              Nota: {item.notes}
                            </p>
                          )}
                        </div>
                        <span className="font-extrabold text-neutral-100">
                          ${(item.unitPrice * item.quantity).toFixed(2)}
                        </span>
                      </div>
                    ))}
                  </div>

                  {/* SELECCIÓN DE PROPINA */}
                  <div className="pt-2">
                    <p className="text-xs text-neutral-400 mb-1.5 font-medium">
                      Agregar propina voluntaria:
                    </p>
                    <div className="grid grid-cols-4 gap-1.5">
                      {[0, 10, 15, 20].map((pct) => (
                        <button
                          key={pct}
                          onClick={() => setBillTipPercent(pct)}
                          className={cn(
                            "py-2 rounded-xl text-xs font-bold border transition-all",
                            billTipPercent === pct
                              ? "bg-[#009ee3]/10 border-[#009ee3] text-[#009ee3]"
                              : "bg-neutral-950 border-neutral-800 text-neutral-400 hover:border-neutral-700"
                          )}
                        >
                          {pct}%
                        </button>
                      ))}
                    </div>
                  </div>
                </>
              )}
            </div>

            {tableOrderData && tableOrderData.items.length > 0 && (
              <div className="pt-3 border-t border-neutral-800 space-y-3">
                <div className="space-y-1 text-xs">
                  <div className="flex justify-between text-neutral-400">
                    <span>Subtotal:</span>
                    <span>${tableOrderData.total.toFixed(2)} MXN</span>
                  </div>
                  <div className="flex justify-between text-neutral-400">
                    <span>Propina ({billTipPercent}%):</span>
                    <span>
                      ${((tableOrderData.total * billTipPercent) / 100).toFixed(2)} MXN
                    </span>
                  </div>
                  <div className="flex justify-between text-sm font-bold text-neutral-100 pt-1 border-t border-neutral-800/60">
                    <span>Total con propina:</span>
                    <span className="text-[#009ee3] font-black">
                      ${(tableOrderData.total * (1 + billTipPercent / 100)).toFixed(2)} MXN
                    </span>
                  </div>
                </div>

                <Button
                  onClick={() => setIsMpModalOpen(true)}
                  className="w-full h-12 bg-[#009ee3] hover:bg-[#009ee3]/90 text-white font-extrabold text-sm rounded-xl shadow-lg shadow-[#009ee3]/20 active:scale-95 transition-all gap-2"
                >
                  <CreditCardIcon className="w-4 h-4" />
                  Pagar con Mercado Pago
                </Button>
              </div>
            )}
          </div>
        </div>
      )}

      {/* MODAL DE QR MERCADO PAGO */}
      <MercadoPagoQrModal
        orderId={tableOrderData?.id || ""}
        tipPercent={billTipPercent}
        isOpen={isMpModalOpen}
        onClose={() => setIsMpModalOpen(false)}
        onPaid={() => {
          setActiveOrder(null);
          setTableOrderData(null);
          setIsBillOpen(false);
          toast.success("¡Tu comanda fue cobrada con Mercado Pago con éxito!");
        }}
        publicSlug={slug}
        publicTableNumber={tableNumber}
      />
    </div>
  );
}
