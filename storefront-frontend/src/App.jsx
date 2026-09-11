import { useLocation } from "react-router-dom";
import classNames from "classnames";

import { useStorefront } from "./storefront.js";
import Refresher from "./Component/Refresher";
import Storefront from "./Component/Storefront";
import ItemViewer from "./Component/ItemViewer";

import ironPickaxe from "./assets/iron_pickaxe.png";

function useQuery() {
  return new URLSearchParams(useLocation().search);
}

function App() {
  const query = useQuery();

  const simpleUI = query.get("simpleUI") !== null;
  const timestamp =
    query.get("timestamp") !== null ? new Date().toLocaleString() : null;
  const username = query.get("username");

  const [state, actions] = useStorefront(username);

  return (
    <div id="app-root">
      {!simpleUI && (
        <header>
          <h1>Storefront</h1>
        </header>
      )}
      {state.loading ? (
        <img
          id="loading-pickaxe"
          src={ironPickaxe}
          alt="Loading storefronts"
          role="status"
        />
      ) : (
        <>
          {!simpleUI && <Refresher loadData={actions.loadData} />}
          {state.error && (
            <p className="status" role="alert">
              {state.error} <button onClick={actions.loadData}>Retry</button>
            </p>
          )}
          {!state.error && Object.keys(state.players).length === 0 && (
            <p className="status">No storefronts found.</p>
          )}
          {Object.entries(state.players).map(([userUUID, usersStorefronts]) => (
            <div className="user" key={userUUID}>
              <div className="user-name">
                <p title={userUUID}>{usersStorefronts[0].owner.name}</p>
              </div>
              {timestamp && <p className={"timestamp"}>{timestamp}</p>}
              <div
                className={classNames("storefront-container", {
                  "storefront-container-full": username !== null,
                })}
              >
                <Storefront
                  usersStorefronts={usersStorefronts}
                  setCurrentItem={actions.setCurrentItem}
                />
              </div>
            </div>
          ))}
        </>
      )}
      {state.currentItem && (
        <ItemViewer
          currentItem={state.currentItem}
          clearCurrentItem={actions.clearCurrentItem}
        />
      )}
    </div>
  );
}

export default App;
