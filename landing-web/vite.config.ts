import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// Em desenvolvimento, a página roda no Vite (3008) e /api/landing vai para o servidor Node (8088).
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');

  return {
    plugins: [react(), tailwindcss()],
    server: {
      port: 3008,
      strictPort: true,
      proxy: {
        '/api/landing': { target: env.SERVIDOR_PROXY_ALVO || 'http://localhost:8088', changeOrigin: true },
      },
    },
  };
});
