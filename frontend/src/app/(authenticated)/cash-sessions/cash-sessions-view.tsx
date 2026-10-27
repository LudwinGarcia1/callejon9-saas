"use client";

import { useEffect, useMemo, useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { Money } from "@/components/shared/money";
import { QueryState } from "@/components/shared/query-state";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useSession } from "@/hooks/use-session";
import { api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";
import { formatShortTime } from "@/lib/format";
import { queryKeys } from "@/lib/query-keys";
import {
  PAYMENT_METHOD_LABELS,
  type CashMovementRequest,
  type CashSessionListResponse,
  type CashSessionResponse,
  type CloseCashSessionRequest,
  type OpenCashSessionRequest,
} from "@/lib/types";

export function CashSessionsView() {
  const client = useQueryClient();
  const { user } = useSession();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [openingFund, setOpeningFund] = useState("0.00");
  const [movementType, setMovementType] = useState<CashMovementRequest["type"]>("DEPOSIT");
  const [movementAmount, setMovementAmount] = useState("");
  const [movementReason, setMovementReason] = useState("");
  const [countedCash, setCountedCash] = useState("");
  const [closureReason, setClosureReason] = useState("");

  const sessionsQuery = useQuery({
    queryKey: queryKeys.cashSessions.all(),
    queryFn: () => api.get<CashSessionListResponse>(endpoints.cashSessions.list()),
    refetchInterval: 15_000,
  });
  const sessions = useMemo(() => sessionsQuery.data?.sessions ?? [], [sessionsQuery.data?.sessions]);
  const ownOpenSession = sessions.find(
    (session) => session.cashierId === user?.userId && session.closedAt === null,
  );

  useEffect(() => {
    if (selectedId || sessions.length === 0) return;
    const preferred = ownOpenSession ?? sessions.find((session) => session.closedAt === null) ?? sessions[0];
    setSelectedId(preferred.id);
  }, [ownOpenSession, selectedId, sessions]);

  const detailQuery = useQuery({
    queryKey: queryKeys.cashSessions.detail(selectedId ?? ""),
    queryFn: () => api.get<CashSessionResponse>(endpoints.cashSessions.detail(selectedId!)),
    enabled: selectedId !== null,
  });
  const session = detailQuery.data;
  const isAdministrator = user?.role === "ADMIN";
  const isOwner = session?.cashierId === user?.userId;
  const canForceClose = isAdministrator && session?.closedAt === null && !isOwner;

  function refreshSession(updated: CashSessionResponse) {
    setSelectedId(updated.id);
    client.setQueryData(queryKeys.cashSessions.detail(updated.id), updated);
    client.invalidateQueries({ queryKey: queryKeys.cashSessions.all() });
    client.invalidateQueries({ queryKey: queryKeys.cashSessions.detail(updated.id) });
  }

  const openMutation = useMutation({
    mutationFn: (request: OpenCashSessionRequest) =>
      api.post<CashSessionResponse>(endpoints.cashSessions.open(), request),
    onSuccess: (created) => {
      refreshSession(created);
      toast.success("Turno de caja abierto.");
    },
    onError: (error) => toast.error(error.message),
  });
  const movementMutation = useMutation({
    mutationFn: ({ sessionId, request }: { sessionId: string; request: CashMovementRequest }) =>
      api.post<CashSessionResponse>(endpoints.cashSessions.movements(sessionId), request),
    onSuccess: (updated) => {
      refreshSession(updated);
      setMovementAmount("");
      setMovementReason("");
      toast.success("Movimiento registrado en la bitácora de caja.");
    },
    onError: (error) => toast.error(error.message),
  });
  const closeMutation = useMutation({
    mutationFn: ({
      sessionId,
      request,
      force,
    }: {
      sessionId: string;
      request: CloseCashSessionRequest;
      force: boolean;
    }) =>
      api.post<CashSessionResponse>(
        force
          ? endpoints.cashSessions.forceClose(sessionId)
          : endpoints.cashSessions.close(sessionId),
        request,
      ),
    onSuccess: (closed) => {
      refreshSession(closed);
      setClosureReason("");
      toast.success("Turno cerrado y arqueo guardado.");
    },
    onError: (error) => toast.error(error.message),
  });

  function submitOpen(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const amount = Number(openingFund);
    if (Number.isFinite(amount) && amount >= 0) {
      openMutation.mutate({ openingFund: amount });
    }
  }

  function submitMovement(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) return;
    const amount = Number(movementAmount);
    if (!Number.isFinite(amount) || amount <= 0 || !movementReason.trim()) return;
    movementMutation.mutate({
      sessionId: session.id,
      request: { type: movementType, amount, reason: movementReason.trim() },
    });
  }

  function submitClose(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) return;
    const amount = Number(countedCash);
    if (!Number.isFinite(amount) || amount < 0) return;
    const force = Boolean(canForceClose);
    closeMutation.mutate({
      sessionId: session.id,
      force,
      request: { countedCash: amount, reason: closureReason.trim() || undefined },
    });
  }

  return (
    <div className="flex flex-1 flex-col gap-6 px-5 py-6 sm:px-8 xl:px-10">
      <header className="flex flex-wrap items-end justify-between gap-4 print:hidden">
        <div>
          <p className="eyebrow">Operación de caja</p>
          <h1 className="font-display text-3xl sm:text-4xl">Turnos de caja</h1>
          <p className="mt-2 max-w-2xl text-sm text-muted-foreground">
            Registra el fondo inicial, retiros y depósitos. El arqueo se calcula con los cobros en efectivo y su cambio.
          </p>
        </div>
        <Button variant="outline" onClick={() => window.print()}>Imprimir resumen</Button>
      </header>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_320px]">
        <main className="flex min-w-0 flex-col gap-5">
          <QueryState
            isLoading={sessionsQuery.isLoading}
            error={sessionsQuery.error}
            isEmpty={sessionsQuery.data !== undefined && sessions.length === 0}
            emptyMessage="Todavía no hay turnos registrados."
          >
            {session ? (
              <SessionDetails session={session} />
            ) : detailQuery.isError ? (
              <section className="flex flex-col items-start gap-3 rounded-lg border bg-card p-5">
                <QueryState isLoading={false} error={detailQuery.error}>{null}</QueryState>
                <Button
                  type="button"
                  variant="outline"
                  disabled={detailQuery.isFetching}
                  onClick={() => void detailQuery.refetch()}
                >
                  {detailQuery.isFetching ? "Cargando…" : "Reintentar"}
                </Button>
              </section>
            ) : detailQuery.isLoading ? (
              <QueryState isLoading>{null}</QueryState>
            ) : selectedId ? (
              <section className="rounded-lg border bg-card p-5">
                <p className="eyebrow">Turno no disponible</p>
                <p className="mt-2 text-sm text-muted-foreground">
                  No se pudo encontrar el turno seleccionado. Elige otro de la lista.
                </p>
              </section>
            ) : (
              <section className="rounded-lg border bg-card p-5">
                <p className="eyebrow">Selecciona un turno</p>
                <p className="mt-2 text-sm text-muted-foreground">
                  El detalle del turno aparecerá aquí con su arqueo y movimientos.
                </p>
              </section>
            )}
          </QueryState>

          {session?.closedAt === null && (
            <div className="grid gap-5 lg:grid-cols-2 print:hidden">
              <form onSubmit={submitMovement} className="flex flex-col gap-3 rounded-lg border bg-card p-5">
                <div>
                  <p className="eyebrow">Movimiento manual</p>
                  <h2 className="mt-1 font-display text-2xl">Depósito o retiro</h2>
                </div>
                <label className="text-sm">Tipo
                  <select
                    aria-label="Tipo de movimiento"
                    value={movementType}
                    onChange={(event) => setMovementType(event.target.value as CashMovementRequest["type"])}
                    className="mt-1 h-10 w-full rounded-md border bg-background px-3"
                  >
                    <option value="DEPOSIT">Depósito</option>
                    <option value="WITHDRAWAL">Retiro</option>
                  </select>
                </label>
                <label className="text-sm">Monto
                  <Input aria-label="Monto del movimiento" type="number" min="0.01" step="0.01" required value={movementAmount} onChange={(event) => setMovementAmount(event.target.value)} />
                </label>
                <label className="text-sm">Motivo obligatorio
                  <Input aria-label="Motivo del movimiento" maxLength={250} required value={movementReason} onChange={(event) => setMovementReason(event.target.value)} />
                </label>
                <Button type="submit" disabled={movementMutation.isPending}>
                  {movementMutation.isPending ? "Guardando…" : "Registrar movimiento"}
                </Button>
              </form>

              {(isOwner || canForceClose) && (
                <form onSubmit={submitClose} className="flex flex-col gap-3 rounded-lg border bg-card p-5">
                  <div>
                    <p className="eyebrow">Arqueo final</p>
                    <h2 className="mt-1 font-display text-2xl">{canForceClose ? "Cierre administrativo" : "Cerrar turno"}</h2>
                  </div>
                  <label className="text-sm">Efectivo contado
                    <Input aria-label="Efectivo contado" type="number" min="0" step="0.01" required value={countedCash} onChange={(event) => setCountedCash(event.target.value)} />
                  </label>
                  <label className="text-sm">
                    {canForceClose ? "Motivo obligatorio" : "Motivo (opcional)"}
                    <Input
                      aria-label="Motivo del cierre"
                      maxLength={250}
                      required={Boolean(canForceClose)}
                      value={closureReason}
                      onChange={(event) => setClosureReason(event.target.value)}
                    />
                  </label>
                  <Button type="submit" variant={canForceClose ? "destructive" : "brand"} disabled={closeMutation.isPending}>
                    {closeMutation.isPending ? "Guardando…" : canForceClose ? "Forzar cierre" : "Cerrar y guardar arqueo"}
                  </Button>
                </form>
              )}
            </div>
          )}
        </main>

        <aside className="flex flex-col gap-4 print:hidden">
          {!ownOpenSession && (
            <form onSubmit={submitOpen} className="flex flex-col gap-3 rounded-lg border bg-card p-5">
              <div>
                <p className="eyebrow">Inicio de turno</p>
                <h2 className="mt-1 font-display text-2xl">Abrir caja</h2>
              </div>
              <label className="text-sm">Fondo inicial
                <Input aria-label="Fondo inicial" type="number" min="0" step="0.01" required value={openingFund} onChange={(event) => setOpeningFund(event.target.value)} />
              </label>
              <Button type="submit" variant="brand" disabled={openMutation.isPending}>
                {openMutation.isPending ? "Abriendo…" : "Abrir turno"}
              </Button>
            </form>
          )}

          <section className="rounded-lg border bg-card p-4">
            <div className="mb-3 flex items-center justify-between">
              <div>
                <p className="eyebrow">Historial</p>
                <h2 className="font-display text-xl">Sesiones</h2>
              </div>
              <span className="font-mono text-xs text-muted-foreground">{sessions.length}</span>
            </div>
            <ul className="flex max-h-[540px] flex-col overflow-y-auto">
              {sessions.map((item) => {
                const active = item.closedAt === null;
                return (
                  <li key={item.id}>
                    <button
                      type="button"
                      onClick={() => setSelectedId(item.id)}
                      aria-current={selectedId === item.id ? "true" : undefined}
                      className={`focus-sala flex w-full flex-col gap-1 border-t px-2 py-3 text-left ${selectedId === item.id ? "bg-brand-tint" : "hover:bg-accent/50"}`}
                    >
                      <span className="flex w-full justify-between gap-2 text-sm font-medium">
                        <span className="truncate">{item.cashierName}</span>
                        <span className={active ? "text-emerald-700" : "text-muted-foreground"}>{active ? "Abierto" : "Cerrado"}</span>
                      </span>
                      <span className="flex w-full justify-between gap-2 text-xs text-muted-foreground">
                        <span>{new Date(item.openedAt).toLocaleDateString("es-MX")} · {formatShortTime(item.openedAt)}</span>
                        {item.difference !== null && <Money amount={item.difference} />}
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>
          </section>
        </aside>
      </div>
    </div>
  );
}

function SessionDetails({ session }: { session: CashSessionResponse }) {
  const isOpen = session.closedAt === null;
  return (
    <section className="flex flex-col gap-5 rounded-lg border bg-card p-5 sm:p-6 print:border-0 print:p-0">
      <header className="flex flex-wrap items-start justify-between gap-4 border-b pb-4">
        <div>
          <p className="eyebrow">{isOpen ? "Turno abierto" : "Turno cerrado"} · {session.cashierName}</p>
          <h2 className="mt-1 font-display text-3xl">{new Date(session.openedAt).toLocaleDateString("es-MX")}</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Apertura {formatShortTime(session.openedAt)}
            {session.closedAt ? ` · Cierre ${formatShortTime(session.closedAt)}` : ""}
          </p>
        </div>
        <span className="rounded-full border px-3 py-1 text-xs font-medium">
          {isOpen ? "En curso" : "Arqueo guardado"}
        </span>
      </header>

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <CashMetric label="Fondo inicial" amount={session.openingFund} />
        <CashMetric label="Efectivo esperado" amount={session.expectedCash} />
        <CashMetric label="Efectivo contado" amount={session.countedCash} />
        <CashMetric label="Diferencia" amount={session.difference} />
      </div>

      <div className="grid gap-5 lg:grid-cols-2">
        <div>
          <p className="eyebrow mb-2">Resumen por método de pago</p>
          {session.paymentSummary.length === 0 ? (
            <p className="rounded-md border border-dashed p-4 text-sm text-muted-foreground">Todavía no hay cobros asociados.</p>
          ) : (
            <ul className="divide-y rounded-md border">
              {session.paymentSummary.map((payment) => (
                <li key={payment.method} className="flex items-center justify-between gap-3 p-3 text-sm">
                  <span>{PAYMENT_METHOD_LABELS[payment.method]} · {payment.paymentCount} pagos</span>
                  <Money amount={payment.appliedAmount} className="font-medium" />
                </li>
              ))}
            </ul>
          )}
        </div>

        <div>
          <p className="eyebrow mb-2">Movimientos de efectivo</p>
          {session.movements.length === 0 ? (
            <p className="rounded-md border border-dashed p-4 text-sm text-muted-foreground">Sin depósitos ni retiros.</p>
          ) : (
            <ul className="divide-y rounded-md border">
              {session.movements.map((movement) => (
                <li key={movement.id} className="flex items-start justify-between gap-3 p-3 text-sm">
                  <span>
                    <span className="block font-medium">{movement.type === "DEPOSIT" ? "Depósito" : "Retiro"} · {movement.reason}</span>
                    <span className="text-xs text-muted-foreground">{formatShortTime(movement.createdAt)}</span>
                  </span>
                  <Money amount={movement.amount} className={movement.type === "DEPOSIT" ? "text-emerald-700" : "text-destructive"} />
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>

      {session.closureReason && (
        <p className="border-t pt-3 text-sm"><span className="font-medium">Motivo del cierre:</span> {session.closureReason}</p>
      )}
    </section>
  );
}

function CashMetric({ label, amount }: { label: string; amount: number | null }) {
  return (
    <div className="rounded-md border bg-surface-alt p-3">
      <p className="eyebrow">{label}</p>
      {amount === null ? (
        <span className="mt-1 block font-display text-2xl text-muted-foreground">Pendiente</span>
      ) : (
        <Money amount={amount} className="mt-1 block font-display text-2xl" />
      )}
    </div>
  );
}
