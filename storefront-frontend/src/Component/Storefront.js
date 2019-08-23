import React from 'react'

import Item from './Item';

export default ({usersStorefronts}) => {
  return usersStorefronts.map(((sf, index) => (
    <div className="storefront" id={index}>
      <div className="sign">
        {
          sf.description.slice(1).map((line) => (
            <p>{line}</p>
          ))
        }
      </div>
      <div className="items">
        {
          sf.contents.map((item, id) => (
            <Item
              item={item}
              id={id}
            />
          ))
        }
      </div>
    </div>
  )));
}