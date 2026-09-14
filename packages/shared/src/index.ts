import { z } from "zod";

export const itemSchema = z.object({
  name: z.string(),
  key: z.string(),
  amount: z.number().int(),
  meta: z.record(z.string(), z.unknown()),
  isBlock: z.boolean(),
  maxDurability: z.number(),
});

export const storefrontSchema = z.object({
  id: z.number().int(),
  owner: z.object({ uuid: z.string(), name: z.string() }),
  contents: z.array(itemSchema.nullable()),
  description: z.array(z.string()),
});

export const storefrontsSchema = z.array(storefrontSchema);

export type Item = z.infer<typeof itemSchema>;
export type Storefront = z.infer<typeof storefrontSchema>;

export function readStorefrontOptions(search: URLSearchParams) {
  return {
    username: search.get("username"),
    simpleUI: search.has("simpleUI"),
    timestamp: search.has("timestamp"),
  };
}

export function storefrontUrl(
  base: string,
  options: ReturnType<typeof readStorefrontOptions>,
): URL {
  const url = new URL(base);
  url.search = "";
  url.hash = "";
  if (options.username !== null) url.searchParams.set("username", options.username);
  if (options.simpleUI) url.searchParams.set("simpleUI", "");
  if (options.timestamp) url.searchParams.set("timestamp", "");
  return url;
}
