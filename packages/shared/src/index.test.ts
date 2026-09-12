import assert from "node:assert/strict";
import { test } from "node:test";
import { readStorefrontOptions, storefrontUrl } from "./index.ts";

test("capture options round trip through the same URL contract the frontend reads", () => {
  const options = { username: "Alice_1", simpleUI: true, timestamp: true };
  const url = storefrontUrl("https://example.com/?username=Bob#old", options);
  assert.deepEqual(readStorefrontOptions(url.searchParams), options);
  assert.equal(url.hash, "");
  assert.deepEqual(readStorefrontOptions(new URLSearchParams()), {
    username: null,
    simpleUI: false,
    timestamp: false,
  });
});
