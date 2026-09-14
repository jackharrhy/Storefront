import { createHash } from "node:crypto";
import { mkdir, readFile, readdir, rename, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { parseArgs } from "node:util";
import { unzipSync } from "fflate";

// This pinned export is labelled "26.2 Pre-Release 2" upstream.
const revision = "96b9b546b8797b1b544a8d9eed67c29b2a90b4cc";
const sha256 = "d5245d8b883612a639a0f536f069aa921e298a1dd164eb80c92981b8659413b6";
const url = `https://codeload.github.com/Owen1212055/mc-assets/zip/${revision}`;
const { values } = parseArgs({
  options: { output: { type: "string" }, force: { type: "boolean" } },
});
const output = values.output
  ? resolve(values.output)
  : resolve(dirname(fileURLToPath(import.meta.url)), "../public/images");
const manifestPath = join(output, ".storefront-icons.json");

async function main() {
  if (!values.force) {
    try {
      const manifest = JSON.parse(await readFile(manifestPath, "utf8"));
      const files = new Set(await readdir(output));
      if (
        manifest.sha256 === sha256 &&
        manifest.files?.length > 0 &&
        manifest.files.every((name: string) => files.has(name))
      ) {
        console.log(`Using ${manifest.files.length} cached Minecraft inventory icons.`);
        return;
      }
    } catch (error) {
      if (
        !(error instanceof Error && "code" in error && error.code === "ENOENT") &&
        !(error instanceof SyntaxError)
      )
        throw error;
    }
  }

  console.log("Downloading pinned Minecraft 26.2 pre-release inventory renders…");
  const response = await fetch(url, { signal: AbortSignal.timeout(120_000) });
  if (!response.ok) throw new Error(`Icon download failed: HTTP ${response.status}`);
  const archive = new Uint8Array(await response.arrayBuffer());
  if (createHash("sha256").update(archive).digest("hex") !== sha256)
    throw new Error("Icon archive checksum mismatch");

  const prefix = `mc-assets-${revision}/item-assets/`;
  const icons = unzipSync(archive, {
    filter: ({ name }) =>
      name.startsWith(prefix) && /^[A-Z0-9_]+\.png$/.test(name.slice(prefix.length)),
  });
  if (Object.keys(icons).length === 0) throw new Error("Archive contains no inventory icons");
  await mkdir(output, { recursive: true });
  const files = [];
  for (const [path, data] of Object.entries(icons)) {
    const name = path.slice(prefix.length).toLowerCase();
    if (Buffer.from(data.subarray(0, 8)).toString("hex") !== "89504e470d0a1a0a")
      throw new Error(`Invalid PNG: ${name}`);
    await writeFile(join(output, `${name}.tmp`), data);
    await rename(join(output, `${name}.tmp`), join(output, name));
    files.push(name);
  }
  await writeFile(
    manifestPath,
    JSON.stringify({ source: url, revision, sha256, files: files.sort() }, null, 2) + "\n",
  );
  console.log(`Installed ${files.length} inventory icons in ${output}`);
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
