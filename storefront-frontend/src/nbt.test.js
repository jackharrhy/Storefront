// @vitest-environment node
import { gzipSync } from "node:zlib";
import { expect, it } from "vitest";
import { decodeNbt } from "./nbt.js";

it("decodes a legacy gzipped compound with a string tag", async () => {
  const compound = Buffer.from([
    10, 0, 0, 8, 0, 4, 110, 97, 109, 101, 0, 5, 104, 101, 108, 108, 111, 0,
  ]);
  const result = await decodeNbt(gzipSync(compound).toString("base64"));
  expect(result).toEqual({ name: "hello" });
  await expect(decodeNbt("not base64!")).rejects.toThrow();
});
