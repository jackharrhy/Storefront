import React, {useState, useEffect} from 'react';
import Refesher from './Component/Refresher';
import minecraftItems from 'minecraft-items'

import testJson from './test';
import missing from './missing.png';
import empty from './empty.png';

function App() {
  const [storefront, setStorefront] = useState([]);

  useEffect(() => {
    const getData = async () => {
      console.log(testJson);
      setStorefront(testJson);
    };
    getData();
  }, []);

  const refreshData = () => {
    console.log(storefront);
  };

  return (
    <div id="root">
      <Refesher
        onClick={refreshData}
      />
      {
        storefront.map((sf) => (
          <div className="storefront">
            {
              sf.map((item) => {
                if (item === null) {
                  return <div className="item">
                    <img title={"Empty"} alt={"Empty"} src={empty} />
                  </div>;
                } else {
                  const itemData = minecraftItems.get(item.key.slice(10));
                  const alt = item.name;
                  return (
                    <div class="item">
                      {
                        itemData ? (
                          <img title={alt} alt={alt} src={`data:image/png;base64,${itemData.icon}`} />
                        ) : (
                          <img title={alt} alt={alt} src={missing} />
                        )
                      }
                    </div>
                  );
                }
              })
            }
          </div>
        ))
      }
    </div>
  );
}

export default App;
