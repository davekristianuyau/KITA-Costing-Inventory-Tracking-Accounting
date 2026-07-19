/// <reference types="vitest/config" />
// Vite + Vitest configuration. During local dev, /auth and /api are proxied to the edge gateway
// (the single public entry point); in the container, nginx does the same proxying.
import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

const edge = process.env.EDGE_URL ?? "http://localhost:8080";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/auth": { target: edge, changeOrigin: true },
      "/api": { target: edge, changeOrigin: true },
    },
  },
  build: {
    outDir: "dist",
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: "./tests/setup.ts",
    include: ["tests/**/*.test.{ts,tsx}"],
  },
});
