const { Client, Events, GatewayIntentBits, Partials } = require("discord.js");
const { commandPrefix, discordToken } = require("./config");
const responses = require("./responses");

const client = new Client({
  intents: [
    GatewayIntentBits.Guilds,
    GatewayIntentBits.GuildMessages,
    GatewayIntentBits.DirectMessages,
    GatewayIntentBits.MessageContent,
  ],
  partials: [Partials.Channel],
});

client.on(Events.Error, console.error);
client.once(Events.ClientReady, (readyClient) =>
  console.log(`Logged in: ${readyClient.user.tag}`),
);
client.on(Events.MessageCreate, async (message) => {
  if (message.author.bot || !message.content.startsWith(commandPrefix)) return;
  const [command, ...args] = message.content
    .slice(commandPrefix.length)
    .trim()
    .split(/\s+/);
  const respond = Object.hasOwn(responses, command) ? responses[command] : null;
  if (!respond) return;
  try {
    await respond(message, args.join(" "));
  } catch (error) {
    console.error(error);
    await message
      .reply("Unable to complete that request. Please try again later.")
      .catch(console.error);
  }
});

client.login(discordToken).catch((error) => {
  console.error(error);
  process.exitCode = 1;
  client.destroy();
});
