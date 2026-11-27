export interface Category {
  id: string;
  name: string;
  sortOrder: number;
}

export interface Product {
  id: string;
  name: string;
  description: string | null;
  price: number;
  categoryId: string | null;
  active: boolean;
}

/** Respuesta de GET /api/v1/public/{slug}/catalog. */
export interface PublicCatalog {
  restaurantName: string;
  slug: string;
  categories: Category[];
  products: Product[];
}

export interface OrderItemRequest {
  productId: string;
  quantity: number;
  notes?: string;
}

export interface OrderResponse {
  id: string;
  folio: string;
  status: string;
  total: number;
}

export interface ProblemDetail {
  title?: string;
  detail?: string;
  status?: number;
}
