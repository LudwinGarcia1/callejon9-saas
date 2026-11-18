// @vitest-environment jsdom
import { act, cleanup, render, waitFor } from "@testing-library/react";
import { useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { Providers } from "@/app/providers";
import { ApiError } from "@/lib/api";

const navigation = vi.hoisted(() => ({ replace: vi.fn(), push: vi.fn(), pathname: "/waiter" }));
vi.mock("next/navigation", () => ({
  useRouter: () => navigation,
  usePathname: () => navigation.pathname,
}));

const unauthorized = () => new ApiError({}, 401);

/** Una consulta de pantalla que responde lo que diga `result`. */
function Screen({ result, onClient }: { result: () => Promise<string>; onClient?: (c: QueryClient) => void }) {
  const client = useQueryClient();
  onClient?.(client);
  useQuery({ queryKey: ["pantalla", navigation.pathname], queryFn: result, retry: false });
  return null;
}

/** Simula estar en `path`: el manejador lee la ruta real del navegador. */
function goTo(path: string) {
  navigation.pathname = path;
  window.history.replaceState(null, "", path);
}

describe("CAL-194: manejo del 401 por ruta", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    goTo("/waiter");
  });
  afterEach(cleanup);

  it("una pantalla protegida abierta sin sesion vuelve al login sin aviso de expiracion", async () => {
    render(<Providers><Screen result={() => Promise.reject(unauthorized())} /></Providers>);

    await waitFor(() => expect(navigation.replace).toHaveBeenCalledWith("/login?next=%2Fwaiter"));
    expect(navigation.replace).toHaveBeenCalledTimes(1);
  });

  it("si habia sesion y luego llega un 401, avisa que expiro", async () => {
    let fail = false;
    const result = () => (fail ? Promise.reject(unauthorized()) : Promise.resolve("ok"));
    let client!: QueryClient;
    render(<Providers><Screen result={result} onClient={(c) => { client = c; }} /></Providers>);
    await waitFor(() => expect(client.getQueryData(["pantalla", "/waiter"])).toBe("ok"));

    fail = true;
    await act(() => client.refetchQueries());

    await waitFor(() => expect(navigation.replace).toHaveBeenCalledWith("/login?expired=1"));
  });

  it("un 401 en el login, como una contrasena equivocada, no redirige", async () => {
    goTo("/login");
    function Login() {
      const login = useMutation({ mutationFn: () => Promise.reject(unauthorized()) });
      return <button onClick={() => login.mutate()}>Entrar</button>;
    }
    const view = render(<Providers><Login /></Providers>);

    await act(async () => view.getByText("Entrar").click());

    expect(navigation.replace).not.toHaveBeenCalled();
  });

  it("tras llegar al login, volver con Atras a la pantalla protegida vuelve a redirigir", async () => {
    const view = render(<Providers><Screen result={() => Promise.reject(unauthorized())} /></Providers>);
    await waitFor(() => expect(navigation.replace).toHaveBeenCalledTimes(1));

    // Llega al login: la pantalla protegida ya no esta montada.
    goTo("/login");
    view.rerender(<Providers>{null}</Providers>);

    // Atras: la pantalla protegida se vuelve a montar y su consulta da 401.
    goTo("/waiter");
    view.rerender(<Providers><Screen result={() => Promise.reject(unauthorized())} /></Providers>);

    await waitFor(() => expect(navigation.replace).toHaveBeenCalledTimes(2));
    expect(navigation.replace).toHaveBeenLastCalledWith("/login?next=%2Fwaiter");
  });

  it("al salir de las pantallas con sesion limpia el cache; al entrar a una publica no", async () => {
    let client!: QueryClient;
    const view = render(
      <Providers><Screen result={() => Promise.resolve("datos")} onClient={(c) => { client = c; }} /></Providers>,
    );
    await waitFor(() => expect(client.getQueryData(["pantalla", "/waiter"])).toBe("datos"));

    goTo("/login");
    view.rerender(<Providers>{null}</Providers>);
    await waitFor(() => expect(client.getQueryData(["pantalla", "/waiter"])).toBeUndefined());

    // De una pantalla publica a otra (login → menu QR) el cache se conserva.
    client.setQueryData(["menu"], "carta");
    goTo("/m/pruebas");
    view.rerender(<Providers>{null}</Providers>);
    expect(client.getQueryData(["menu"])).toBe("carta");
  });
});
