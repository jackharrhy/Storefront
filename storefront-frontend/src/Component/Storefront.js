import React from 'react'

import Item from './Item';

export default ({usersStorefronts}) => {
  return usersStorefronts.map((sf => (
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
          sf.contents.map((item) => (
            <Item item={item} />
          ))
        }
      </div>
    </div>
  )));
}