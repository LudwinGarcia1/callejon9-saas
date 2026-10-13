import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

export default defineConfig({
  esbuild: { jsx: "automatic" },
  test: {
    coverage: {
      provider: "v8",
      include: ["src/lib/api.ts", "src/lib/query-keys.ts", "src/lib/tenant-theme.ts", "src/hooks/use-session.ts",
        "src/components/shared/field-error.tsx", "src/components/shared/query-state.tsx",
        "src/app/**/order-view.tsx"],
      thresholds: { statements: 85, branches: 75, functions: 70, lines: 85 },
    },
  },
  resolve: { alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) } },
});
