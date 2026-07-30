import { OrderView } from "./order-view";

interface WaiterOrderPageProps {
  params: Promise<{ id: string }>;
}

export default async function WaiterOrderPage({ params }: WaiterOrderPageProps) {
  const { id } = await params;
  return <OrderView orderId={id} />;
}
