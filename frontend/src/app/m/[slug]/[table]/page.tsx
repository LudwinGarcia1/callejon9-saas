import { DinerMenuView } from "./diner-menu-view";

interface PageProps {
  params: Promise<{
    slug: string;
    table: string;
  }>;
}

export default async function DinerTableMenuPage({ params }: PageProps) {
  const { slug, table } = await params;

  return <DinerMenuView slug={slug} tableNumber={table} />;
}
