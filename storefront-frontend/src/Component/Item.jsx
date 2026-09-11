import { useState } from "react";

import Damage from "./Damage";

import empty from "../assets/empty.png";
import missing from "../assets/missing.png";

export default function Item({ item, setCurrentItem }) {
  const [imageError, setImageError] = useState(null);

  if (item === null) {
    return (
      <div className="item">
        <img title={"Empty"} alt={"Empty"} src={empty} />
      </div>
    );
  }

  const amount = `(${item.amount})`;
  const alt = `${item.name} ${item.amount > 1 ? amount : ""}`;

  return (
    <button
      type="button"
      aria-label={alt}
      className="item"
      onClick={() => setCurrentItem(item)}
    >
      <img
        className={imageError === item.image ? "empty" : null}
        title={alt}
        alt={alt}
        src={
          imageError === item.image
            ? missing
            : `${import.meta.env.BASE_URL}images/${item.image}.png`
        }
        onError={() => {
          setImageError(item.image);
        }}
      />
      <p className="count">{item.amount > 1 ? item.amount : null}</p>
      {item.maxDurability > 0 &&
        Number(item.meta?.Damage ?? item.meta?.damage) > 0 && (
          <Damage
            maxDurability={item.maxDurability}
            damage={item.meta.Damage ?? item.meta.damage}
          />
        )}
    </button>
  );
}
