// @ts-check
const { defineConfig, devices } = require('@playwright/test');

/**
 * Suite E2E del login de TrailerSys.
 *
 * Solo levanta el frontend estatico (dev/server.js, el mismo que documenta
 * el README de la raiz) en el puerto 5173. El backend Spring Boot (:8080)
 * y PostgreSQL se asumen corriendo aparte -- esta suite no los arranca ni
 * los detiene.
 */
module.exports = defineConfig({
  testDir: './tests',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: [['html', { open: 'never' }], ['list']],

  use: {
    baseURL: 'http://localhost:5173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },

  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],

  // Arranca "node ../dev/server.js .. 5173" (equivalente al "node dev/server.js . 5173"
  // que documenta el README, ejecutado desde e2e/) antes de correr los tests, y lo
  // reutiliza si ya esta corriendo en vez de fallar por puerto ocupado.
  webServer: {
    command: 'node ../dev/server.js .. 5173',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
    timeout: 30_000,
  },
});
