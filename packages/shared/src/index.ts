export interface Item {
  name: string;
  key: string;
  amount: number;
  meta: Record<string, unknown>;
  isBlock: boolean;
  maxDurability: number;
}

export interface Storefront {
  id: number;
  owner: { uuid: string; name: string };
  contents: (Item | null)[];
  description: string[];
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
function isItem(value: unknown): value is Item {
  return (
    isRecord(value) &&
    typeof value.name === "string" &&
    typeof value.key === "string" &&
    Number.isInteger(value.amount) &&
    isRecord(value.meta) &&
    typeof value.isBlock === "boolean" &&
    typeof value.maxDurability === "number"
  );
}
function isStorefront(value: unknown): value is Storefront {
  return (
    isRecord(value) &&
    Number.isInteger(value.id) &&
    isRecord(value.owner) &&
    typeof value.owner.uuid === "string" &&
    typeof value.owner.name === "string" &&
    Array.isArray(value.description) &&
    value.description.every((line) => typeof line === "string") &&
    Array.isArray(value.contents) &&
    value.contents.every((item) => item === null || isItem(item))
  );
}

export function parseStorefronts(data: unknown): Storefront[] {
  if (!Array.isArray(data) || !data.every(isStorefront))
    throw new Error("The server returned invalid storefront data.");
  return data;
}

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
