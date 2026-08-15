import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import { headers } from "next/headers";
import "./globals.css";
import { Toaster } from "@/components/ui/sonner";
import { PATHNAME_HEADER } from "@/middleware";
import { Providers } from "./providers";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "Callejon 9",
  description: "Plataforma SaaS multi-restaurante Callejon 9",
};

/**
 * Cocina es la unica pantalla oscura, y lo decide el servidor.
 *
 * Marcarla despues del montaje con un efecto no llega a tiempo al primer
 * pintado: al cargar o refrescar /kitchen —que es como arranca una pantalla
 * de cocina y como se recupera tras un reinicio— el navegador pintaba el tema
 * claro entero y lo invertia al hidratar. En una tablet modesta eso es un
 * destello blanco a brillo completo, en la unica pantalla pensada para leerse
 * de lejos en un local iluminado.
 */
function isDarkRoute(pathname: string): boolean {
  return pathname.startsWith("/kitchen");
}

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  // Solo existe en rutas cubiertas por el middleware; en /login no, y ahi la
  // condicion es falsa, que es lo correcto.
  const pathname = (await headers()).get(PATHNAME_HEADER) ?? "";

  return (
    // Las variables de fuente van en <html> y no en <body>: globals.css aplica
    // la familia al elemento raiz, y una custom property definida en el body no
    // es visible desde su padre. Con ellas en el body, `html { font-sans }` no
    // resolvia y toda la aplicacion caia a la fuente por defecto del navegador.
    <html
      lang="es"
      className={`${geistSans.variable} ${geistMono.variable}${
        isDarkRoute(pathname) ? " dark" : ""
      }`}
    >
      <body className="antialiased">
        <Providers>{children}</Providers>
        <Toaster />
      </body>
    </html>
  );
}
