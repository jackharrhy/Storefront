import assert from "node:assert/strict";
import { once } from "node:events";
import { mkdir, writeFile } from "node:fs/promises";
import { setTimeout as delay } from "node:timers/promises";
import mineflayer from "mineflayer";
import { Rcon } from "rcon-client";
import { Vec3 } from "vec3";

const count = Number(process.env.STOREFRONTS || 100);
assert(Number.isInteger(count) && count > 0 && count <= 1000, "STOREFRONTS must be 1–1000");
const dirty = process.env.DIRTY === "true";
const rounds = dirty ? 3 : 10;
const host = process.env.MC_HOST || "paper";
const api = process.env.API_URL || "http://paper:7000";
const username = "StorefrontLoad";
const label = process.env.RUN_LABEL || "stress";
assert(/^[\w-]+$/.test(label));
const samples = [];
const errors = [];
let stopTraffic = false;
let rcon;
let traffic = [];
const bot = mineflayer.createBot({
  host,
  username,
  auth: "offline",
  version: process.env.MC_VERSION || "26.1",
});
const timeout = setTimeout(() => {
  console.error("Stress test timed out");
  process.exit(1);
}, 15 * 60_000);
bot.on("error", console.error);
bot.on("kicked", console.error);
const strip = (text) => text.replace(/\u001b\[[0-9;]*m|§./g, "").trim();
const percentile = (values, fraction) =>
  [...values].sort((a, b) => a - b)[Math.ceil(values.length * fraction) - 1] ?? null;
async function shops() {
  const response = await fetch(`${api}/storefronts/`, { signal: AbortSignal.timeout(10_000) });
  assert.equal(response.status, 200);
  return (await response.json()).filter((shop) => shop.owner.name === username);
}
async function waitForRefresh() {
  const start = performance.now();
  let status;
  do {
    await delay(50);
    status = JSON.parse(strip(await rcon.send("storefrontrefreshstatus")));
    assert(!status.error, status.error);
    assert(performance.now() - start < 60_000, "Refresh did not complete");
  } while (status.running);
  return status;
}
async function reader() {
  while (!stopTraffic) {
    const start = performance.now();
    try {
      await shops();
      samples.push(performance.now() - start);
    } catch (error) {
      errors.push(error.message);
    }
    await delay(100);
  }
}
try {
  await once(bot, "spawn");
  rcon = await Rcon.connect({
    host,
    port: 25575,
    password: process.env.RCON_PASSWORD || "storefront-local-only",
    timeout: 30_000,
  });
  if (process.env.SETUP !== "false") {
    for (let i = 0; i < count; i++) {
      const x = 1024 + (i % 25) * 4;
      const z = 1024 + Math.floor(i / 25) * 4;
      await rcon.send(`tp ${username} ${x + 0.5} 65 ${z + 3.5}`);
      await rcon.send(`fill ${x - 1} 64 ${z - 1} ${x + 1} 64 ${z + 3} stone`);
      const items = Array.from(
        { length: 27 },
        (_, slot) => `{Slot:${slot}b,id:"minecraft:diamond",count:64}`,
      ).join(",");
      await rcon.send(`setblock ${x} 65 ${z} chest[facing=south]{Items:[${items}]}`);
      await rcon.send(
        `setblock ${x} 65 ${z + 1} oak_wall_sign[facing=south]{front_text:{messages:["[storefront]","Stress ${i}","Full chest",""]},is_waxed:1b}`,
      );
      await bot.waitForChunksToLoad();
      await bot.waitForTicks(6);
      const sign = bot.blockAt(new Vec3(x, 65, z + 1));
      assert.equal(sign?.name, "oak_wall_sign");
      let registered = false;
      for (let attempt = 0; attempt < 10; attempt++) {
        await bot.activateBlock(sign);
        await bot.waitForTicks(3);
        if ((await shops()).some((shop) => shop.description[1] === `Stress ${i}`)) {
          registered = true;
          break;
        }
      }
      assert(registered, `Sign interaction did not register shop ${i}`);
      if ((i + 1) % 25 === 0) console.log(`Registered ${i + 1}/${count} shops`);
    }
  }
  await delay(2000);
  const initial = await shops();
  assert.equal(initial.length, count);
  assert(
    initial.every(
      (shop) => shop.contents.length === 27 && shop.contents.every((item) => item?.amount === 64),
    ),
  );
  // Keep fixture chunks resident so before/after runs measure inventory refresh, not world generation.
  await rcon.send("forceload add 1023 1023 1125 1185");
  await delay(6000);
  const before = strip(await rcon.send("mspt"));
  traffic = Array.from({ length: 4 }, () => reader());
  const refreshMs = [];
  const replies = [];
  const refreshes = [];
  for (let i = 0; i < rounds; i++) {
    if (dirty) {
      for (let j = 0; j < count; j++) {
        await rcon.send(
          `item replace block ${1024 + (j % 25) * 4} 65 ${1024 + Math.floor(j / 25) * 4} container.0 with diamond ${i % 2 === 0 ? 63 : 64}`,
        );
      }
    }
    const start = performance.now();
    replies.push(strip(await rcon.send("storefrontforceupdate")));
    refreshMs.push(performance.now() - start);
    const status = await waitForRefresh();
    refreshes.push(status);
    assert(status.targets >= count);
    assert(status.skippedUnloaded <= status.targets - count, "Fixture chunks were not loaded");
    if (dirty) {
      assert(status.changed >= count, "Dirty inventories were not persisted");
      assert((await shops()).every((shop) => shop.contents[0]?.amount === (i % 2 === 0 ? 63 : 64)));
    }
    await delay(1000);
  }
  const after = strip(await rcon.send("mspt"));
  stopTraffic = true;
  await Promise.all(traffic);
  assert.equal((await shops()).length, count);
  const report = {
    label,
    count,
    dirty,
    rounds,
    itemsPerChest: 27,
    readers: 4,
    timestamp: new Date().toISOString(),
    before,
    after,
    refreshCommandMs: {
      p50: percentile(refreshMs, 0.5),
      p95: percentile(refreshMs, 0.95),
      max: Math.max(...refreshMs),
      samples: refreshMs,
    },
    apiMs: {
      requests: samples.length,
      p50: percentile(samples, 0.5),
      p95: percentile(samples, 0.95),
      max: Math.max(...samples),
    },
    errors,
    replies,
    refreshes,
  };
  await mkdir("/reports", { recursive: true });
  await writeFile(`/reports/${label}-${count}.json`, JSON.stringify(report, null, 2) + "\n");
  console.log(JSON.stringify(report, null, 2));
  assert.equal(errors.length, 0, "HTTP errors during stress test");
} catch (error) {
  console.error(error);
  process.exitCode = 1;
} finally {
  clearTimeout(timeout);
  stopTraffic = true;
  await Promise.allSettled(traffic);
  if (rcon && dirty) {
    try {
      for (let j = 0; j < count; j++) {
        await rcon.send(
          `item replace block ${1024 + (j % 25) * 4} 65 ${1024 + Math.floor(j / 25) * 4} container.0 with diamond 64`,
        );
      }
      await waitForRefresh();
      await rcon.send("storefrontforceupdate");
      await waitForRefresh();
      assert((await shops()).every((shop) => shop.contents[0]?.amount === 64));
    } catch (error) {
      console.error("Fixture restoration failed:", error);
      process.exitCode = 1;
    }
  }
  if (rcon) {
    await rcon.send("forceload remove 1023 1023 1125 1185").catch(console.error);
    await rcon.end();
  }
  bot.quit();
}
