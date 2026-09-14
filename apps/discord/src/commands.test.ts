import assert from "node:assert/strict";
import { test } from "node:test";
import { commandReply, parseCommand } from "./commands.ts";
import { readStorefrontOptions } from "@storefront/shared";
import { readConfig } from "./config.ts";

const config = {
  storefrontUrl: "http://storefront-frontend/?old=true#old",
  publicUrl: "https://example.com/",
  commandPrefix: "sf!",
};

test("prefix commands ignore unrelated messages and reject invalid usernames before rendering", async () => {
  assert.equal(parseCommand("hello", "sf!"), null);
  assert.equal(parseCommand("sf!constructor", "sf!"), null);
  assert.deepEqual(parseCommand("sf!show Alice_1", "sf!"), { name: "show", username: "Alice_1" });
  for (const username of ["", "Alice Bob", "@everyone", "a".repeat(17)]) {
    const result = await commandReply({ name: "show", username }, config, async () => {
      throw new Error("Must not render");
    });
    assert.equal(result.content, "Usage: sf!show <Minecraft username>");
  }
});

test("show builds a capture URL and returns the screenshot without mentions", async () => {
  const result = await commandReply(
    { name: "show", username: "Alice" },
    config,
    async (address) => {
      const url = new URL(address);
      assert.equal(url.host, "storefront-frontend");
      assert.deepEqual(readStorefrontOptions(url.searchParams), {
        username: "Alice",
        simpleUI: true,
        timestamp: true,
      });
      assert.equal(url.searchParams.has("old"), false);
      assert.equal(url.hash, "");
      return new Uint8Array([1, 2, 3]);
    },
  );
  assert.equal(result.content, "<https://example.com/?username=Alice>");
  assert.deepEqual(result.files, [{ attachment: Buffer.from([1, 2, 3]), name: "storefront.png" }]);
  assert.deepEqual(result.allowedMentions, { parse: [], repliedUser: false });
  const empty = await commandReply({ name: "show", username: "Nobody" }, config, async () => null);
  assert.equal(empty.content, "No storefronts found for that player.");
});

test("configuration fails early for missing tokens and invalid URLs", () => {
  assert.throws(() => readConfig({}), /STOREFRONT_DISCORD_TOKEN/);
  assert.throws(
    () => readConfig({ STOREFRONT_DISCORD_TOKEN: "test", STOREFRONT_URL: "file:///tmp/" }),
    /HTTP/,
  );
  assert.equal(readConfig({ STOREFRONT_DISCORD_TOKEN: "test" }).commandPrefix, "sf!");
});
