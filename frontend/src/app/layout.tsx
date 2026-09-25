import type { Metadata } from "next";
import {
  Geist,
  Geist_Mono,
  Instrument_Serif,
  Space_Grotesk,
} from "next/font/google";
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

const instrumentSerif = Instrument_Serif({
  variable: "--font-instrument-serif",
  subsets: ["latin"],
  weight: "400",
  style: ["normal", "italic"],
});

const spaceGrotesk = Space_Grotesk({
  variable: "--font-space-grotesk",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "Callejon 9",
  description: "Plataforma SaaS multi-restaurante Callejon 9",
};

function isDarkRoute(pathname: string): boolean {
  return pathname.startsWith("/kitchen");
}

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const pathname = (await headers()).get(PATHNAME_HEADER) ?? "";

  return (
    <html
      lang="es"
      className={`${geistSans.variable} ${geistMono.variable} ${
        instrumentSerif.variable
      } ${spaceGrotesk.variable}${
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