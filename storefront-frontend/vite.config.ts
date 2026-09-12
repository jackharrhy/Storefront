import { defineConfig } from "vitest/config";
import { loadEnv } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  return {
    base: "/storefront/",
    plugins: [react()],
    server: {
      proxy: {
        "/storefront/api": {
          target: env.API_PROXY_TARGET || "http://127.0.0.1:7000",
          rewrite: (path) => path.replace(/^\/storefront\/api/, ""),
        },
      },
    },
    test: { environment: "jsdom", restoreMocks: true },
  };
});
