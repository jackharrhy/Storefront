import { config } from "dotenv";
import { fileURLToPath } from "node:url";

config({ path: fileURLToPath(new URL("../.env", import.meta.url)), quiet: true });

export function readConfig(env: NodeJS.ProcessEnv = process.env) {
  const token = env.STOREFRONT_DISCORD_TOKEN?.trim();
  if (!token) throw new Error("Set STOREFRONT_DISCORD_TOKEN in storefront-discord/.env.");
  const storefrontUrl = new URL(env.STOREFRONT_URL || "http://localhost:8080/");
  if (!["http:", "https:"].includes(storefrontUrl.protocol))
    throw new Error("STOREFRONT_URL must be an HTTP or HTTPS URL.");
  const commandPrefix = env.STOREFRONT_COMMAND_PREFIX || "sf!";
  if (!commandPrefix.trim()) throw new Error("STOREFRONT_COMMAND_PREFIX cannot be blank.");
  return { token, commandPrefix, storefrontUrl: storefrontUrl.toString() };
}
