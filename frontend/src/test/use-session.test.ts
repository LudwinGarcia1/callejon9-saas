// @vitest-environment jsdom
import { createElement, type ReactNode } from "react";
import { act, cleanup, renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useSession } from "@/hooks/use-session";
import { api, ApiError } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
import type { SessionResponse } from "@/lib/types";

const navigation = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => navigation }));
vi.mock("@/lib/api", async (original) => ({ ...await original<typeof import("@/lib/api")>(),
  api: { get: vi.fn(), post: vi.fn() } }));

describe("session lifecycle", () => {
  let client: QueryClient;
  const session: SessionResponse = { userId: "user-1", fullName: "Usuario de prueba",
    role: "WAITER", tenantId: "tenant-1", slug: "test", restaurantName: "Prueba" };
  beforeEach(() => {
    vi.resetAllMocks();
    client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  });
  afterEach(() => { cleanup(); client.clear(); });
  const wrapper = ({ children }: { children: ReactNode }) =>
    createElement(QueryClientProvider, { client }, children);

  it("shows loading until the session request resolves", async () => {
    let resolve!: (value: SessionResponse) => void;
    vi.mocked(api.get).mockReturnValue(new Promise<SessionResponse>((done) => { resolve = done; }));
    const { result } = renderHook(useSession, { wrapper });
    expect(result.current.isLoading).toBe(true);
    expect(result.current.user).toBeUndefined();
    await act(async () => resolve(session));
    await waitFor(() => expect(result.current.user).toEqual(session));
    expect(result.current.isLoading).toBe(false);
    expect(api.get).toHaveBeenCalledWith("/api/v1/auth/me");
  });

  it("stops loading without retrying an unauthorized session", async () => {
    vi.mocked(api.get).mockRejectedValue(new ApiError({ detail: "Sesión vencida" }, 401));
    const { result } = renderHook(useSession, { wrapper });
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.user).toBeUndefined();
    expect(api.get).toHaveBeenCalledTimes(1);
    expect(navigation.push).not.toHaveBeenCalled();
  });

  it.each([false, true])("clears tenant cache and navigates after logout (failure=%s)", async (failure) => {
    vi.mocked(api.get).mockResolvedValue(session);
    let settle!: () => void;
    vi.mocked(api.post).mockReturnValue(new Promise<void>((resolve, reject) => {
      settle = () => failure ? reject(new Error("offline")) : resolve();
    }));
    client.setQueryData(queryKeys.orders.detail("order-1"), { id: "order-1" });
    const { result, unmount } = renderHook(useSession, { wrapper });
    // La navegación real desmonta los consumidores de sesión de la ruta protegida.
    navigation.push.mockImplementation(() => unmount());
    await waitFor(() => expect(result.current.user).toEqual(session));
    act(() => result.current.logout());
    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/auth/logout"));
    expect(navigation.push).not.toHaveBeenCalled();
    expect(client.getQueryData(queryKeys.orders.detail("order-1"))).toBeDefined();
    await act(async () => settle());
    await waitFor(() => expect(navigation.push).toHaveBeenCalledWith("/login"));
    expect(client.getQueryData(queryKeys.orders.detail("order-1"))).toBeUndefined();
    expect(client.getQueryData(queryKeys.session.me())).toBeUndefined();
  });
});
