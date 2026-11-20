import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // Must match FRONTEND_ORIGIN in the gateway's CORS config.
    port: 5173,
    strictPort: true,
  },
})
