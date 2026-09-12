import type { Item as ItemData, Storefront as StorefrontData } from "../types.ts";
import Item from "./Item.tsx";

export default function Storefront({
  usersStorefronts,
  setCurrentItem,
}: {
  usersStorefronts: StorefrontData[];
  setCurrentItem: (item: ItemData) => void;
}) {
  return usersStorefronts.map((sf) => (
    <div className="storefront" key={sf.id}>
      <div className="sign">
        {sf.description.slice(1).map((line, i) => (
          <p key={i}>{line}</p>
        ))}
      </div>
      <div className="items">
        {sf.contents.map((item, i) => (
          <Item item={item} key={i} setCurrentItem={setCurrentItem} />
        ))}
      </div>
    </div>
  ));
}
