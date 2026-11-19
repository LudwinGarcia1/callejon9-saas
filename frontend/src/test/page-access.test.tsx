// @vitest-environment jsdom
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { canOpenPage, homeSectionFor, NAV_ITEMS_BY_ROLE } from "@/components/layout/nav";
import { NO_PERMISSION_MESSAGE, PageAccess } from "@/components/layout/page-access";
import { ApiError } from "@/lib/api";
import { PROTECTED_PAGE_PREFIXES } from "@/lib/protected-pages";
import type { SessionResponse, UserRole } from "@/lib/types";

const state = vi.hoisted(() => ({
  pathname: "/waiter",
  session: {} as { user?: unknown; isLoading: boolean; error: unknown },
}));
vi.mock("next/navigation", () => ({ usePathname: () => state.pathname }));
vi.mock("@/hooks/use-session", () => ({ useSession: () => state.session }));

const ROLES = Object.keys(NAV_ITEMS_BY_ROLE) as UserRole[];

function sessionFor(role: UserRole): SessionResponse {
  return {
    userId: "user-1", fullName: "Usuario de prueba", role, tenantId: "tenant-1",
    slug: "pruebas", restaurantName: "Pruebas", timezone: "America/Mexico_City",
  };
}

describe("CAL-195: pantallas por rol", () => {
  // Los casos que el ensayo de UAT-13 encontró abiertos.
  it.each<[UserRole, string]>([
    ["WAITER", "/admin"],
    ["WAITER", "/cashier"],
    ["CASHIER", "/admin"],
    ["KITCHEN", "/waiter"],
    ["KITCHEN", "/waiter/order/123"],
    ["WAITER", "/history"],
    ["CASHIER", "/analytics"],
    ["ADMIN", "/platform"],
    ["SUPER_ADMIN", "/admin"],
  ])("%s no abre %s", (role, pathname) => {
    expect(canOpenPage(role, pathname)).toBe(false);
  });

  it.each<[UserRole, string]>([
    ["WAITER", "/waiter"],
    ["WAITER", "/waiter/order/123"],
    ["KITCHEN", "/kitchen"],
    ["KITCHEN", "/inventory"],
    ["CASHIER", "/cashier"],
    ["CASHIER", "/cash-sessions"],
    ["CASHIER", "/history"],
    ["SUPER_ADMIN", "/platform"],
  ])("%s abre %s", (role, pathname) => {
    expect(canOpenPage(role, pathname)).toBe(true);
  });

  it("el administrador abre todas las secciones del restaurante y ninguna de plataforma", () => {
    for (const prefix of PROTECTED_PAGE_PREFIXES) {
      expect(canOpenPage("ADMIN", prefix)).toBe(prefix !== "/platform");
    }
  });

  it("un prefijo parecido no cuenta como la seccion", () => {
    expect(canOpenPage("WAITER", "/waiters")).toBe(false);
    expect(canOpenPage("CASHIER", "/cashier-old")).toBe(false);
  });

  it("toda ruta protegida es de algun rol y cada rol vuelve a una seccion propia", () => {
    for (const prefix of PROTECTED_PAGE_PREFIXES) {
      expect(ROLES.some((role) => canOpenPage(role, prefix)), prefix).toBe(true);
    }
    for (const role of ROLES) {
      expect(canOpenPage(role, homeSectionFor(role).href)).toBe(true);
    }
  });
});

describe("CAL-195: PageAccess", () => {
  beforeEach(() => {
    state.pathname = "/waiter";
    state.session = { user: undefined, isLoading: false, error: null };
  });
  afterEach(cleanup);

  it("no monta la pantalla mientras no conoce el rol", () => {
    state.session = { user: undefined, isLoading: true, error: null };
    render(<PageAccess><p>Pantalla</p></PageAccess>);
    expect(screen.queryByText("Pantalla")).toBeNull();
  });

  it("muestra el mensaje de permiso y el camino a la seccion propia, sin la pantalla ajena", () => {
    state.pathname = "/admin";
    state.session = { user: sessionFor("WAITER"), isLoading: false, error: null };
    render(<PageAccess><p>Pantalla</p></PageAccess>);
    expect(screen.queryByText("Pantalla")).toBeNull();
    expect(screen.getByRole("heading", { name: NO_PERMISSION_MESSAGE })).toBeDefined();
    expect(screen.getByRole("link", { name: "Ir a Mesas" }).getAttribute("href")).toBe("/waiter");
  });

  it("pinta la pantalla cuando el rol la tiene", () => {
    state.pathname = "/waiter/order/123";
    state.session = { user: sessionFor("WAITER"), isLoading: false, error: null };
    render(<PageAccess><p>Pantalla</p></PageAccess>);
    expect(screen.getByText("Pantalla")).toBeDefined();
    expect(screen.queryByText(NO_PERMISSION_MESSAGE)).toBeNull();
  });

  it("sin sesion por 401 no muestra error ni pantalla mientras redirige", () => {
    state.session = { user: undefined, isLoading: false, error: new ApiError({}, 401) };
    render(<PageAccess><p>Pantalla</p></PageAccess>);
    expect(screen.queryByText("Pantalla")).toBeNull();
    expect(screen.queryByText(/error/i)).toBeNull();
  });

  it("si la sesion falla por otra causa lo dice, sin pintar la pantalla", () => {
    state.session = { user: undefined, isLoading: false, error: new Error("offline") };
    render(<PageAccess><p>Pantalla</p></PageAccess>);
    expect(screen.queryByText("Pantalla")).toBeNull();
    expect(screen.getByText("No se pudo cargar tu sesión. Intenta de nuevo.")).toBeDefined();
  });
});
