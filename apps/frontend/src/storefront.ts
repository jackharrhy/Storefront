import { queryOptions } from "@tanstack/react-query";
import { storefrontsSchema, type Storefront } from "@storefront/shared";

export const storefrontQuery = queryOptions({
  queryKey: ["storefronts"],
  queryFn: async ({ signal }): Promise<Storefront[]> => {
    const response = await fetch("/api/storefronts/", { signal });
    if (!response.ok) throw new Error(`Unable to load storefronts (${response.status}).`);
    const data: unknown = await response.json();
    const result = storefrontsSchema.safeParse(data);
    if (!result.success) throw new Error("The server returned invalid storefront data.");
    return result.data;
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
