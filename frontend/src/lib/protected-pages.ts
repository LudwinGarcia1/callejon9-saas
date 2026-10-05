export const PROTECTED_PAGE_PREFIXES = [
  "/admin",
  "/analytics",
  "/cashier",
  "/history",
  "/inventory",
  "/kitchen",
  "/platform",
  "/waiter",
] as const;

export function isProtectedPagePath(pathname: string): boolean {
  return PROTECTED_PAGE_PREFIXES.some(
    (prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`),
  );
}

export function loginPathFor(pathname: string): string {
  return `/login?${new URLSearchParams({ next: pathname }).toString()}`;
}
