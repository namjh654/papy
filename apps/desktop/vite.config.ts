import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Tauri 개발 서버 규칙: 고정 포트, 실패 시 다른 포트로 넘어가지 않음, src-tauri 변경은 감시하지 않음.
export default defineConfig({
  plugins: [react()],
  clearScreen: false,
  server: {
    port: 1420,
    strictPort: true,
    watch: { ignored: ["**/src-tauri/**"] },
  },
});
