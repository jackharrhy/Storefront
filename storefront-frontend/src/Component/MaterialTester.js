import React from 'react'

import allMaterialsJson from '../test-data/all-materials';

export default () => {
  console.log(allMaterialsJson);
  return (
    <div
      id="material-tester"
    >
      {
        Object.entries(allMaterialsJson).map(([key, enumerator]) => (
          <div>
            <p>{`${key}`}</p>
            <div className="item">
              <img title={key} alt={key} src={`./images/${key}.png`} />
            </div>
          </div>
        ))
      }
    </div>
  )
}