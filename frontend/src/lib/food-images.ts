/**
 * Mapeo visual inteligente de imágenes gastronómicas optimizadas.
 * Permite que los platillos del menú digital QR cuenten con fotografías apetitosas
 * de alta definición servidas localmente sin latencia y con soporte offline.
 */

export interface FoodVisual {
  image: string;
  emoji: string;
  badge?: string;
  gradient: string;
}

function normalize(text: string): string {
  return text
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .trim();
}

export function getFoodVisual(name: string, categoryName: string = ""): FoodVisual {
  const normName = normalize(name);
  const normCat = normalize(categoryName);
  const combined = `${normName} ${normCat}`;

  // 1. Papas fritas / botanas
  if (combined.includes("papa") || combined.includes("patata") || combined.includes("french fry") || combined.includes("fritas")) {
    return {
      image: "/images/food/papas.jpg",
      emoji: "🍟",
      badge: "Crujientes",
      gradient: "from-amber-600/30 to-yellow-600/10",
    };
  }

  // 2. Leche y lácteos
  if (combined.includes("leche") || combined.includes("milk") || combined.includes("lacteo")) {
    return {
      image: "/images/food/leche.jpg",
      emoji: "🥛",
      badge: "Fresco",
      gradient: "from-blue-600/30 to-sky-600/10",
    };
  }

  // 3. Hamburguesas
  if (combined.includes("burger") || combined.includes("hamburguesa")) {
    return {
      image: "/images/food/burger.jpg",
      emoji: "🍔",
      badge: "Gourmet",
      gradient: "from-amber-600/30 to-orange-600/10",
    };
  }

  // 4. Tacos y antojitos mexicanos
  if (combined.includes("taco") || combined.includes("taquito") || combined.includes("pastor") || combined.includes("carnita") || combined.includes("suadero")) {
    return {
      image: "/images/food/tacos.jpg",
      emoji: "🌮",
      badge: "Especialidad",
      gradient: "from-amber-600/30 to-red-600/10",
    };
  }

  // 5. Carnes, arrachera y cortes
  if (
    combined.includes("arrachera") ||
    combined.includes("carne") ||
    combined.includes("corte") ||
    combined.includes("asada") ||
    combined.includes("ribeye") ||
    combined.includes("sirloin") ||
    combined.includes("bistec") ||
    combined.includes("filete")
  ) {
    return {
      image: "/images/food/carne.jpg",
      emoji: "🥩",
      badge: "A las brasas",
      gradient: "from-red-600/30 to-amber-700/10",
    };
  }

  // 6. Guacamole, totopos y nachos
  if (combined.includes("guacamole") || combined.includes("totopo") || combined.includes("nacho") || combined.includes("aguacate")) {
    return {
      image: "/images/food/guacamole.jpg",
      emoji: "🥑",
      badge: "Al momento",
      gradient: "from-emerald-600/30 to-teal-600/10",
    };
  }

  // 7. Queso fundido y chistorra
  if (combined.includes("queso") || combined.includes("fundido") || combined.includes("chistorra") || combined.includes("quesadilla")) {
    return {
      image: "/images/food/queso_fundido.jpg",
      emoji: "🧀",
      badge: "Al comal",
      gradient: "from-amber-500/30 to-yellow-600/10",
    };
  }

  // 8. Pizzas
  if (combined.includes("pizza") || combined.includes("calzone")) {
    return {
      image: "/images/food/pizza.jpg",
      emoji: "🍕",
      badge: "Artesanal",
      gradient: "from-orange-600/30 to-amber-600/10",
    };
  }

  // 9. Postres y dulces
  if (combined.includes("postre") || combined.includes("flan") || combined.includes("pastel") || combined.includes("helado") || combined.includes("tarta")) {
    return {
      image: "/images/food/postre.jpg",
      emoji: "🍰",
      badge: "Casero",
      gradient: "from-pink-600/30 to-rose-600/10",
    };
  }

  // 10. Cervezas
  if (combined.includes("cerveza") || combined.includes("beer") || combined.includes("ipa") || combined.includes("corona") || combined.includes("victoria")) {
    return {
      image: "/images/food/cerveza.jpg",
      emoji: "🍺",
      badge: "Bien fría",
      gradient: "from-amber-500/30 to-yellow-500/10",
    };
  }

  // 11. Bebidas y refrescos
  if (
    combined.includes("bebida") ||
    combined.includes("agua") ||
    combined.includes("refresco") ||
    combined.includes("soda") ||
    combined.includes("jugo") ||
    combined.includes("horchata") ||
    combined.includes("jamaica") ||
    combined.includes("limonada") ||
    combined.includes("cafe")
  ) {
    return {
      image: "/images/food/bebida.jpg",
      emoji: "🥤",
      badge: "Refrescante",
      gradient: "from-blue-600/30 to-teal-600/10",
    };
  }

  // 12. Ensaladas
  if (combined.includes("ensalada") || combined.includes("salad") || combined.includes("verde") || combined.includes("vegetal")) {
    return {
      image: "/images/food/ensalada.jpg",
      emoji: "🥗",
      badge: "Fresco y ligero",
      gradient: "from-emerald-600/30 to-green-600/10",
    };
  }

  // Fallback por categoría
  if (normCat.includes("bebida")) {
    return {
      image: "/images/food/bebida.jpg",
      emoji: "🥤",
      gradient: "from-blue-600/30 to-teal-600/10",
    };
  }
  if (normCat.includes("postre")) {
    return {
      image: "/images/food/postre.jpg",
      emoji: "🍰",
      gradient: "from-pink-600/30 to-rose-600/10",
    };
  }
  if (normCat.includes("fuerte") || normCat.includes("principal")) {
    return {
      image: "/images/food/carne.jpg",
      emoji: "🥩",
      gradient: "from-red-600/30 to-amber-700/10",
    };
  }
  if (normCat.includes("entrada")) {
    return {
      image: "/images/food/guacamole.jpg",
      emoji: "🥑",
      gradient: "from-emerald-600/30 to-teal-600/10",
    };
  }

  // Default gastronómico general
  return {
    image: "/images/food/default.jpg",
    emoji: "🍽️",
    gradient: "from-neutral-700/40 to-neutral-800/10",
  };
}

export function getCategoryEmoji(categoryName: string): string {
  const norm = normalize(categoryName);
  if (norm.includes("entrada") || norm.includes("botana")) return "🥗";
  if (norm.includes("fuerte") || norm.includes("plato") || norm.includes("carne")) return "🥩";
  if (norm.includes("rapida") || norm.includes("burger") || norm.includes("pizza") || norm.includes("taco")) return "🍔";
  if (norm.includes("bebida") || norm.includes("refresco") || norm.includes("tomar")) return "🥤";
  if (norm.includes("postre") || norm.includes("dulce")) return "🍰";
  if (norm.includes("cerveza") || norm.includes("alcohol") || norm.includes("bar")) return "🍺";
  return "🍽️";
}
