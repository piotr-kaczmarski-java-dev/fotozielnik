// Opens one page in headless Chromium and reports what an agent cannot otherwise see:
// a screenshot, console errors, page errors and failed network requests. Used by tools/look.sh.
//
// node e2e/look.mjs --url <url> --out <file.png> [--width 1280] [--height 800] [--wait <css selector>]
//                   [--eval-file <file.js>] [--full]
// --eval-file: the file's content is the body of an async function run in the page after load;
//              its JSON-serialisable return value is printed (e.g. `return document.title`).
import { chromium } from '@playwright/test'
import { readFileSync } from 'node:fs'

const args = Object.fromEntries(
  process.argv.slice(2).reduce((acc, a, i, all) => {
    if (a.startsWith('--')) acc.push([a.slice(2), all[i + 1]?.startsWith('--') || all[i + 1] === undefined ? true : all[i + 1]])
    return acc
  }, []),
)
const width = Number(args.width ?? 1280)
const height = Number(args.height ?? 800)

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width, height } })
const problems = []
page.on('console', (m) => m.type() === 'error' && problems.push(`console.error: ${m.text()}`))
page.on('pageerror', (e) => problems.push(`page error: ${e.message}`))
page.on('response', (r) => r.status() >= 400 && problems.push(`HTTP ${r.status()} ${r.request().method()} ${r.url()}`))
page.on('requestfailed', (r) => problems.push(`request failed: ${r.url()} (${r.failure()?.errorText})`))

let code = 0
try {
  const res = await page.goto(args.url, { waitUntil: 'networkidle', timeout: 30_000 })
  console.log(`page: ${args.url} -> HTTP ${res?.status()} "${await page.title()}"`)
  if (typeof args.wait === 'string') await page.waitForSelector(args.wait, { timeout: 10_000 })
  if (typeof args['eval-file'] === 'string') {
    const body = readFileSync(args['eval-file'], 'utf8')
    const result = await page.evaluate(`(async () => { ${body}\n})()`)
    console.log(`eval: ${JSON.stringify(result)}`)
  }
  await page.screenshot({ path: args.out, fullPage: Boolean(args.full) })
  console.log(`screenshot: ${args.out} (${width}x${height}${args.full ? ', full page' : ''})`)
} catch (e) {
  problems.push(`look failed: ${e instanceof Error ? e.message : e}`)
  code = 1
} finally {
  await browser.close()
}
for (const p of problems) console.log(`PROBLEM ${p}`)
process.exit(problems.length ? 1 : code)
