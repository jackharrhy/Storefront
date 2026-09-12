import { queryOptions } from "@tanstack/react-query";
import { parseStorefronts, type Storefront } from "@storefront/shared";

export const storefrontQuery = queryOptions({
  queryKey: ["storefronts"],
  queryFn: async ({ signal }): Promise<Storefront[]> => {
    const response = await fetch("/api/storefronts/", { signal });
    if (!response.ok) throw new Error(`Unable to load storefronts (${response.status}).`);
    const data: unknown = await response.json();
    return parseStorefronts(data);
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
