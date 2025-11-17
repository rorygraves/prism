import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  fullyParallel: false, // Run sequentially to avoid conflicts
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1, // Single worker to avoid race conditions
  reporter: 'html',
  use: {
    baseURL: 'http://localhost:3000',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    // Explicitly run headless for cloud/CI environments
    headless: true,
    // Disable video to save resources in headless mode
    video: 'retain-on-failure',
  },

  projects: [
    {
      name: 'firefox',
      use: {
        ...devices['Desktop Firefox'],
        // Firefox is more stable in containerized/cloud environments
      },
    },
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        // Cloud-friendly Chromium launch args for containerized environments
        launchOptions: {
          args: [
            '--disable-gpu',
            '--disable-dev-shm-usage',
            '--disable-setuid-sandbox',
            '--no-sandbox',
            '--disable-accelerated-2d-canvas',
            '--disable-software-rasterizer',
          ],
        },
      },
    },
  ],

  /* Run your local dev server before starting the tests */
  webServer: [
    {
      command: 'cd ../backend && poetry run python -m chat_demo.main',
      url: 'http://localhost:8000/health',
      reuseExistingServer: !process.env.CI,
      timeout: 120000,
    },
    {
      command: 'cd ../frontend/chat-demo && pnpm run dev',
      url: 'http://localhost:3000',
      reuseExistingServer: !process.env.CI,
      timeout: 120000,
    },
  ],
});
