const renderPage = require("./utils/render-page");
const { storefrontUrl } = require("./config");

module.exports = {
  ping: async (message) => message.channel.send("pong!"),
  show: async (message, username) => {
    if (!username) return message.reply("Usage: show <Minecraft username>");
    const url = new URL(storefrontUrl);
    url.search = new URLSearchParams({
      username,
      simpleUI: "",
      timestamp: "",
    }).toString();
    const screenshot = await renderPage(url.toString());
    if (screenshot === null) return message.reply("Unknown user!");
    return message.reply({
      files: [{ attachment: Buffer.from(screenshot), name: "storefront.png" }],
    });
  },
};
