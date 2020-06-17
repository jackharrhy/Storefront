import React, {useState} from 'react'
import JSONPretty from 'react-json-pretty';
import 'react-json-pretty/themes/monikai.css';

import empty from '../empty.png';

const imageFallback = (event) => {
  event.target.src = 'missing.png';
  event.target.className = 'empty'
};

export default ({item}) => {
  const [showMeta, setShowMeta] = useState(false);

  if (item === null) {
    return (
      <div className="item">
        <img title={"Empty"} alt={"Empty"} src={empty} />
      </div>
    );
  }

  const alt = `${item.name} (${item.amount})`;

  return (
    <>
      <div
        className="item"
        onClick={() => setShowMeta(!showMeta)}
      >
        <img
          title={alt}
          alt={alt}
          src={`./images/${item.image}.png`}
          onError={imageFallback}
        />
      </div>
      { showMeta && (
          <div className="item-meta">
            <JSONPretty id="json-pretty" data={item}></JSONPretty>
          </div>
        )
      }
    </>
  );
};