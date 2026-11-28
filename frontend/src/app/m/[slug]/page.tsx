import Link from "next/link";
import { UtensilsIcon, QrCodeIcon } from "lucide-react";

interface PageProps {
  params: Promise<{ slug: string }>;
}

export default async function DinerRestaurantPage({ params }: PageProps) {
  const { slug } = await params;
  const formattedSlug = slug.replace(/-/g, " ");

  const sampleTables = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12];

  return (
    <main className="min-h-screen bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-amber-500/10 via-neutral-950 to-neutral-950 text-neutral-100 flex flex-col justify-between p-6 max-w-2xl mx-auto">
      <div className="pt-8 text-center space-y-4">
        <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-amber-500/15 text-amber-500 border border-amber-500/30 mb-2 shadow-lg shadow-amber-500/10">
          <UtensilsIcon className="w-8 h-8" />
        </div>
        <div>
          <span className="text-xs font-black tracking-wider text-amber-400 uppercase bg-amber-500/10 border border-amber-500/20 px-3 py-1 rounded-full">
            RestoHub · Menú Digital & Auto-pedido
          </span>
          <h1 className="text-3xl sm:text-4xl font-extrabold capitalize tracking-tight mt-3">
            {formattedSlug}
          </h1>
          <p className="text-sm text-neutral-400 mt-2 max-w-md mx-auto">
            Selecciona tu número de mesa para ver la carta gastronómica y ordenar directamente a cocina.
          </p>
        </div>
      </div>

      <div className="my-8">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-sm font-bold text-neutral-200 flex items-center gap-2">
            <QrCodeIcon className="w-4 h-4 text-amber-400" />
            ¿En qué mesa te encuentras?
          </h2>
          <span className="text-xs text-amber-400/80 font-medium">Toca para abrir menú</span>
        </div>

        <div className="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-6 gap-3">
          {sampleTables.map((tableNum) => (
            <Link
              key={tableNum}
              href={`/m/${slug}/${tableNum}`}
              className="group flex flex-col items-center justify-center p-4 rounded-2xl bg-neutral-900/90 border border-neutral-800 hover:border-amber-500/80 hover:bg-neutral-850 hover:shadow-lg hover:shadow-amber-500/10 transition-all text-center active:scale-95"
            >
              <span className="text-xs text-neutral-400 group-hover:text-amber-400 font-medium transition-colors">Mesa</span>
              <span className="text-2xl font-black text-neutral-100 group-hover:text-white transition-colors">{tableNum}</span>
            </Link>
          ))}
        </div>
      </div>

      <div className="pb-6 text-center text-xs text-neutral-500 border-t border-neutral-800/80 pt-6">
        <p>Potenciado por <strong className="text-neutral-300">RestoHub</strong> · Plataforma Gastronómica Multi-Tenant</p>
      </div>
    </main>
  );
}
