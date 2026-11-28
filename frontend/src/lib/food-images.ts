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
  prepTime?: string;
  suggestedNotes?: string[];
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

  // 1. Café y bebidas calientes (MÁXIMA PRIORIDAD para bebidas)
  if (
    combined.includes("cafe") ||
    combined.includes("cappuccino") ||
    combined.includes("capuchino") ||
    combined.includes("latte") ||
    combined.includes("espresso") ||
    combined.includes("expreso") ||
    combined.includes("americano") ||
    combined.includes("moka") ||
    combined.includes("mocha") ||
    combined.includes("infusion") ||
    combined.includes("te ") ||
    normName === "te" ||
    combined.includes("chocolate caliente") ||
    combined.includes("bebida caliente") ||
    combined.includes("caliente")
  ) {
    return {
      image: "/images/food/cafe.jpg",
      emoji: "☕",
      badge: "Recién Hecho",
      gradient: "from-amber-700/30 to-yellow-800/10",
      prepTime: "3-5 min",
      suggestedNotes: [
        "Sin azúcar",
        "Leche deslactosada",
        "Leche de almendra",
        "Poco caliente",
        "Extra caliente",
        "Con canela",
      ],
    };
  }

  // 2. Papas fritas / botanas
  if (
    combined.includes("papa") ||
    combined.includes("patata") ||
    combined.includes("french fry") ||
    combined.includes("fritas")
  ) {
    return {
      image: "/images/food/papas.jpg",
      emoji: "🍟",
      badge: "Crujientes",
      gradient: "from-amber-600/30 to-yellow-600/10",
      prepTime: "8-12 min",
      suggestedNotes: ["Salsa de queso aparte", "Bien doradas", "Poca sal", "Ketchup extra"],
    };
  }

  // 3. Leche y lácteos
  if (combined.includes("leche") || combined.includes("milk") || combined.includes("lacteo")) {
    return {
      image: "/images/food/leche.jpg",
      emoji: "🥛",
      badge: "Fresco",
      gradient: "from-blue-600/30 to-sky-600/10",
      prepTime: "2-3 min",
      suggestedNotes: ["Bien fría", "Tibia", "Deslactosada", "Con chocolate"],
    };
  }

  // 4. Hamburguesas
  if (combined.includes("burger") || combined.includes("hamburguesa")) {
    return {
      image: "/images/food/burger.jpg",
      emoji: "🍔",
      badge: "Gourmet",
      gradient: "from-amber-600/30 to-orange-600/10",
      prepTime: "12-18 min",
      suggestedNotes: [
        "Sin cebolla",
        "Sin pepinillos",
        "Salsa aparte",
        "Carne bien cocida",
        "Sin mostaza",
      ],
    };
  }

  // 5. Tacos y antojitos mexicanos
  if (
    combined.includes("taco") ||
    combined.includes("taquito") ||
    combined.includes("pastor") ||
    combined.includes("carnita") ||
    combined.includes("suadero") ||
    combined.includes("gringa")
  ) {
    return {
      image: "/images/food/tacos.jpg",
      emoji: "🌮",
      badge: "Especialidad",
      gradient: "from-amber-600/30 to-red-600/10",
      prepTime: "8-12 min",
      suggestedNotes: ["Sin cebolla", "Sin cilantro", "Salsa aparte", "Copia doble", "Con limón"],
    };
  }

  // 6. Chilaquiles y desayunos
  if (
    combined.includes("chilaquil") ||
    combined.includes("huevo") ||
    combined.includes("omelet") ||
    combined.includes("desayuno") ||
    combined.includes("pancake") ||
    combined.includes("hot cake") ||
    combined.includes("waffle")
  ) {
    return {
      image: "/images/food/chilaquiles.jpg",
      emoji: "🍳",
      badge: "Al gusto",
      gradient: "from-orange-600/30 to-amber-600/10",
      prepTime: "10-15 min",
      suggestedNotes: ["Salsa verde", "Salsa roja", "Sin crema", "Huevos estrellados bien cocidos"],
    };
  }

  // 7. Alitas, boneless y tenders
  if (
    combined.includes("alita") ||
    combined.includes("wing") ||
    combined.includes("boneless") ||
    combined.includes("tender") ||
    combined.includes("nugget")
  ) {
    return {
      image: "/images/food/alitas.jpg",
      emoji: "🍗",
      badge: "Crujiente",
      gradient: "from-red-600/30 to-orange-600/10",
      prepTime: "12-16 min",
      suggestedNotes: ["Salsa BBQ", "Salsa Búfalo", "Aderezo ranch aparte", "Bien doradas"],
    };
  }

  // 8. Carnes, arrachera y cortes
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
      prepTime: "15-20 min",
      suggestedNotes: ["Término medio", "Tres cuartos", "Bien cocido", "Tortillas de maíz", "Con cebollitas"],
    };
  }

  // 9. Pastas y cocina italiana
  if (
    combined.includes("pasta") ||
    combined.includes("espagueti") ||
    combined.includes("spaghetti") ||
    combined.includes("lasana") ||
    combined.includes("lasagna") ||
    combined.includes("fettuccine") ||
    combined.includes("alfredo")
  ) {
    return {
      image: "/images/food/pasta.jpg",
      emoji: "🍝",
      badge: "Artesanal",
      gradient: "from-amber-600/30 to-red-600/10",
      prepTime: "12-16 min",
      suggestedNotes: ["Queso parmesano extra", "Sin perejil", "Pan con ajo"],
    };
  }

  // 10. Sopas, caldos y consomés
  if (
    combined.includes("sopa") ||
    combined.includes("caldo") ||
    combined.includes("consome") ||
    combined.includes("pozole") ||
    combined.includes("crema de") ||
    combined.includes("ramen")
  ) {
    return {
      image: "/images/food/sopa.jpg",
      emoji: "🍲",
      badge: "Reconfortante",
      gradient: "from-orange-600/30 to-yellow-600/10",
      prepTime: "8-12 min",
      suggestedNotes: ["Bien caliente", "Sin crema", "Limón aparte", "Tortillas calientes"],
    };
  }

  // 11. Mariscos y pescados
  if (
    combined.includes("marisco") ||
    combined.includes("camaron") ||
    combined.includes("pescado") ||
    combined.includes("ceviche") ||
    combined.includes("atun") ||
    combined.includes("salmon") ||
    combined.includes("pulpo")
  ) {
    return {
      image: "/images/food/mariscos.jpg",
      emoji: "🦐",
      badge: "Fresco del día",
      gradient: "from-cyan-600/30 to-blue-600/10",
      prepTime: "10-15 min",
      suggestedNotes: ["Salsa aparte", "Con aguacate extra", "Poco picante", "Con tostadas"],
    };
  }

  // 12. Guacamole, totopos y nachos
  if (
    combined.includes("guacamole") ||
    combined.includes("totopo") ||
    combined.includes("nacho") ||
    combined.includes("aguacate")
  ) {
    return {
      image: "/images/food/guacamole.jpg",
      emoji: "🥑",
      badge: "Al momento",
      gradient: "from-emerald-600/30 to-teal-600/10",
      prepTime: "5-8 min",
      suggestedNotes: ["Sin cebolla", "Con jalapeños aparte", "Totopos extra"],
    };
  }

  // 13. Queso fundido y chistorra
  if (
    combined.includes("queso") ||
    combined.includes("fundido") ||
    combined.includes("chistorra") ||
    combined.includes("quesadilla")
  ) {
    return {
      image: "/images/food/queso_fundido.jpg",
      emoji: "🧀",
      badge: "Al comal",
      gradient: "from-amber-500/30 to-yellow-600/10",
      prepTime: "8-12 min",
      suggestedNotes: ["Tortillas de harina", "Tortillas de maíz", "Con chorizo", "Bien derretido"],
    };
  }

  // 14. Pizzas
  if (combined.includes("pizza") || combined.includes("calzone")) {
    return {
      image: "/images/food/pizza.jpg",
      emoji: "🍕",
      badge: "Horno de piedra",
      gradient: "from-orange-600/30 to-amber-600/10",
      prepTime: "15-20 min",
      suggestedNotes: ["Bien dorada", "Orilla delgada", "Chile quebrado aparte", "Extra queso"],
    };
  }

  // 15. Pan dulce y repostería
  if (
    combined.includes("pan ") ||
    combined.includes("croissant") ||
    combined.includes("dona") ||
    combined.includes("churro") ||
    combined.includes("concha")
  ) {
    return {
      image: "/images/food/pan.jpg",
      emoji: "🥐",
      badge: "Horneado hoy",
      gradient: "from-amber-600/30 to-yellow-700/10",
      prepTime: "2-4 min",
      suggestedNotes: ["Calientito", "Con cajeta", "Con chocolate"],
    };
  }

  // 16. Postres y dulces
  if (
    combined.includes("postre") ||
    combined.includes("flan") ||
    combined.includes("pastel") ||
    combined.includes("helado") ||
    combined.includes("tarta") ||
    combined.includes("brownie")
  ) {
    return {
      image: "/images/food/postre.jpg",
      emoji: "🍰",
      badge: "Casero",
      gradient: "from-pink-600/30 to-rose-600/10",
      prepTime: "3-5 min",
      suggestedNotes: ["Cuchara extra", "Sin canela", "Bien frío"],
    };
  }

  // 17. Cervezas
  if (
    combined.includes("cerveza") ||
    combined.includes("beer") ||
    combined.includes("ipa") ||
    combined.includes("corona") ||
    combined.includes("victoria") ||
    combined.includes("modelo") ||
    combined.includes("michelada")
  ) {
    return {
      image: "/images/food/cerveza.jpg",
      emoji: "🍺",
      badge: "Bien fría",
      gradient: "from-amber-500/30 to-yellow-500/10",
      prepTime: "2-3 min",
      suggestedNotes: ["Vaso escarchado", "Con limón y sal", "Bien fría", "Michelada cubana"],
    };
  }

  // 18. Cócteles y licores
  if (
    combined.includes("coctel") ||
    combined.includes("cocktail") ||
    combined.includes("margarita") ||
    combined.includes("tequila") ||
    combined.includes("mezcal") ||
    combined.includes("mojito") ||
    combined.includes("gin") ||
    combined.includes("vodka")
  ) {
    return {
      image: "/images/food/coctel.jpg",
      emoji: "🍸",
      badge: "Mixología",
      gradient: "from-purple-600/30 to-indigo-600/10",
      prepTime: "4-6 min",
      suggestedNotes: ["Escarchado con sal", "Poco dulce", "Hielo frappé"],
    };
  }

  // 19. Ensaladas y verdes
  if (
    combined.includes("ensalada") ||
    combined.includes("salad") ||
    combined.includes("lechuga") ||
    combined.includes("vegetal") ||
    combined.includes("cesar")
  ) {
    return {
      image: "/images/food/ensalada.jpg",
      emoji: "🥗",
      badge: "Light & Fresh",
      gradient: "from-emerald-600/30 to-green-600/10",
      prepTime: "6-10 min",
      suggestedNotes: ["Aderezo aparte", "Sin crutones", "Sin cebolla", "Extra aderezo"],
    };
  }

  // 20. Bebidas y refrescos fríos
  if (
    combined.includes("bebida") ||
    combined.includes("agua") ||
    combined.includes("refresco") ||
    combined.includes("soda") ||
    combined.includes("jugo") ||
    combined.includes("horchata") ||
    combined.includes("jamaica") ||
    combined.includes("limonada") ||
    combined.includes("coca") ||
    combined.includes("sprite")
  ) {
    return {
      image: "/images/food/bebida.jpg",
      emoji: "🥤",
      badge: "Refrescante",
      gradient: "from-blue-600/30 to-cyan-600/10",
      prepTime: "2-3 min",
      suggestedNotes: ["Sin hielo", "Poco hielo", "Popote biodegradable", "Vaso con limón"],
    };
  }

  // Fallback gourmet
  return {
    image: "/images/food/default.jpg",
    emoji: "🍽️",
    badge: "Especial",
    gradient: "from-neutral-700/30 to-neutral-800/10",
    prepTime: "10-15 min",
    suggestedNotes: ["Sin cebolla", "Salsa aparte", "Bien cocido"],
  };
}

