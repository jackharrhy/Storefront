# NOTE: this is a tool used to create the 'images' folder than storefront expects,
# from the following tools built by CyclopsMC
#
# https://www.curseforge.com/minecraft/mc-mods/iconexporter
# https://github.com/CyclopsMC/IconExporter

import os
import shutil

for filename in os.listdir('./'):
	if filename.endswith(".png"):
		name = filename.split("__")[1].split(".")[0]
		print(name)
		shutil.copy(filename, f"../{name}.png")
