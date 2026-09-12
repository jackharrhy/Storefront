import assert from 'node:assert/strict';
import { once } from 'node:events';
import mineflayer from 'mineflayer';
import { Rcon } from 'rcon-client';
import { Vec3 } from 'vec3';

const host = process.env.MC_HOST || 'paper';
const api = process.env.API_URL || 'http://paper:7000';
const username = 'StorefrontBot';
const bot = mineflayer.createBot({
  host,
  port: Number(process.env.MC_PORT || 25565),
  username,
  auth: 'offline',
  version: process.env.MC_VERSION || '26.1',
});
const timeout = setTimeout(() => { console.error('Smoke test timed out'); process.exit(1); }, 90_000);
bot.on('kicked', (reason) => console.error('Bot kicked:', reason));
bot.on('error', (error) => console.error('Bot error:', error));
let rcon;

async function listings() {
  const response = await fetch(`${api}/storefronts/`);
  assert.equal(response.status, 200);
  return response.json();
}

async function refresh() {
  const deadline = Date.now() + 60_000;
  let reply;
  do {
    reply = await rcon.send('storefrontforceupdate');
    let status;
    do {
      await bot.waitForTicks(2);
      status = JSON.parse((await rcon.send('storefrontrefreshstatus')).replace(/\u001b\[[0-9;]*m|§./g, '').trim());
      assert(!status.error, status.error);
      assert(Date.now() < deadline, 'Refresh did not complete');
    } while (status.running);
  } while (!reply.includes('queued'));
}

async function waitForListing(predicate) {
  for (let attempt = 0; attempt < 50; attempt++) {
    const shops = (await listings()).filter((shop) => shop.owner.name === username);
    if (predicate(shops)) return shops;
    await bot.waitForTicks(2);
  }
  throw new Error('Storefront API did not reach the expected state');
}

try {
  await once(bot, 'spawn');
  console.log(`Joined Paper as ${username} using Minecraft ${bot.version}`);
  rcon = await Rcon.connect({ host, port: 25575, password: process.env.RCON_PASSWORD || 'storefront-local-only' });
  const commands = [
    'fill -5 64 -5 5 64 5 minecraft:stone',
    'setblock 0 65 1 air',
    'setblock 0 65 0 air',
    'storefrontforceupdate',
    'setblock 0 65 0 minecraft:chest[facing=south]',
    'item replace block 0 65 0 container.0 with minecraft:diamond 3',
    'item replace block 0 65 0 container.1 with minecraft:iron_pickaxe[minecraft:damage=42] 1',
    'setblock 0 65 1 minecraft:oak_wall_sign[facing=south]{front_text:{messages:["[storefront]","Headless test","Diamonds for sale",""]},is_waxed:1b}',
    `gamemode creative ${username}`,
    `tp ${username} 0.5 65 3.5`,
  ];
  for (const command of commands) {
    if (command === 'storefrontforceupdate') await refresh();
    else console.log(await rcon.send(command));
  }
  await bot.waitForChunksToLoad();
  await bot.waitForTicks(10);
  const signPosition = new Vec3(0, 65, 1);
  const sign = bot.blockAt(signPosition);
  assert.equal(sign?.name, 'oak_wall_sign');
  await bot.activateBlock(sign);
  const [created] = await waitForListing((shops) => shops.length === 1);
  assert.equal(created.contents[0].key, 'minecraft:diamond');
  assert.equal(created.contents[0].amount, 3);
  assert.equal(created.description[1], 'Headless test');
  assert.equal(created.contents.length, 27);
  console.log(`Created storefront ${created.id} by right-clicking the sign`);

  await rcon.send('item replace block 0 65 0 container.0 with minecraft:diamond 7');
  await refresh();
  const [updated] = await waitForListing((shops) => shops[0]?.contents[0]?.amount === 7);
  assert.equal(updated.id, created.id);
  console.log('Inventory refresh preserved the storefront ID and updated the item count');

  await bot.dig(bot.blockAt(signPosition));
  await waitForListing((shops) => shops.length === 0);
  console.log('Breaking the sign removed the storefront');

  await rcon.send(commands[7]);
  await bot.waitForTicks(5);
  await bot.activateBlock(bot.blockAt(signPosition));
  await waitForListing((shops) => shops.length === 1);
  console.log('PASS: left a demo shop at 0, 65, 0 for the web UI');
} catch (error) {
  console.error(error);
  process.exitCode = 1;
} finally {
  clearTimeout(timeout);
  if (rcon) await rcon.end();
  bot.quit();
}
