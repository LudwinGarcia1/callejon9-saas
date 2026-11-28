import { afterEach, beforeEach, expect, it, vi } from "vitest";

import {
  connectKitchenChannel,
  connectWaiterCallChannel,
  kitchenEventOrderId,
  kitchenTopic,
  parseKitchenEvent,
  parseWaiterCall,
  realtimeUrl,
  RECONNECT_DELAY_MS,
  waiterCallsTopic,
} from "@/lib/realtime";
import type { KitchenItemResponse, OrderResponse, WaiterCallResponse } from "@/lib/types";

const stomp = vi.hoisted(() => ({
  config: null as null | Record<string, unknown>,
  subscribe: vi.fn(),
  activate: vi.fn(),
  deactivate: vi.fn(),
}));
vi.mock("@stomp/stompjs", () => ({
  Client: vi.fn().mockImplementation((config: Record<string, unknown>) => {
    stomp.config = config;
    return { subscribe: stomp.subscribe, activate: stomp.activate, deactivate: stomp.deactivate };
  }),
}));

const TENANT = "11111111-2222-3333-4444-555555555555";
const item: KitchenItemResponse = { id: "i1", orderId: "o1", productId: "p1", productName: "Sopa",
  quantity: 1, kitchenStatus: "READY", notes: null };
const order = { id: "o2", folio: "O-2", status: "SENT", items: [] } as unknown as OrderResponse;

beforeEach(() => {
  vi.clearAllMocks();
  stomp.config = null;
  vi.stubGlobal("window", { location: { protocol: "https:", host: "restohub.azurewebsites.net" } });
});
afterEach(() => vi.unstubAllGlobals());

it("subscribes to the tenant kitchen topic, the one KitchenRealtimeEventPublisher uses", () => {
  expect(kitchenTopic(TENANT)).toBe(`/topic/tenant.${TENANT}.kitchen`);
});

it("opens the channel on the frontend origin, never on the API host", () => {
  expect(realtimeUrl({ protocol: "https:", host: "restohub.azurewebsites.net" }))
    .toBe("wss://restohub.azurewebsites.net/ws");
  expect(realtimeUrl({ protocol: "http:", host: "localhost:3000" })).toBe("ws://localhost:3000/ws");
});

it("finds the order of either event shape", () => {
  expect(kitchenEventOrderId(item)).toBe("o1");
  expect(kitchenEventOrderId(order)).toBe("o2");
});

it("ignores frames that are not an event", () => {
  expect(parseKitchenEvent({ body: JSON.stringify(item) })).toEqual(item);
  expect(parseKitchenEvent({ body: "no es json" })).toBeNull();
  expect(parseKitchenEvent({ body: "null" })).toBeNull();
  expect(parseKitchenEvent({ body: JSON.stringify({ folio: "sin id" }) })).toBeNull();
});

it("subscribes on every connect, delivers events and reports the connection state", () => {
  const onEvent = vi.fn();
  const onConnectionChange = vi.fn();
  const close = connectKitchenChannel({ tenantId: TENANT, onEvent, onConnectionChange });

  expect(stomp.config).toMatchObject({
    brokerURL: "wss://restohub.azurewebsites.net/ws",
    reconnectDelay: RECONNECT_DELAY_MS,
  });
  expect(stomp.activate).toHaveBeenCalledTimes(1);

  const config = stomp.config as { onConnect: () => void; onWebSocketClose: () => void };
  config.onConnect();
  expect(onConnectionChange).toHaveBeenLastCalledWith(true);
  expect(stomp.subscribe).toHaveBeenCalledWith(`/topic/tenant.${TENANT}.kitchen`, expect.any(Function));

  const deliver = stomp.subscribe.mock.calls[0][1] as (message: { body: string }) => void;
  deliver({ body: JSON.stringify(item) });
  deliver({ body: "basura" });
  expect(onEvent).toHaveBeenCalledTimes(1);
  expect(onEvent).toHaveBeenCalledWith(item);

  config.onWebSocketClose();
  expect(onConnectionChange).toHaveBeenLastCalledWith(false);
  config.onConnect();
  expect(stomp.subscribe).toHaveBeenCalledTimes(2);

  close();
  expect(stomp.deactivate).toHaveBeenCalledTimes(1);
  expect(onConnectionChange).toHaveBeenLastCalledWith(false);
});

const call: WaiterCallResponse = { tableId: "t7", tableNumber: 7, calledAt: "2026-11-28T20:00:00Z" };

it("subscribes to the waiter calls topic, the one WaiterCallRealtimeEventPublisher uses", () => {
  expect(waiterCallsTopic(TENANT)).toBe(`/topic/tenant.${TENANT}.waiter-calls`);
});

it("accepts only frames shaped like a waiter call", () => {
  expect(parseWaiterCall({ body: JSON.stringify(call) })).toEqual(call);
  expect(parseWaiterCall({ body: JSON.stringify(item) })).toBeNull();
  expect(parseWaiterCall({ body: JSON.stringify({ ...call, tableNumber: "7" }) })).toBeNull();
  expect(parseWaiterCall({ body: "no es json" })).toBeNull();
  expect(parseWaiterCall({ body: "null" })).toBeNull();
});

it("delivers waiter calls from the restaurant topic and ignores other frames", () => {
  const onEvent = vi.fn();
  connectWaiterCallChannel({ tenantId: TENANT, onEvent, onConnectionChange: vi.fn() });

  (stomp.config as { onConnect: () => void }).onConnect();
  expect(stomp.subscribe).toHaveBeenCalledWith(`/topic/tenant.${TENANT}.waiter-calls`, expect.any(Function));

  const deliver = stomp.subscribe.mock.calls[0][1] as (message: { body: string }) => void;
  deliver({ body: JSON.stringify(call) });
  deliver({ body: JSON.stringify(item) });
  expect(onEvent).toHaveBeenCalledTimes(1);
  expect(onEvent).toHaveBeenCalledWith(call);
});