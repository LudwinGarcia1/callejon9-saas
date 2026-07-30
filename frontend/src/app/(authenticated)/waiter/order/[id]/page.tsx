interface WaiterOrderPageProps {
  params: Promise<{ id: string }>;
}

export default async function WaiterOrderPage({ params }: WaiterOrderPageProps) {
  const { id } = await params;
  return <h1>Orden {id}</h1>;
}
