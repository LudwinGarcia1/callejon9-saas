import { cn } from "@/lib/utils";

/*
 * Réplica de la app móvil de meseros (mobile/src/screens/OrderScreen.tsx) en
 * la pantalla de una mesa ocupada: la orden abierta y los productos que se
 * le agregan. Usa la paleta de la app móvil, no la del sitio. Decorativa.
 */

const ITEMS = [
  { name: "Guacamole con totopos", price: "$95.00", qty: 0 },
  { name: "Margarita", price: "$135.00", qty: 2 },
  { name: "Flan napolitano", price: "$75.00", qty: 1 },
];

function Stepper({ value }: { value: number }) {
  return (
    <div className="flex items-center gap-1">
      <span className="flex size-6 items-center justify-center rounded-[6px] border border-[#E0DBD1] bg-white text-[13px] text-[#2B2723]">
        −
      </span>
      <span className="w-4 text-center text-[11px] font-bold text-[#2B2723]">{value}</span>
      <span className="flex size-6 items-center justify-center rounded-[6px] border border-[#E0DBD1] bg-white text-[13px] text-[#2B2723]">
        +
      </span>
    </div>
  );
}

export function PhonePreview({ className }: { className?: string }) {
  return (
    <div
      aria-hidden="true"
      className={cn(
        "w-[248px] rounded-[38px] border-[9px] border-mk-ink bg-mk-ink shadow-[0_40px_80px_-30px_rgba(13,13,13,0.55)]",
        className,
      )}
    >
      <div className="relative overflow-hidden rounded-[29px] bg-[#F1EEE6]">
        {/* Barra de estado */}
        <div className="flex h-7 items-center justify-between px-5 text-[9px] font-semibold text-[#2B2723]">
          <span>9:41</span>
          <span className="h-4 w-16 rounded-full bg-mk-ink" />
          <span>100%</span>
        </div>

        <div className="space-y-2.5 px-3.5 pt-1 pb-3.5">
          <div>
            <p className="text-[10px] font-semibold text-[#776F66]">‹ Mesas</p>
            <p className="text-[17px] leading-tight font-extrabold text-[#2B2723]">Mesa 4</p>
          </div>

          <div className="space-y-1 rounded-[8px] border border-[#E0DBD1] bg-[#FBF9F5] p-2.5">
            <div className="flex justify-between text-[10px]">
              <span className="font-semibold text-[#2B2723]">Orden ORD-0412</span>
              <span className="text-[#776F66]">6 comensales</span>
            </div>
            <div className="flex justify-between text-[9.5px] text-[#776F66]">
              <span>2 × Arrachera 300 g</span>
              <span>$640.00</span>
            </div>
            <div className="flex justify-between text-[9.5px] text-[#776F66]">
              <span>3 × Agua de horchata</span>
              <span>$135.00</span>
            </div>
            <div className="flex justify-between border-t border-[#E0DBD1] pt-1 text-[10px] font-semibold text-[#2B2723]">
              <span>Cuenta actual</span>
              <span>$775.00</span>
            </div>
            <p className="text-[9px] leading-snug text-[#776F66]">
              Ya está en cocina: lo que agregues aparecerá ahí como pendiente.
            </p>
          </div>

          <p className="text-[9.5px] text-[#776F66]">Agregar más productos</p>
          <div className="space-y-1.5">
            {ITEMS.map((item) => (
              <div
                key={item.name}
                className="flex items-center justify-between rounded-[8px] border border-[#E0DBD1] bg-[#FBF9F5] px-2.5 py-2"
              >
                <div>
                  <p className="text-[10.5px] font-semibold text-[#2B2723]">{item.name}</p>
                  <p className="text-[9.5px] text-[#776F66]">{item.price}</p>
                </div>
                <Stepper value={item.qty} />
              </div>
            ))}
          </div>

          <div className="space-y-2 border-t border-[#E0DBD1] pt-2.5">
            <div className="flex items-baseline justify-between">
              <span className="text-[9.5px] text-[#776F66]">3 productos nuevos</span>
              <span className="text-[15px] font-extrabold text-[#2B2723]">$345.00</span>
            </div>
            <div className="rounded-[8px] bg-[#2B2723] py-2 text-center text-[10.5px] font-semibold text-[#F7F4EE]">
              Agregar a la orden
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
