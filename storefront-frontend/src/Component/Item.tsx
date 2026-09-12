import type { Item as ItemData } from "../types.ts";
import { useState } from "react";

import Damage from "./Damage.tsx";

import empty from "../assets/empty.png";
import missing from "../assets/missing.png";

export default function Item({
  item,
  setCurrentItem,
}: {
  item: ItemData | null;
  setCurrentItem: (item: ItemData) => void;
}) {
  const [imageError, setImageError] = useState<string | null>(null);

  if (item === null) {
    return (
      <div className="item">
        <img title={"Empty"} alt={"Empty"} src={empty} />
      </div>
    );
  }

  const imageName = item.key.replace(/^minecraft:/, "");
  const alt = item.amount > 1 ? `${item.name} (${item.amount})` : item.name;

  return (
    <button type="button" aria-label={alt} className="item" onClick={() => setCurrentItem(item)}>
      <img
        className={imageError === imageName ? "empty" : undefined}
        title={alt}
        alt={alt}
        src={
          imageError === imageName ? missing : `${import.meta.env.BASE_URL}images/${imageName}.png`
        }
        onError={() => {
          setImageError(imageName);
        }}
      />
      <span className="count">{item.amount > 1 ? item.amount : null}</span>
      {item.maxDurability > 0 && Number(item.meta?.Damage ?? item.meta?.damage) > 0 && (
        <Damage
          maxDurability={item.maxDurability}
          damage={Number(item.meta.Damage ?? item.meta.damage)}
        />
      )}
    </button>
  );
}
