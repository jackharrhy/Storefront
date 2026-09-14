import { storefrontUrl } from "@storefront/shared";
import type { MessageCreateOptions } from "discord.js";

export type Command = { name: "ping" } | { name: "show"; username: string };

export function parseCommand(content: string, prefix: string): Command | null {
  if (!content.startsWith(prefix)) return null;
  const [name, ...args] = content.slice(prefix.length).trim().split(/\s+/);
  if (name === "ping") return { name };
  if (name === "show") return { name, username: args.join(" ") };
  return null;
}

export async function commandReply(
  command: Command,
  config: { storefrontUrl: string; publicUrl: string; commandPrefix: string },
  render: (url: string) => Promise<Uint8Array | null>,
): Promise<MessageCreateOptions> {
  const allowedMentions: MessageCreateOptions["allowedMentions"] = {
    parse: [],
    repliedUser: false,
  };
  if (command.name === "ping") return { content: "pong!", allowedMentions };
  if (!/^[a-zA-Z0-9_]{1,16}$/.test(command.username))
    return { content: `Usage: ${config.commandPrefix}show <Minecraft username>`, allowedMentions };
  const url = storefrontUrl(config.storefrontUrl, {
    username: command.username,
    simpleUI: true,
    timestamp: true,
  }).toString();
  const screenshot = await render(url);
  if (!screenshot) return { content: "No storefronts found for that player.", allowedMentions };
  return {
    content: `<${storefrontUrl(config.publicUrl, { username: command.username, simpleUI: false, timestamp: false })}>`,
    files: [{ attachment: Buffer.from(screenshot), name: "storefront.png" }],
    allowedMentions,
  };
}
