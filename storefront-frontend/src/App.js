import React, {useState, useEffect} from 'react';

import Refesher from './Component/Refresher';
import Storefront from './Component/Storefront';
import MaterialTester from './Component/MaterialTester';

function App() {
  const [storefront, setStorefront] = useState([]);

  useEffect(() => {
    const getData = async () => {
      const storefrontResponse = await fetch('./storefront/');
      const storefrontJson = await storefrontResponse.json();
      const players = {};
      storefrontJson.map(async (sf) => {
        sf.contents.map((item) => {
          if (item !== null) {
            item.image = item.key.slice(10,item.key.length);
          }
        });
        players[sf.owner.uuid] ? players[sf.owner.uuid].push(sf) : players[sf.owner.uuid] = [sf];
      })
      setStorefront(players);
    };
    getData();
  }, []);

  const refreshData = () => {
    console.log(storefront);
  };

  return (
    <div id="app-root">
      {/*<MaterialTester />*/}
      <Refesher
        onClick={refreshData}
      />
      <header>
        <h1>Storefront</h1>
      </header>
      {
        Object.entries(storefront).map(([userUUID, usersStorefronts]) => (
          <div className="user">
            <div className="user-name">
              <p title={userUUID}>{usersStorefronts[0].owner.name}</p>
            </div>
            <Storefront
              usersStorefronts={usersStorefronts}
            />
          </div>
        ))
      }
    </div>
  );
}

export default App;