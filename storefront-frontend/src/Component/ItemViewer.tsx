import { useEffect } from "react";
import { useQuery } from "@tanstack/react-query";
import Item from "./Item.tsx";
import type { Item as ItemData } from "@storefront/shared";

export default function ItemViewer({
  currentItem: item,
  clearCurrentItem,
}: {
  currentItem: ItemData;
  clearCurrentItem: () => void;
}) {
  const internal = typeof item.meta.internal === "string" ? item.meta.internal : null;
  const decoded = useQuery({
    queryKey: ["nbt", internal],
    enabled: internal !== null,
    queryFn: async () => {
      if (internal === null) return null;
      const { decodeNbt } = await import("../nbt.ts");
      return decodeNbt(internal);
    },
    staleTime: Infinity,
    retry: false,
  });
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") clearCurrentItem();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [clearCurrentItem]);
  return (
    <section id="item-viewer" aria-label={`Details for ${item.name}`}>
      <div>
        <Item item={item} setCurrentItem={() => {}} />
        <div className="json">
          <pre>
            {JSON.stringify(
              {
                item,
                ...(internal === null ? {} : { nbt: decoded.data, error: decoded.error?.message }),
              },
              (_key: string, value: unknown) =>
                typeof value === "bigint" ? value.toString() : value,
              2,
            )}
          </pre>
        </div>
        <p>{item.name}</p>
        <button className="close" aria-label="Close item details" onClick={clearCurrentItem}>
          ×
        </button>
      </div>
    </section>
  );
}
