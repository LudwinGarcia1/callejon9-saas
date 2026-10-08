import { QueryClient } from "@tanstack/react-query";
import { describe, expect, it } from "vitest";

import { queryKeys } from "@/lib/query-keys";

describe("query cache boundaries", () => {
  it("keeps active and administrative catalog results separate", () => {
    const client = new QueryClient();
    try {
      client.setQueryData(queryKeys.products.all(), ["active"]);
      client.setQueryData(queryKeys.products.all(true), ["active", "inactive"]);
      expect(client.getQueryData(queryKeys.products.all(false))).toEqual(["active"]);
      expect(client.getQueryData(queryKeys.products.all(true))).toEqual(["active", "inactive"]);
      expect(queryKeys.tables.all(true)).not.toEqual(queryKeys.tables.all());
      expect(queryKeys.inventory.items(true)).not.toEqual(queryKeys.inventory.items());
    } finally { client.clear(); }
  });

  it("invalidates order details with their list without affecting other resources", async () => {
    const client = new QueryClient();
    try {
      client.setQueryData(queryKeys.orders.all(), []);
      client.setQueryData(queryKeys.orders.detail("order-1"), { id: "order-1" });
      client.setQueryData(queryKeys.tables.all(), []);
      await client.invalidateQueries({ queryKey: queryKeys.orders.all() });
      expect(client.getQueryState(queryKeys.orders.detail("order-1"))?.isInvalidated).toBe(true);
      expect(client.getQueryState(queryKeys.orders.all())?.isInvalidated).toBe(true);
      expect(client.getQueryState(queryKeys.tables.all())?.isInvalidated).toBe(false);
    } finally { client.clear(); }
  });

  it("changes cache identity when date ranges or inventory filters change", () => {
    expect(queryKeys.sales.history("2026-10-01", "2026-10-08"))
      .not.toEqual(queryKeys.sales.history("2026-10-02", "2026-10-08"));
    expect(queryKeys.analytics.summary("2026-10-01", "2026-10-08"))
      .not.toEqual(queryKeys.analytics.summary("2026-10-01", "2026-10-09"));
    expect(queryKeys.inventory.movements("a", "b", "item-1"))
      .not.toEqual(queryKeys.inventory.movements("a", "b", "item-2"));
    expect(queryKeys.orders.detail("order-1")).not.toEqual(queryKeys.orders.detail("order-2"));
  });
});
