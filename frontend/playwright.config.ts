import { defineConfig, devices } from '@playwright/test'

// e2e runs against its OWN stack, never the dev one: Spring on :8081 via `bootTestRun` (fresh Postgres + Garage
// from Testcontainers, a fixed e2e admin) and Vite on :5174 proxying to it. So tests never depend on (or
// change) your dev data or dev admin password. A stack left running from an earlier run is reused.
export const E2E_BACKEND = 'http://localhost:8081'
export const E2E_ADMIN = { email: 'e2e-admin@fotozielnik.test', password: 'e2e-admin-password' }

export default defineConfig({
  testDir: './e2e',
  outputDir: './test-results',
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  timeout: 60_000,
  use: {
    baseURL: 'http://localhost:5174',
    locale: 'pl-PL',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      command:
        "./gradlew --quiet bootTestRun --args='--server.port=8081 --spring.docker.compose.enabled=false'",
      cwd: '../backend',
      url: `${E2E_BACKEND}/actuator/health`,
      env: { ADMIN_EMAIL: E2E_ADMIN.email, ADMIN_INITIAL_PASSWORD: E2E_ADMIN.password },
      reuseExistingServer: true,
      timeout: 240_000,
    },
    {
      command: 'npm run dev -- --port 5174',
      url: 'http://localhost:5174',
      env: { BACKEND_URL: E2E_BACKEND },
      reuseExistingServer: true,
      timeout: 60_000,
    },
  ],
})
