/**
 * Layout compartido por las pantallas que requieren sesion iniciada
 * (mesero, cocina, caja, administracion y plataforma). El contenido real de
 * la barra lateral (navegacion por rol, datos del usuario) llega en una
 * tarea posterior.
 */
export default function AuthenticatedLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="flex min-h-screen">
      <aside className="w-64 shrink-0 border-r p-4">Barra lateral</aside>
      <main className="flex-1 p-6">{children}</main>
    </div>
  );
}
