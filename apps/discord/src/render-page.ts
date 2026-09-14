import puppeteer, { type Browser } from "puppeteer";

export class ScreenshotRenderer {
  private browser: Promise<Browser> | undefined;
  private active = 0;
  private closed = false;

  async render(url: string): Promise<Uint8Array | null> {
    if (this.closed) throw new Error("Screenshot service is shutting down.");
    if (this.active >= 2) throw new Error("Screenshots are busy. Try again in a moment.");
    this.active++;
    try {
      this.browser ??= puppeteer
        .launch({
          defaultViewport: { width: 612, height: 100 },
          protocolTimeout: 15_000,
          args: ["--no-sandbox", "--disable-setuid-sandbox"],
        })
        .catch((error: unknown) => {
          this.browser = undefined;
          throw error;
        });
      const browser = await this.browser;
      if (!browser.connected) {
        this.browser = undefined;
        throw new Error("Screenshot browser disconnected. Please try again.");
      }
      const context = await browser.createBrowserContext();
      try {
        const page = await context.newPage();
        page.setDefaultTimeout(15_000);
        await page.goto(url, { waitUntil: "domcontentloaded" });
        await page.waitForSelector('#app-root:not([data-loading="true"])');
        if (await page.$('[role="alert"]')) throw new Error("Storefront API request failed.");
        if (!(await page.$(".user"))) return null;
        await page.evaluate(async () => {
          document.querySelectorAll<HTMLElement>(".storefront").forEach((shop) => {
            shop.style.contentVisibility = "visible";
          });
          document.querySelectorAll("img").forEach((img) => {
            img.loading = "eager";
          });
          await document.fonts.ready;
        });
        await page.waitForFunction(
          () => [...document.images].every((img) => img.complete && img.naturalWidth > 0),
          { timeout: 45_000 },
        );
        const root = await page.$("#app-root");
        const bounds = await root?.boundingBox();
        if (!root || !bounds) throw new Error("Storefront could not be captured.");
        if (bounds.height > 30_000)
          throw new Error("Too many shops for one screenshot. Open the storefront link instead.");
        const screenshot = await root.screenshot({ type: "png" });
        if (screenshot.byteLength > 8_000_000)
          throw new Error("Screenshot is too large. Open the storefront link instead.");
        return screenshot;
      } finally {
        await context.close();
      }
    } finally {
      this.active--;
    }
  }

  async close() {
    this.closed = true;
    const browser = await this.browser?.catch(() => undefined);
    this.browser = undefined;
    await browser?.close();
  }
}
