export interface MenuItemRef {
  id: string;
  categoryId: string;
  categoryName: string;
  name: string;
  description: string;
  price: number;
  popular?: boolean;
  vegetarian?: boolean;
  spicy?: boolean;
}

function normalize(text: string): string {
  return text
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .trim();
}

/**
 * Determina qué tipo de alimento es para hacer maridajes inteligentes.
 */
export function classifyFoodType(name: string, categoryName: string = ""): "COFFEE" | "DRINK_COLD" | "ALCOHOL" | "MAIN" | "SNACK" | "DESSERT" {
  const text = `${normalize(name)} ${normalize(categoryName)}`;

  if (
    text.includes("cafe") ||
    text.includes("latte") ||
    text.includes("cappuccino") ||
    text.includes("capuchino") ||
    text.includes("espresso") ||
    text.includes("americano") ||
    text.includes("te") ||
    text.includes("infusion") ||
    text.includes("caliente")
  ) {
    return "COFFEE";
  }

  if (
    text.includes("cerveza") ||
    text.includes("beer") ||
    text.includes("coctel") ||
    text.includes("margarita") ||
    text.includes("tequila") ||
    text.includes("mezcal") ||
    text.includes("alcohol")
  ) {
    return "ALCOHOL";
  }

  if (
    text.includes("postre") ||
    text.includes("flan") ||
    text.includes("pastel") ||
    text.includes("helado") ||
    text.includes("tarta") ||
    text.includes("dulce") ||
    text.includes("pan") ||
    text.includes("churro")
  ) {
    return "DESSERT";
  }

  if (
    text.includes("papa") ||
    text.includes("nacho") ||
    text.includes("guacamole") ||
    text.includes("queso") ||
    text.includes("alita") ||
    text.includes("boneless") ||
    text.includes("entrada") ||
    text.includes("botana")
  ) {
    return "SNACK";
  }

  if (
    text.includes("agua") ||
    text.includes("refresco") ||
    text.includes("soda") ||
    text.includes("limonada") ||
    text.includes("jugo") ||
    text.includes("bebida")
  ) {
    return "DRINK_COLD";
  }

  return "MAIN";
}

/**
 * Devuelve hasta 3 sugerencias de maridaje / upselling complementarias.
 */
export function getSmartPairings(
  targetItem: MenuItemRef,
  allAvailableItems: MenuItemRef[],
  cartItemIds: string[] = []
): { items: MenuItemRef[]; title: string; subtitle: string } {
  const currentType = classifyFoodType(targetItem.name, targetItem.categoryName);
  const candidates = allAvailableItems.filter((it) => it.id !== targetItem.id);

  let targetType: "COFFEE" | "DRINK_COLD" | "ALCOHOL" | "MAIN" | "SNACK" | "DESSERT" = "DRINK_COLD";
  let title = "Acompaña tu orden";
  let subtitle = "Recomendaciones ideales del chef para este platillo";

  switch (currentType) {
    case "COFFEE":
      targetType = "DESSERT";
      title = "☕ Maridaje dulce recomendado";
      subtitle = "Combina perfecto con un postre o pan horneado";
      break;

    case "MAIN":
      // Si es plato fuerte, sugerir entrada o bebida refrescante
      targetType = Math.random() > 0.5 ? "SNACK" : "DRINK_COLD";
      title = "🍽️ Completa tu experiencia";
      subtitle = "Papas, guacamole o una bebida fría para acompañar";
      break;

    case "SNACK":
      targetType = "DRINK_COLD";
      title = "🥤 ¿Algo refrescante para beber?";
      subtitle = "Acompaña tus botanas con una bebida o cerveza bien fría";
      break;

    case "DESSERT":
      targetType = "COFFEE";
      title = "☕ El toque final perfecto";
      subtitle = "Disfruta tu postre con un café recién preparado";
      break;

    case "ALCOHOL":
      targetType = "SNACK";
      title = "🍟 Para botanear con tu trago";
      subtitle = "Alitas, papas crujientes o guacamole al momento";
      break;

    case "DRINK_COLD":
      targetType = "MAIN";
      title = "🌮 Para abrir el apetito";
      subtitle = "Especialidades del chef para disfrutar con tu bebida";
      break;
  }

  // Filtrar platillos que coincidan con el tipo objetivo preferente
  let matched = candidates.filter((item) => classifyFoodType(item.name, item.categoryName) === targetType);

  // Si no hay suficientes del tipo preferido, rellenar con populares
  if (matched.length < 3) {
    const populars = candidates.filter((item) => item.popular && !matched.some((m) => m.id === item.id));
    matched = [...matched, ...populars];
  }

  // Si aún faltan, agregar cualquier otro producto disponible
  if (matched.length < 3) {
    const remaining = candidates.filter((item) => !matched.some((m) => m.id === item.id));
    matched = [...matched, ...remaining];
  }

  // Priorizar los que NO están en el carrito actualmente
  const sorted = [...matched].sort((a, b) => {
    const aInCart = cartItemIds.includes(a.id) ? 1 : 0;
    const bInCart = cartItemIds.includes(b.id) ? 1 : 0;
    return aInCart - bInCart;
  });

  return {
    items: sorted.slice(0, 3),
    title,
    subtitle,
  };
}
