import type { Metadata } from "next";
import { Geist, Geist_Mono, Instrument_Serif, Space_Grotesk } from "next/font/google";
import "./globals.css";
import { Toaster } from "@/components/ui/sonner";
import { Providers } from "./providers";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

/**
 * Tipografia de display predeterminada del sistema: titulos de pantalla,
 * numero de mesa, totales y nombre del restaurante. La itálica solo la usa la
 * frase del panel de marca en login.
 */
const instrumentSerif = Instrument_Serif({
  variable: "--font-instrument-serif",
  subsets: ["latin"],
  weight: "400",
  style: ["normal", "italic"],
});

/**
 * Segunda opcion de display elegible por restaurante. La lista es cerrada a
 * proposito (Instrument Serif, Space Grotesk, Geist): una fuente arbitraria
 * romperia la jerarquia de numeros de mesa y totales, que es donde vive la
 * legibilidad del sistema.
 */
// Sin `weight`: Space Grotesk es una fuente variable y Google sirve el rango
// completo en un solo archivo. Pedirle pesos sueltos genera un CSS que apunta a
// instancias estaticas inexistentes y el build no resuelve esos modulos.
const spaceGrotesk = Space_Grotesk({
  variable: "--font-space-grotesk",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "Callejon 9",
  description: "Plataforma SaaS multi-restaurante Callejon 9",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    // Las variables de fuente van en <html>, no en <body>: `globals.css`
    // declara `--font-display-family` sobre `:root` a partir de ellas, y una
    // custom property solo puede resolver otra que exista en el mismo elemento.
    <html
      lang="es"
      className={`${geistSans.variable} ${geistMono.variable} ${instrumentSerif.variable} ${spaceGrotesk.variable}`}
    >
      <body className="antialiased">
        <Providers>{children}</Providers>
        <Toaster />
      </body>
    </html>
  );
}