export function getCategoryEmoji(categoryName: string): string {
  const norm = normalize(categoryName);
  if (norm.includes("cafe") || norm.includes("caliente")) return "☕";
  if (norm.includes("bebida") || norm.includes("refresco") || norm.includes("liquido")) return "🥤";
  if (norm.includes("cerveza") || norm.includes("alcohol") || norm.includes("bar")) return "🍺";
  if (norm.includes("comida rapida") || norm.includes("fast food") || norm.includes("snack")) return "🍟";
  if (norm.includes("taco") || norm.includes("antojito")) return "🌮";
  if (norm.includes("carne") || norm.includes("corte") || norm.includes("asador")) return "🥩";
  if (norm.includes("lacteo") || norm.includes("leche") || norm.includes("queso")) return "🧀";
  if (norm.includes("postre") || norm.includes("dulce") || norm.includes("pastel")) return "🍰";
  if (norm.includes("desayuno") || norm.includes("huevo")) return "🍳";
  if (norm.includes("pasta") || norm.includes("italiana")) return "🍝";
  if (norm.includes("marisco") || norm.includes("pescado")) return "🦐";
  if (norm.includes("ensalada") || norm.includes("vegetariano") || norm.includes("verde")) return "🥗";
  if (norm.includes("sopa") || norm.includes("caldo")) return "🍲";
  return "🍽️";
}
