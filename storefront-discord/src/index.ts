import { Client, Events, GatewayIntentBits, Partials } from "discord.js";
import { readConfig } from "./config.ts";
import { parseCommand, commandReply } from "./commands.ts";
import { ScreenshotRenderer } from "./render-page.ts";
import { storefrontUrl } from "@storefront/shared";

const config = readConfig();
const renderer = new ScreenshotRenderer();
const client = new Client({
  intents: [
    GatewayIntentBits.Guilds,
    GatewayIntentBits.GuildMessages,
    GatewayIntentBits.DirectMessages,
    GatewayIntentBits.MessageContent,
  ],
  partials: [Partials.Channel],
  allowedMentions: { parse: [], repliedUser: false },
});

client.on(Events.Error, console.error);
client.once(Events.ClientReady, (readyClient) => console.log(`Logged in: ${readyClient.user.tag}`));
client.on(Events.MessageCreate, async (message) => {
  if (message.author.bot) return;
  const command = parseCommand(message.content, config.commandPrefix);
  if (!command) return;
  try {
    await message.reply(await commandReply(command, config, (url) => renderer.render(url)));
  } catch (error) {
    console.error(error);
    const url = storefrontUrl(config.publicUrl, {
      username: command.name === "show" ? command.username : null,
      simpleUI: false,
      timestamp: false,
    });
    await message
      .reply({
        content: `Unable to complete that request. Try again shortly or open <${url}>.`,
        allowedMentions: { parse: [], repliedUser: false },
      })
      .catch(console.error);
  }
});

async function shutdown() {
  await client.destroy();
  await renderer.close();
}
for (const signal of ["SIGINT", "SIGTERM"] as const) {
  process.once(signal, () => {
    void shutdown().catch(console.error);
  });
}
try {
  await client.login(config.token);
} catch (error) {
  console.error(error);
  process.exitCode = 1;
  await shutdown();
}
