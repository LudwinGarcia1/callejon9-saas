// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import { RestaurantSettingsSection } from "@/app/(authenticated)/admin/restaurant-settings-section";
import { ApiError, api } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
import type { RestaurantSettingsResponse } from "@/lib/types";

const effects = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock("sonner", () => ({ toast: effects }));
vi.mock("@/lib/api", async (original) => ({ ...await original<typeof import("@/lib/api")>(), api: { get: vi.fn(), patch: vi.fn() } }));
// Radix Select no se puede operar en jsdom; un <select> nativo expone el mismo contrato value/onValueChange.
vi.mock("@/components/ui/select", () => ({
  Select: ({ value, onValueChange, children }: { value: string; onValueChange: (value: string) => void; children: ReactNode }) =>
    <select aria-label="Zona del restaurante" value={value} onChange={(event) => onValueChange(event.target.value)}>{children}</select>,
  SelectTrigger: () => null,
  SelectValue: () => null,
  SelectContent: ({ children }: { children: ReactNode }) => <>{children}</>,
  SelectItem: ({ value, children }: { value: string; children: ReactNode }) => <option value={value}>{children}</option>,
}));

let client: QueryClient;
function mount(settings: RestaurantSettingsResponse) {
  client.setQueryData(queryKeys.settings.detail(), settings);
  vi.mocked(api.get).mockResolvedValue(settings);
  render(<QueryClientProvider client={client}><RestaurantSettingsSection /></QueryClientProvider>);
}
const cancun: RestaurantSettingsResponse = { name: "Tacos", slug: "tacos", timezone: "America/Cancun" };

beforeEach(() => {
  vi.resetAllMocks();
  client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity }, mutations: { retry: false } } });
});
afterEach(() => { cleanup(); client.clear(); });

describe("zona horaria del restaurante (CAL-19)", () => {
  it("muestra la zona guardada y no permite guardar sin cambios", () => {
    mount(cancun);
    expect((screen.getByLabelText("Zona del restaurante") as HTMLSelectElement).value).toBe("America/Cancun");
    expect((screen.getByRole("button", { name: "Guardar zona horaria" }) as HTMLButtonElement).disabled).toBe(true);
  });

  it("guarda la zona elegida, refresca la sesión y avisa", async () => {
    const tijuana = { ...cancun, timezone: "America/Tijuana" };
    vi.mocked(api.patch).mockResolvedValue(tijuana);
    const invalidate = vi.spyOn(client, "invalidateQueries");
    mount(cancun);

    fireEvent.change(screen.getByLabelText("Zona del restaurante"), { target: { value: "America/Tijuana" } });
    fireEvent.click(screen.getByRole("button", { name: "Guardar zona horaria" }));

    await waitFor(() => expect(api.patch).toHaveBeenCalledWith("/api/v1/settings", { timezone: "America/Tijuana" }));
    await waitFor(() => expect(effects.success).toHaveBeenCalledWith("Zona horaria actualizada."));
    expect(client.getQueryData(queryKeys.settings.detail())).toEqual(tijuana);
    expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.session.me() });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.sales.all() });
  });

  it("muestra el motivo cuando el backend rechaza la zona", async () => {
    vi.mocked(api.patch).mockRejectedValue(new ApiError(
      { title: "Validacion fallida", errors: { timezone: "Debe ser una zona horaria IANA geografica, por ejemplo America/Cancun." } }, 400));
    mount(cancun);

    fireEvent.change(screen.getByLabelText("Zona del restaurante"), { target: { value: "America/Mexico_City" } });
    fireEvent.click(screen.getByRole("button", { name: "Guardar zona horaria" }));

    expect(await screen.findByText("No se pudo guardar la zona horaria")).toBeTruthy();
    expect(screen.getByText(/zona horaria IANA geografica/)).toBeTruthy();
  });

  it("conserva como opción una zona guardada que no está en la lista de México", () => {
    mount({ ...cancun, timezone: "Europe/Madrid" });
    expect((screen.getByLabelText("Zona del restaurante") as HTMLSelectElement).value).toBe("Europe/Madrid");
    expect(screen.getByRole("option", { name: "Europe/Madrid" })).toBeTruthy();
  });
});
