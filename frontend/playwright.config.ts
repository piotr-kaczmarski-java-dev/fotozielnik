import { defineConfig, devices } from '@playwright/test'

// `npm run e2e` starts what is not already running: Spring (bootRun also starts compose.yml's Postgres)
// and Vite. With both already up (tools/dev.sh), it reuses them.
export default defineConfig({
  testDir: './e2e',
  outputDir: './test-results',
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      command: './gradlew bootRun --quiet',
      cwd: '../backend',
      url: 'http://localhost:8080/actuator/health',
      reuseExistingServer: true,
      timeout: 180_000,
    },
    {
      command: 'npm run dev',
      url: 'http://localhost:5173',
      reuseExistingServer: true,
      timeout: 60_000,
    },
  ],
})
