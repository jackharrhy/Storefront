import React, {useState, useEffect} from 'react';
import Refesher from './Component/Refresher';
import minecraftItems from 'minecraft-items'

import missing from './missing.png';
import empty from './empty.png';

function App() {
  const [storefront, setStorefront] = useState([]);

  useEffect(() => {
    const getData = async () => {
      const storefrontResponse = await fetch('./storefront/');
      const storefrontJson = await storefrontResponse.json();
      const players = {};
      storefrontJson.map(async (sf) => {
        players[sf.owner.uuid] ? players[sf.owner.uuid].push(sf) : players[sf.owner.uuid] = [sf];
      })
      console.log(players);
      setStorefront(players);
    };
    getData();
  }, []);

  const refreshData = () => {
    console.log(storefront);
  };

  return (
    <div id="app-root">
      <Refesher
        onClick={refreshData}
      />
      {
        Object.entries(storefront).map(([userUUID, usersStorefronts]) => (
          <div className="user">
            <div className="user-name">
              <p title={userUUID}>{usersStorefronts[0].owner.name}</p>
            </div>
            {
              usersStorefronts.map((sf => (
                <div className="storefront">
                  <div className="sign">
                    {
                      sf.description.slice(1).map((line) => (
                        <p>{line}</p>
                      ))
                    }
                  </div>
                  <div className="items">
                    {
                      sf.contents.map((item) => {
                        if (item === null) {
                          return <div className="item">
                            <img title={"Empty"} alt={"Empty"} src={empty} />
                          </div>;
                        } else {
                          const itemData = minecraftItems.get(item.key.slice(10));
                          const alt = item.name;
                          return (
                            <div className="item">
                              <img title={alt} alt={alt} src={`./static/images/${item.key.slice(10)}.png`} />
                              {/*
                                itemData ? (
                                ) : (
                                  <img title={alt} alt={alt} src={missing} />
                                )
                              */}
                            </div>
                          );
                        }
                      })
                    }
                  </div>
                </div>
              )))
            }
          </div>
        ))
      }
    </div>
  );
}

export default App;
