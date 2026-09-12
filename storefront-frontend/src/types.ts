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
