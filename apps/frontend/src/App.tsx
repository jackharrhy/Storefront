import { readStorefrontOptions } from "@storefront/shared";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useSearchParams, useNavigation } from "react-router-dom";
import classNames from "classnames";
import { groupStorefronts, storefrontQuery } from "./storefront.ts";
import Storefront from "./Component/Storefront.tsx";
import ItemViewer from "./Component/ItemViewer.tsx";
import { LoadingPage } from "./route-status.tsx";
import type { Item } from "@storefront/shared";

export default function App() {
  const [search] = useSearchParams();
  const options = readStorefrontOptions(search);
  return <StorefrontPage key={options.username} {...options} />;
}

function StorefrontPage({
  username,
  simpleUI,
  timestamp,
}: {
  username: string | null;
  simpleUI: boolean;
  timestamp: boolean;
}) {
  const query = useQuery(storefrontQuery);
  const navigation = useNavigation();
  const [currentItem, setCurrentItem] = useState<Item | null>(null);
  if (!query.data) return <LoadingPage />;
  const players = groupStorefronts(query.data, username);
  const busy = query.isFetching || navigation.state !== "idle";
  return (
    <main id="app-root">
      {!simpleUI && (
        <>
          <header>
            <h1>Storefront</h1>
          </header>
          <div id="refresh">
            <button disabled={busy} onClick={() => void query.refetch()}>
              Refresh
            </button>
            <output className="refresh-status">{busy ? "Updating storefronts…" : ""}</output>
          </div>
        </>
      )}
      {query.error && (
        <p className="status" role="alert">
          {query.error.message} Showing the last loaded shops.
          <button disabled={busy} onClick={() => void query.refetch()}>
            Retry
          </button>
        </p>
      )}
      {Object.keys(players).length === 0 && <p className="status">No storefronts found.</p>}
      {Object.entries(players).map(([uuid, shops]) => (
        <section className="user" key={uuid} aria-label={`${shops[0].owner.name}'s shops`}>
          <div className="user-name">
            <p title={uuid}>{shops[0].owner.name}</p>
          </div>
          {timestamp && (
            <p className="timestamp">{new Date(query.dataUpdatedAt).toLocaleString()}</p>
          )}
          <div
            className={classNames("storefront-container", {
              "storefront-container-full": username !== null,
            })}
          >
            <Storefront usersStorefronts={shops} setCurrentItem={setCurrentItem} />
          </div>
        </section>
      ))}
      {currentItem && (
        <ItemViewer currentItem={currentItem} clearCurrentItem={() => setCurrentItem(null)} />
      )}
    </main>
  );
}
