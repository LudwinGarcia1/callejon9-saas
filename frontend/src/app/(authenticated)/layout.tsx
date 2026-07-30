import { AppSidebar } from "@/components/layout/app-sidebar";

/**
 * Layout compartido por las pantallas que requieren sesion iniciada
 * (mesero, cocina, caja, administracion y plataforma).
 */
export default function AuthenticatedLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="flex min-h-screen">
      <AppSidebar />
      <main className="flex-1 p-6">{children}</main>
    </div>
  );
}
