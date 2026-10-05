const LOCAL_ORIGIN = "https://callejon9.invalid";

/** Devuelve solo rutas internas seguras; la autorización continúa en Spring. */
export function postLoginPath(nextPath: string | null, fallbackPath: string): string {
  if (!nextPath || nextPath.trim() !== nextPath) {
    return fallbackPath;
  }

  try {
    const decodedPath = decodeURIComponent(nextPath);
    if (
      !decodedPath.startsWith("/") ||
      !nextPath.startsWith("/") ||
      decodedPath.startsWith("//") ||
      decodedPath.includes("\\") ||
      /[\u0000-\u001f]/.test(decodedPath)
    ) {
      return fallbackPath;
    }

    const target = new URL(nextPath, LOCAL_ORIGIN);
    return target.origin === LOCAL_ORIGIN ? `${target.pathname}${target.search}${target.hash}` : fallbackPath;
  } catch {
    return fallbackPath;
  }
}
