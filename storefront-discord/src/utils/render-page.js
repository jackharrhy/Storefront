const puppeteer = require("puppeteer");

module.exports = async (url) => {
  const browser = await puppeteer.launch({
    defaultViewport: { width: 612, height: 100 },
    args: ["--no-sandbox", "--disable-setuid-sandbox"],
  });
  try {
    const page = await browser.newPage();
    await page.goto(url, { waitUntil: "networkidle0" });
    await page.waitForSelector("#app-root");
    await page.waitForSelector('[data-loading="true"]', { hidden: true });
    if (await page.$('[role="alert"]'))
      throw new Error("Storefront API request failed");
    if (!(await page.$(".user"))) return null;
    const root = await page.$("#app-root");
    return await root.screenshot();
  } finally {
    await browser.close();
  }
};
