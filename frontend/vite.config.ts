// Vite build configuration (scaffolding skeleton).
// The app itself is implemented in a later feature.
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // During local dev, API calls are proxied to the gateway.
    proxy: {
      "/api": {
        target: process.env.GATEWAY_URL ?? "http://localhost:8081",
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: "dist",
  },
});
