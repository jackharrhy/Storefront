import assert from "node:assert/strict";
import { readFile, writeFile } from "node:fs/promises";
import puppeteer from "puppeteer";

const base = new URL(process.env.STOREFRONT_URL || "http://127.0.0.1:8080/storefront/");
const catalog = JSON.parse(
  await readFile(new URL("../reports/catalog.json", import.meta.url), "utf8"),
);
const bundle = catalog.entries.find((entry) => entry.label === "Bundle with contents");
assert.equal(bundle.metadata.items[0].id, "minecraft:diamond");
assert.equal(bundle.metadata.items[0].count, 3);
const crossbow = catalog.entries.find((entry) => entry.label === "Loaded crossbow");
assert.equal(crossbow.metadata["charged-projectiles"][0].id, "minecraft:arrow");
const keys = [...new Set(catalog.entries.map((entry) => entry.key))].sort();
const missing = [];
let cursor = 0;
await Promise.all(
  Array.from({ length: 8 }, async () => {
    while (cursor < keys.length) {
      const key = keys[cursor++];
      const response = await fetch(new URL(`images/${key.replace("minecraft:", "")}.png`, base), {
        signal: AbortSignal.timeout(10_000),
      });
      const bytes = Buffer.from(await response.arrayBuffer());
      if (!response.ok || bytes.subarray(0, 8).toString("hex") !== "89504e470d0a1a0a")
        missing.push({ key, status: response.status });
    }
  }),
);
missing.sort((a, b) => a.key.localeCompare(b.key));
const browser = await puppeteer.launch({
  executablePath: process.env.BROWSER_EXECUTABLE_PATH || undefined,
  args: ["--no-sandbox"],
});
const browserVersion = await browser.version();
const scenarios = [];
try {
  for (const [name, width, height, filtered] of [
    ["desktop", 1280, 800, true],
    ["mobile", 390, 844, true],
    ["mobile-breakpoint", 600, 900, true],
    ["nested-scroll", 1280, 800, false],
  ]) {
    const page = await browser.newPage();
    await page.setViewport({ width, height });
    await page.setCacheEnabled(false);
    await page.evaluateOnNewDocument(() => {
      window.catalogCLS = 0;
      window.catalogShifts = [];
      new PerformanceObserver((list) => {
        for (const entry of list.getEntries())
          if (!entry.hadRecentInput) {
            window.catalogCLS += entry.value;
            window.catalogShifts.push({
              value: entry.value,
              sources: entry.sources.map((source) => ({
                node: source.node?.className || source.node?.nodeName,
                before: source.previousRect.toJSON(),
                after: source.currentRect.toJSON(),
              })),
            });
          }
      }).observe({ type: "layout-shift", buffered: true });
    });
    const runtimeErrors = [];
    const requested = new Set();
    page.on("pageerror", (error) => runtimeErrors.push(error.message));
    page.on("request", (request) => {
      if (request.url().includes("/images/")) requested.add(request.url());
    });
    const url = new URL(base);
    if (filtered) url.searchParams.set("username", catalog.username);
    await page.goto(url.href, { waitUntil: "networkidle0" });
    const selector = `section[aria-label="${catalog.username}'s shops"] .storefront`;
    await page.waitForSelector(selector);
    const shopElements = await page.$$(selector);
    const shopCount = shopElements.length;
    assert.equal(shopCount, catalog.chestCount);
    const initialRequests = requested.size;
    console.log(`${name}: ${shopCount} chests, ${initialRequests} initial icon requests`);
    assert(initialRequests < keys.length / 2, `${name}: images were fetched eagerly`);
    let largestImageShift = 0;
    let renderedSamples = 0;
    const rendered = new Set();
    const fallbacks = new Set();
    for (let index = 0; index < shopCount; index++) {
      const shop = shopElements[index];
      await shop.evaluate((element) =>
        element.scrollIntoView({ block: "center", inline: "nearest" }),
      );
      const before = await shop.evaluate(
        (element) => element.querySelector(".items").getBoundingClientRect().height,
      );
      await page.waitForFunction(
        (selector, index) => {
          const shop = document.querySelectorAll(selector)[index];
          return [...shop.querySelectorAll("img")].every(
            (img) => img.complete && img.naturalWidth > 0,
          );
        },
        { timeout: 15_000 },
        selector,
        index,
      );
      const result = await shop.evaluate((element) => ({
        height: element.querySelector(".items").getBoundingClientRect().height,
        keys: [...element.querySelectorAll("[data-item-key]")].map(
          (button) => button.dataset.itemKey,
        ),
        missing: [...element.querySelectorAll("[data-missing-icon]")].map(
          (img) => img.closest("[data-item-key]").dataset.itemKey,
        ),
      }));
      largestImageShift = Math.max(largestImageShift, Math.abs(result.height - before));
      renderedSamples += result.keys.length;
      result.keys.forEach((key) => rendered.add(key));
      result.missing.forEach((key) => fallbacks.add(key));
    }
    const cls = await page.evaluate(() => window.catalogCLS);
    if (cls > 0) console.log(name, JSON.stringify(await page.evaluate(() => window.catalogShifts)));
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > innerWidth);
    scenarios.push({
      name,
      width,
      height,
      shops: shopCount,
      initialIconRequests: initialRequests,
      totalIconRequests: requested.size,
      renderedItemTypes: rendered.size,
      renderedSamples,
      fallbackKeys: [...fallbacks].sort(),
      cls,
      layoutShifts: await page.evaluate(() => window.catalogShifts),
      largestImageShift,
      horizontalOverflow: overflow,
      runtimeErrors,
    });
    assert.equal(rendered.size, keys.length);
    assert.equal(renderedSamples, catalog.entries.length);
    assert.deepEqual([...fallbacks].sort(), missing.map((item) => item.key).sort());
    assert.deepEqual(runtimeErrors, []);
    assert(!overflow, `${name}: horizontal page overflow`);
    assert(largestImageShift <= 0.5, `${name}: image loading resized inventory slots`);
    assert(cls <= 0.1, `${name}: cumulative layout shift ${cls} exceeds 0.1`);
    if (filtered) {
      await page.evaluate(async () => {
        scrollTo(0, 0);
        await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)));
      });
      await page.screenshot({
        path: new URL(`../reports/catalog-${name}.png`, import.meta.url).pathname,
      });
    }
    await page.close();
  }
} finally {
  await browser.close();
}
const variantCounts = {};
for (const entry of catalog.entries)
  variantCounts[entry.kind] = (variantCounts[entry.kind] || 0) + 1;
const report = {
  minecraft: catalog.minecraft,
  browser: browserVersion,
  timestamp: new Date().toISOString(),
  itemTypes: keys.length,
  entries: catalog.entries.length,
  excluded: catalog.excluded,
  variantCounts,
  availableIcons: keys.length - missing.length,
  missingIcons: missing,
  variantRendering:
    "Icons are keyed by base item type. Potion colors, trims, dye colors, banner patterns, enchantment glint, bundle contents and other metadata do not select distinct sprites. Durability bars and stack counts are rendered separately.",
  scenarios,
};
await writeFile(
  new URL("../reports/catalog-coverage.json", import.meta.url),
  JSON.stringify(report, null, 2) + "\n",
);
console.log(JSON.stringify(report, null, 2));
assert.equal(
  missing.length,
  0,
  "Missing or invalid base icons; see dev/reports/catalog-coverage.json",
);
