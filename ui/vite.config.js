import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ command, mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const polarionUrl = env.VITE_BASE_URL || 'http://localhost';

  if (command === 'serve') {
    return {
      plugins: [react()],
      server: {
        proxy: {
          '/polarion/xml-repair/rest': {
            target: polarionUrl,
            changeOrigin: true,
          },
          '/polarion/rest': {
            target: polarionUrl,
            changeOrigin: true,
          },
          '/polarion/ria': {
            target: polarionUrl,
            changeOrigin: true,
          },
          '/polarion/icons': {
            target: polarionUrl,
            changeOrigin: true,
          },
        },
      },
    };
  }

  return {
    plugins: [react()],
    base: '/polarion/xml-repair-app/ui/app/',
    build: {
      outDir: './dist/app',
      emptyOutDir: true,
    },
  };
});
