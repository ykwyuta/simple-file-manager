// @ts-check
const { defineConfig, devices } = require('@playwright/test');
const path = require('node:path');

const PORT = Number(process.env.E2E_PORT || 8080);
const BASE_URL = process.env.E2E_BASE_URL || `http://localhost:${PORT}`;
const JAR = path.join(__dirname, '..', 'target', 'file-manager-0.0.1-SNAPSHOT.jar');

/**
 * The suite drives a real application instance.
 *
 * The `h2` profile pulls in `local-storage`, so the server runs on an in-memory
 * database with filesystem-backed object storage: no PostgreSQL and no S3
 * service to install before running the tests. Every run starts from an empty
 * schema, which is what lets the tests assert on absolute counts.
 */
module.exports = defineConfig({
  testDir: './tests',
  // Data-mutating specs share one server, so files run one at a time. Tests
  // within a file still run in declaration order.
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : [['list'], ['html', { open: 'never' }]],
  timeout: 30_000,
  expect: { timeout: 10_000 },

  use: {
    baseURL: BASE_URL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
  },

  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        // Honour a preinstalled browser when the sandbox provides one.
        launchOptions: process.env.CHROMIUM_PATH
          ? { executablePath: process.env.CHROMIUM_PATH }
          : {},
      },
    },
  ],

  // Reuses an already-running server locally; always starts a fresh one in CI.
  webServer: process.env.E2E_NO_SERVER
    ? undefined
    : {
        command: `java -jar "${JAR}" --spring.profiles.active=h2 --server.port=${PORT} --app.bootstrap.demo-user=false`,
        url: `${BASE_URL}/login`,
        reuseExistingServer: !process.env.CI,
        timeout: 120_000,
        stdout: 'pipe',
        stderr: 'pipe',
      },
});
