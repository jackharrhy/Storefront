import { useEffect, useState } from "react";
import Item from "./Item.jsx";

export default function ItemViewer({ currentItem: item, clearCurrentItem }) {
  const [decoded, setDecoded] = useState(null);
  useEffect(() => {
    let active = true;
    if (item.meta?.internal) {
      import("../nbt.js")
        .then(({ decodeNbt }) => decodeNbt(item.meta.internal))
        .then((nbt) => {
          if (active) setDecoded({ item, nbt });
        })
        .catch((error) => {
          if (active) setDecoded({ item, error: error.message });
        });
    }
    return () => {
      active = false;
    };
  }, [item]);

  useEffect(() => {
    const onKeyDown = (event) => {
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
                ...(decoded?.item === item
                  ? { nbt: decoded.nbt, error: decoded.error }
                  : {}),
              },
              (_key, value) =>
                typeof value === "bigint" ? value.toString() : value,
              2,
            )}
          </pre>
        </div>
        <p>{item.name}</p>
        <button
          className="close"
          aria-label="Close item details"
          onClick={clearCurrentItem}
        >
          ×
        </button>
      </div>
    </section>
  );
}
