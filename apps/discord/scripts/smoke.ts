import assert from "node:assert/strict";
import { writeFile } from "node:fs/promises";
import { storefrontUrl } from "@storefront/shared";
import { ScreenshotRenderer } from "../src/render-page.ts";

const base = process.env.STOREFRONT_URL || "http://127.0.0.1:8080/";
const username = process.env.SMOKE_USERNAME || "StorefrontBot";
const renderer = new ScreenshotRenderer();
const url = (name: string) =>
  storefrontUrl(base, { username: name, simpleUI: true, timestamp: true }).toString();
try {
  const results = await Promise.allSettled([
    renderer.render(url(username)),
    renderer.render(url("__no_shops__")),
    renderer.render(url(username)),
  ]);
  const [capture, missing, busy] = results;
  if (capture.status === "rejected") throw capture.reason;
  assert.ok(capture.value, `No shops for ${username}; run the Compose bot fixture first.`);
  assert.equal(Buffer.from(capture.value).subarray(0, 8).toString("hex"), "89504e470d0a1a0a");
  assert.deepEqual(missing, { status: "fulfilled", value: null });
  assert.equal(busy.status, "rejected");
  if (busy.status === "rejected") assert.match(String(busy.reason), /busy/);
  assert.ok(await renderer.render(url(username)), "Browser must remain usable after a capture.");
  if (process.env.SCREENSHOT_PATH) await writeFile(process.env.SCREENSHOT_PATH, capture.value);
  console.log(
    `Captured ${username}: ${capture.value.byteLength} bytes. Empty results, concurrency limit, and browser reuse passed.`,
  );
} finally {
  await renderer.close();
}
await assert.rejects(renderer.render(url(username)), /shutting down/);
