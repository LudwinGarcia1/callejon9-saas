// @vitest-environment jsdom
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useNavCounts } from "@/hooks/use-nav-counts";
import { api } from "@/lib/api";
import type { UserRole } from "@/lib/types";

vi.mock("@/lib/api", () => ({
  api: {
    get: vi.fn(),
  },
}));

let client: QueryClient;

beforeEach(() => {
  vi.resetAllMocks();
  client = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        staleTime: Infinity,
      },
    },
  });
});

afterEach(() => {
  cleanup();
  client.clear();
});

function NavCountsConsumer({ role }: { role: UserRole | undefined }) {
  const counts = useNavCounts(role);
  return (
    <div>
      <span data-testid="waiter-count">{counts["/waiter"] ?? "none"}</span>
      <span data-testid="kitchen-count">{counts["/kitchen"] ?? "none"}</span>
      <span data-testid="cashier-count">{counts["/cashier"] ?? "none"}</span>
    </div>
  );
}

function mount(role: UserRole | undefined) {
  return render(
    <QueryClientProvider client={client}>
      <NavCountsConsumer role={role} />
    </QueryClientProvider>,
  );
}

describe("useNavCounts", () => {
  it("returns counts for ADMIN who can view orders, tables, and kitchen", async () => {
    vi.mocked(api.get).mockImplementation(async (path: string) => {
      if (path.includes("/tables")) {
        return [{ id: "t1" }, { id: "t2" }, { id: "t3" }];
      }
      if (path.includes("/kitchen/orders")) {
        return [{ id: "k1" }, { id: "k2" }];
      }
      if (path.includes("/orders")) {
        return [
          { id: "o1", status: "SENT" },
          { id: "o2", status: "READY" },
          { id: "o3", status: "CLOSED" },
          { id: "o4", status: "DRAFT" },
        ];
      }
      return [];
    });

    mount("ADMIN");

    await waitFor(() => {
      expect(screen.getByTestId("waiter-count").textContent).toBe("3");
      expect(screen.getByTestId("kitchen-count").textContent).toBe("2");
      // Only SENT and READY are payable orders
      expect(screen.getByTestId("cashier-count").textContent).toBe("2");
    });
  });

  it("disables kitchen query for WAITER", async () => {
    vi.mocked(api.get).mockImplementation(async (path: string) => {
      if (path.includes("/tables")) {
        return [{ id: "t1" }];
      }
      if (path.includes("/orders")) {
        return [{ id: "o1", status: "SENT" }];
      }
      return [];
    });

    mount("WAITER");

    await waitFor(() => {
      expect(screen.getByTestId("waiter-count").textContent).toBe("1");
      expect(screen.getByTestId("cashier-count").textContent).toBe("1");
    });
    expect(screen.getByTestId("kitchen-count").textContent).toBe("none");
  });

  it("disables tables and cashier queries for KITCHEN", async () => {
    vi.mocked(api.get).mockImplementation(async (path: string) => {
      if (path.includes("/kitchen/orders")) {
        return [{ id: "k1" }, { id: "k2" }, { id: "k3" }, { id: "k4" }];
      }
      return [];
    });

    mount("KITCHEN");

    await waitFor(() => {
      expect(screen.getByTestId("kitchen-count").textContent).toBe("4");
    });
    expect(screen.getByTestId("waiter-count").textContent).toBe("none");
    expect(screen.getByTestId("cashier-count").textContent).toBe("none");
  });

  it("disables all queries when role is undefined", () => {
    mount(undefined);

    expect(screen.getByTestId("waiter-count").textContent).toBe("none");
    expect(screen.getByTestId("kitchen-count").textContent).toBe("none");
    expect(screen.getByTestId("cashier-count").textContent).toBe("none");
    expect(api.get).not.toHaveBeenCalled();
  });
});
