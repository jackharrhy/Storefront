import { read } from "nbtify";

export async function decodeNbt(base64) {
  const bytes = Uint8Array.from(atob(base64), (character) =>
    character.charCodeAt(0),
  );
  const data = await read(bytes);
  return data.data;
}
