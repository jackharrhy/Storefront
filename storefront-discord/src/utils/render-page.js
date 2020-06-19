const puppeteer = require('puppeteer');

module.exports = async (uri) => {
	const browser = await puppeteer.launch({
		defaultViewport: {
			width: 612,
			height: 100,
		},
		args: ['--no-sandbox', '--disable-setuid-sandbox'],
	});
	const page = await browser.newPage();

	await page.goto(uri, { waitUntil: 'networkidle0' });

	const selector = '#app-root';
	const { childCount, width, height } = await page.evaluate((selector) => {
		const element = document.querySelector(selector);
		return {
			childCount: element.childElementCount,
			width: element.scrollWidth,
			height: element.scrollHeight,
		};
	}, selector);

	if (childCount === 0) {
		return null;
	}

	await page.setViewport({ width, height });

	const screenshot = await page.screenshot();

	await browser.close();
	return screenshot;
};

