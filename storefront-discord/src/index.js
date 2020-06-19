const Discord = require('discord.js');
const {
  commandPrefix,
  discordToken,
}= require('./config');

const client = new Discord.Client();

(async () => {
  const responses = await require('./responses')();

  client.on('error', console.error);

  client.on('ready', async () => {
    console.log(`loggedin: ${client.user.tag}`);
  });

  client.on('message', async (msg) => {
		const content = msg.content;
    if (content.startsWith(commandPrefix)) {
			const command = content.substring(commandPrefix.length);

      for (key in responses) {
        if (command.startsWith(key)) {
          try {
            await responses[key](msg, command.substring(key.length));
          }
          catch(err) {
            const {message, stack} = err;
            console.error(stack);
            try {
              await msg.reply(`${message}\`\`\`${stack}\`\`\``);
            }
            catch {
              await msg.reply(`${message}\`\`\`${stack.substring(0,1750)}\n...\`\`\``);
            }
          }
          return;
        }
      }
    }
  });

  client.login(discordToken);
})();
