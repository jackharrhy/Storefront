require('dotenv').config();

module.exports = {
	commandPrefix: process.env.STOREFRONT_COMMAND_PREFIX,
	discordToken: process.env.STOREFRONT_DISCORD_TOKEN,
};
