import { copyFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

// Copy react-sbb-polarion's breadcrumb bridge next to the built app, where RSP's BreadcrumbInjector
// looks for it (it resolves the URL relative to the running app). The file cannot be bundled: it runs
// in the Polarion shell window, outside this app's frame, and has to be a classic script. It is served
// from this app's own context, so generic's webapp is no longer involved in the breadcrumb at all.
function copyBreadcrumbBridge() {
  return {
    name: 'copy-breadcrumb-bridge',
    writeBundle(options) {
      const require = createRequire(import.meta.url);
      copyFileSync(
        require.resolve('@grigoriev/react-sbb-polarion/breadcrumb-bridge.js'),
        `${options.dir}/breadcrumb-bridge.js`,
      );
    },
  };
}

export default defineConfig(({ command, mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const polarionUrl = env.VITE_BASE_URL || 'http://localhost';

  // The shared @grigoriev/react-sbb-polarion package is linked via a `file:`
  // dependency, which npm symlinks into node_modules together with its own dev copies of React and
  // sonner. Dedupe so the app and the linked library resolve to this app's single instance of each:
  // React (avoids the dual-React "invalid hook call") and sonner (the RSP `Toaster` host and this
  // app's `toast()` calls must share one sonner instance, or fired toasts never reach the host).
  // Harmless once the package is consumed from a registry.
  const resolve = { dedupe: ['react', 'react-dom', 'sonner'] };

  if (command === 'serve') {
    return {
      plugins: [react()],
      resolve,
      server: {
        proxy: {
          // Generic assets still served by GenericUiServlet: only the markdown stylesheet index.html
          // links. Served unauthenticated in Polarion, so the dev proxy can fetch it without a session.
          '/polarion/xml-repair-app/ui/generic': {
            target: polarionUrl,
            changeOrigin: true,
          },
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
    plugins: [react(), copyBreadcrumbBridge()],
    resolve,
    // Never let a developer's personal access token reach a shipped bundle. VITE_BEARER_TOKEN is a
    // `vite dev` convenience (it switches useRemote to the token-authenticated /api endpoints); Vite
    // inlines import.meta.env.VITE_* at build time, so a local .env.local would otherwise be baked
    // into the bundle that `mvn -P install-to-local-polarion` deploys, readable by everyone the SPA is
    // served to. Forcing it undefined here keeps production on the session-authenticated /internal
    // endpoints, which is what Polarion provides anyway.
    define: { 'import.meta.env.VITE_BEARER_TOKEN': 'undefined' },
    base: '/polarion/xml-repair-app/ui/app/',
    build: {
      outDir: './dist/app',
      emptyOutDir: true,
    },
  };
});
