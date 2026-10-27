import { defineConfig } from '@playwright/test'
import base from './playwright.performance.config'

export default defineConfig({ ...base, testMatch: ['query-toolbars.spec.ts', 'market-filters.spec.ts'] })
