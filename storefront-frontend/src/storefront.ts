import { queryOptions } from "@tanstack/react-query";
import type { Item, Storefront } from "./types.ts";

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

export const storefrontQuery = queryOptions({
  queryKey: ["storefronts"],
  queryFn: async ({ signal }): Promise<Storefront[]> => {
    const response = await fetch("/api/storefronts/", { signal });
    if (!response.ok) throw new Error(`Unable to load storefronts (${response.status}).`);
    const data: unknown = await response.json();
    if (!Array.isArray(data) || !data.every(isStorefront))
      throw new Error("The server returned invalid storefront data.");
    return data;
  },
  staleTime: 30_000,
  refetchOnWindowFocus: false,
  retry: false,
});

export function groupStorefronts(storefronts: Storefront[], username: string | null) {
  const players: Record<string, Storefront[]> = {};
  for (const storefront of storefronts) {
    if (username !== null && storefront.owner.name !== username) continue;
    (players[storefront.owner.uuid] ??= []).push(storefront);
  }
  return players;
}
