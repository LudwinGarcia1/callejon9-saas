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
    <main className="min-h-screen bg-neutral-950 text-neutral-100 flex flex-col justify-between p-6 max-w-md mx-auto">
      <div className="pt-8 text-center space-y-4">
        <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-amber-500/10 text-amber-500 border border-amber-500/20 mb-2">
          <UtensilsIcon className="w-8 h-8" />
        </div>
        <div>
          <span className="text-xs font-semibold tracking-wider text-amber-400 uppercase">
            Menú Digital & Auto-pedido
          </span>
          <h1 className="text-3xl font-extrabold capitalize tracking-tight mt-1">
            {formattedSlug}
          </h1>
          <p className="text-sm text-neutral-400 mt-2">
            Selecciona tu número de mesa para ver la carta y ordenar directamente a cocina.
          </p>
        </div>
      </div>

      <div className="my-8">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-sm font-semibold text-neutral-300 flex items-center gap-2">
            <QrCodeIcon className="w-4 h-4 text-amber-400" />
            ¿En qué mesa te encuentras?
          </h2>
          <span className="text-xs text-neutral-500">Mesa física</span>
        </div>

        <div className="grid grid-cols-4 gap-3">
          {sampleTables.map((tableNum) => (
            <Link
              key={tableNum}
              href={`/m/${slug}/${tableNum}`}
              className="flex flex-col items-center justify-center p-4 rounded-xl bg-neutral-900 border border-neutral-800 hover:border-amber-500 hover:bg-neutral-800/80 transition-all text-center active:scale-95"
            >
              <span className="text-xs text-neutral-500 font-medium">Mesa</span>
              <span className="text-xl font-bold text-neutral-100">{tableNum}</span>
            </Link>
          ))}
        </div>
      </div>

      <div className="pb-6 text-center text-xs text-neutral-500 border-t border-neutral-900 pt-6">
        <p>Potenciado por <strong>RestoHub</strong> · Plataforma Gastronómica</p>
      </div>
    </main>
  );
}
