require("dotenv").config();

module.exports = {
  commandPrefix: process.env.STOREFRONT_COMMAND_PREFIX || "sf!",
  discordToken: process.env.STOREFRONT_DISCORD_TOKEN,
  storefrontUrl:
    process.env.STOREFRONT_URL || "http://localhost:8080/",
};
