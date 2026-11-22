// Prints an HTML file to an A4 PDF with Playwright's Chromium (installed for the frontend's e2e tests).
// Usage (from frontend/): node ../tools/ml/html_to_pdf.mjs <in.html> <out.pdf>
import { createRequire } from 'node:module'
import { pathToFileURL } from 'node:url'

const require = createRequire(pathToFileURL(process.cwd() + '/package.json'))
const { chromium } = require('@playwright/test')
const [input, output] = process.argv.slice(2)

const browser = await chromium.launch()
const page = await browser.newPage()
await page.goto(pathToFileURL(input).href, { waitUntil: 'load' })
await page.pdf({ path: output, format: 'A4', printBackground: true, margin: { top: '12mm', bottom: '12mm', left: '12mm', right: '12mm' } })
await browser.close()
