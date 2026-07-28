import { defineConfig } from '@playwright/test';

const pmsE2ePassword = process.env.PMS_E2E_PASSWORD || 'chrono-pms-e2e-only';

export default defineConfig({
    testDir: 'e2e',
    fullyParallel: false,
    workers: 1,
    reporter: [['list'], ['html', { outputFolder: 'output/playwright/report', open: 'never' }]],
    use: {
        headless: true,
        baseURL: process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:5174',
        screenshot: 'only-on-failure',
        trace: 'retain-on-failure',
    },
    webServer: process.env.PLAYWRIGHT_SKIP_WEBSERVER === 'true' ? undefined : [
        {
            command: '.\\mvnw.cmd spring-boot:run',
            cwd: '../Chrono-backend',
            port: 18084,
            reuseExistingServer: false,
            timeout: 120_000,
            env: {
                SPRING_PROFILES_ACTIVE: 'local',
                SPRING_DATASOURCE_URL: 'jdbc:h2:mem:chrono_pms_e2e;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1',
                SPRING_DATASOURCE_USERNAME: 'sa',
                SPRING_DATASOURCE_PASSWORD: '',
                SPRING_DATASOURCE_DRIVER_CLASS_NAME: 'org.h2.Driver',
                SPRING_JPA_HIBERNATE_DDL_AUTO: 'none',
                SPRING_FLYWAY_ENABLED: 'true',
                APP_INITIALIZE_ADMIN: 'false',
                APP_PMS_TEST_ACCOUNT_ENABLED: 'true',
                APP_PMS_TEST_ACCOUNT_USERNAME: 'Christopher',
                APP_PMS_TEST_ACCOUNT_PASSWORD: pmsE2ePassword,
                APP_PMS_DEMO_DATA_ENABLED: 'true',
                APP_PMS_OUTBOX_ENABLED: 'false',
                APP_PMS_ALERTS_ENABLED: 'false',
                LLM_WARMUP_ENABLED: 'false',
                SERVER_PORT: '18084',
                SPRING_MAIL_HOST: 'localhost',
                SPRING_MAIL_PORT: '2525',
                SPRING_MAIL_PASSWORD: 'local-test-only',
            },
        },
        {
            command: 'npm run dev -- --host 127.0.0.1 --port 5174',
            cwd: '.',
            port: 5174,
            reuseExistingServer: false,
            timeout: 60_000,
            env: {
                VITE_API_BASE_URL: 'http://127.0.0.1:18084',
            },
        },
    ],
});
