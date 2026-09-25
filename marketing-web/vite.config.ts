import { fileURLToPath } from 'node:url';
import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// Caminho base e API vêm de configuração: a casca hoje serve o módulo em /modulos/marketing/,
// mas a decisão é de outro grupo e pode mudar (norteador, seção 9).
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');

  return {
    base: env.VITE_BASE_PATH || '/modulos/marketing/',
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
    },
    server: {
      port: 3007,
      strictPort: true,
      proxy: {
        '/api/marketing': {
          target: env.API_PROXY_ALVO || 'http://localhost:8087',
          changeOrigin: true,
        },
      },
    },
  };
});
