import assert from "node:assert/strict";
import { test } from "node:test";
import { commandReply, parseCommand } from "./commands.ts";
import { readConfig } from "./config.ts";

test("prefix commands ignore unrelated messages and reject invalid usernames before rendering", async () => {
  assert.equal(parseCommand("hello", "sf!"), null);
  assert.equal(parseCommand("sf!constructor", "sf!"), null);
  assert.deepEqual(parseCommand("sf!show Alice_1", "sf!"), { name: "show", username: "Alice_1" });
  for (const username of ["", "Alice Bob", "@everyone", "a".repeat(17)]) {
    const result = await commandReply(
      { name: "show", username },
      "https://example.com/",
      "sf!",
      async () => {
        throw new Error("Must not render");
      },
    );
    assert.equal(result.content, "Usage: sf!show <Minecraft username>");
  }
});

test("show builds a capture URL and returns the screenshot without mentions", async () => {
  const result = await commandReply(
    { name: "show", username: "Alice" },
    "https://example.com/?old=true",
    "sf!",
    async (address) => {
      const url = new URL(address);
      assert.equal(url.searchParams.get("username"), "Alice");
      assert.ok(url.searchParams.has("simpleUI"));
      assert.ok(url.searchParams.has("timestamp"));
      assert.equal(url.searchParams.has("old"), false);
      return new Uint8Array([1, 2, 3]);
    },
  );
  assert.equal(result.files?.length, 1);
  assert.deepEqual(result.allowedMentions, { parse: [], repliedUser: false });
  const empty = await commandReply(
    { name: "show", username: "Nobody" },
    "https://example.com/",
    "sf!",
    async () => null,
  );
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
