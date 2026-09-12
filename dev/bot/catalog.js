import assert from "node:assert/strict";
import { once } from "node:events";
import { writeFile } from "node:fs/promises";
import mineflayer from "mineflayer";
import { Rcon } from "rcon-client";
import { Vec3 } from "vec3";

const host = process.env.MC_HOST || "paper";
const api = process.env.API_URL || "http://paper:7000";
const username = "StorefrontItems";
const bot = mineflayer.createBot({
  host,
  username,
  auth: "offline",
  version: process.env.MC_VERSION || "26.1",
});
const timeout = setTimeout(() => {
  console.error("Catalog fixture timed out");
  process.exit(1);
}, 15 * 60_000);
bot.on("error", console.error);
bot.on("kicked", console.error);
let rcon;
const jsonCommand = async (command) =>
  JSON.parse((await rcon.send(command)).replace(/\u001b\[[0-9;]*m|§./g, "").trim());
async function shops() {
  const response = await fetch(`${api}/storefronts/`, { signal: AbortSignal.timeout(10_000) });
  assert.equal(response.status, 200);
  return (await response.json()).filter((shop) => shop.owner.name === username);
}
try {
  await Promise.race([
    once(bot, "spawn"),
    once(bot, "end").then(() => {
      throw new Error("Bot disconnected before spawning");
    }),
  ]);
  rcon = await Rcon.connect({
    host,
    port: 25575,
    password: process.env.RCON_PASSWORD || "storefront-local-only",
    timeout: 30_000,
  });
  const registry = await jsonCommand("storefrontcatalog");
  const materials = [];
  for (let page = 0; page < registry.pages; page++)
    materials.push(...(await jsonCommand(`storefrontcatalog items ${page}`)));
  assert.equal(materials.length, registry.items);
  const enabled = materials.filter((item) => item.enabled);
  const entries = enabled.map((item) => ({
    kind: "base",
    key: item.key,
    label: item.key,
    input: item.key,
    amount: item.stackSize,
  }));
  const variant = (kind, key, label, components) =>
    entries.push({
      kind,
      key: `minecraft:${key}`,
      label,
      input: `minecraft:${key}[${components},minecraft:custom_name=${JSON.stringify(label)}]`,
      amount: 1,
    });
  for (const item of enabled.filter((item) => item.durability > 1)) {
    for (const damage of new Set([Math.floor(item.durability / 2), item.durability - 1])) {
      variant(
        "durability",
        item.key.replace("minecraft:", ""),
        `${item.key}: damage ${damage}`,
        `minecraft:damage=${damage}`,
      );
    }
  }
  for (const potion of await jsonCommand("storefrontcatalog potions")) {
    for (const form of ["potion", "splash_potion", "lingering_potion", "tipped_arrow"]) {
      variant(
        "potion",
        form,
        `${form}: ${potion}`,
        `minecraft:potion_contents={potion:${JSON.stringify(potion)}}`,
      );
    }
  }
  for (const enchantment of await jsonCommand("storefrontcatalog enchantments")) {
    for (let level = 1; level <= enchantment.maxLevel; level++) {
      variant(
        "enchantment",
        "enchanted_book",
        `${enchantment.key} ${level}`,
        `minecraft:stored_enchantments={${JSON.stringify(enchantment.key)}:${level}}`,
      );
    }
  }
  const patterns = await jsonCommand("storefrontcatalog trim-patterns");
  const trimMaterials = await jsonCommand("storefrontcatalog trim-materials");
  for (const pattern of patterns)
    for (const material of trimMaterials) {
      variant(
        "trim",
        "netherite_chestplate",
        `${pattern} / ${material}`,
        `minecraft:trim={pattern:${JSON.stringify(pattern)},material:${JSON.stringify(material)}}`,
      );
    }
  const colors = {
    white: 0xf9fffe,
    orange: 0xf9801d,
    magenta: 0xc74ebd,
    light_blue: 0x3ab3da,
    yellow: 0xfed83d,
    lime: 0x80c71f,
    pink: 0xf38baa,
    gray: 0x474f52,
    light_gray: 0x9d9d97,
    cyan: 0x169c9c,
    purple: 0x8932b8,
    blue: 0x3c44aa,
    brown: 0x835432,
    green: 0x5e7c16,
    red: 0xb02e26,
    black: 0x1d1d21,
  };
  for (const [color, rgb] of Object.entries(colors))
    for (const piece of ["helmet", "chestplate", "leggings", "boots", "horse_armor"]) {
      variant(
        "dye",
        `leather_${piece}`,
        `${color} leather ${piece}`,
        `minecraft:dyed_color=${rgb}`,
      );
    }
  for (const instrument of await jsonCommand("storefrontcatalog instruments")) {
    variant(
      "instrument",
      "goat_horn",
      instrument,
      `minecraft:instrument=${JSON.stringify(instrument)}`,
    );
  }
  variant(
    "metadata",
    "diamond_sword",
    "Named sword with lore",
    'minecraft:lore=["Catalog fixture","Two lines of lore"],minecraft:enchantments={"minecraft:sharpness":5}',
  );
  variant(
    "metadata",
    "bundle",
    "Bundle with contents",
    'minecraft:bundle_contents=[{id:"minecraft:diamond",count:3}]',
  );
  variant(
    "metadata",
    "crossbow",
    "Loaded crossbow",
    'minecraft:charged_projectiles=[{id:"minecraft:arrow",count:1}]',
  );
  variant(
    "metadata",
    "firework_rocket",
    "Colored firework",
    'minecraft:fireworks={flight_duration:3,explosions:[{shape:"star",colors:[I;16711680,255],has_trail:true}]}',
  );
  variant(
    "metadata",
    "white_banner",
    "Patterned banner",
    'minecraft:banner_patterns=[{pattern:"minecraft:creeper",color:"green"}]',
  );
  const chestCount = Math.ceil(entries.length / 27);
  assert(chestCount <= 300, "Catalog exceeds the reserved fixture area");
  console.log(
    `Paper ${registry.minecraft}: ${enabled.length} item types, ${entries.length - enabled.length} variants, ${chestCount} chests`,
  );
  const index = [];
  for (let page = 0; page < chestCount; page++) {
    const x = 2048 + (page % 20) * 4;
    const z = 2048 + Math.floor(page / 20) * 4;
    await rcon.send(`tp ${username} ${x + 0.5} 65 ${z + 3.5}`);
    await bot.waitForTicks(3);
    await bot.waitForChunksToLoad();
    await rcon.send(`fill ${x - 1} 64 ${z - 1} ${x + 1} 64 ${z + 3} stone`);
    await rcon.send(`setblock ${x} 65 ${z} air`);
    await rcon.send(`setblock ${x} 65 ${z} chest[facing=south]`);
    const batch = entries.slice(page * 27, (page + 1) * 27);
    for (const [slot, entry] of batch.entries()) {
      const reply = await rcon.send(
        `item replace block ${x} 65 ${z} container.${slot} with ${entry.input} ${entry.amount}`,
      );
      assert(reply.includes("Replaced"), `${entry.label}: ${reply}`);
    }
    await rcon.send(
      `setblock ${x} 65 ${z + 1} oak_wall_sign[facing=south]{front_text:{messages:["[storefront]","Catalog ${page + 1}","${batch[0].kind}","${batch.length} samples"]},is_waxed:1b}`,
    );
    await bot.waitForChunksToLoad();
    await rcon.send(`tp ${username} ${x + 0.5} 65 ${z + 3.5}`);
    await bot.waitForTicks(6);
    let shop;
    for (let attempt = 0; attempt < 10; attempt++) {
      await bot.activateBlock(bot.blockAt(new Vec3(x, 65, z + 1)));
      await bot.waitForTicks(3);
      shop = (await shops()).find((shop) => shop.description[1] === `Catalog ${page + 1}`);
      if (
        shop &&
        batch.every(
          (entry, slot) =>
            shop.contents[slot]?.key === entry.key &&
            shop.contents[slot]?.amount === entry.amount &&
            (entry.kind === "base" || shop.contents[slot]?.name === entry.label),
        )
      )
        break;
      shop = undefined;
    }
    assert(shop, `Catalog ${page + 1} did not match the server inventory`);
    batch.forEach((entry, slot) =>
      index.push({ ...entry, storefrontId: shop.id, slot, metadata: shop.contents[slot].meta }),
    );
    if ((page + 1) % 10 === 0 || page + 1 === chestCount)
      console.log(`Verified ${page + 1}/${chestCount} catalog chests`);
  }
  const actual = await shops();
  assert.equal(
    actual.length,
    chestCount,
    "Extra catalog shops exist; remove old catalog fixtures before using a smaller registry",
  );
  assert.equal(
    new Set(index.filter((item) => item.kind === "base").map((item) => item.key)).size,
    enabled.length,
  );
  await writeFile(
    "/reports/catalog.json",
    JSON.stringify(
      {
        minecraft: registry.minecraft,
        timestamp: new Date().toISOString(),
        username,
        registeredItems: materials.length,
        excluded: materials.filter((item) => !item.enabled),
        chestCount,
        entries: index,
      },
      null,
      2,
    ) + "\n",
  );
  console.log(
    `PASS: all ${enabled.length} enabled item types and ${entries.length - enabled.length} variant samples verified through Paper and the API.`,
  );
} catch (error) {
  console.error(error);
  process.exitCode = 1;
} finally {
  clearTimeout(timeout);
  if (rcon) await rcon.end();
  bot.quit();
}
