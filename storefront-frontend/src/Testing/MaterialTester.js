import React from 'react'

// TODO this file is out of date, update to 1.15!
import allMaterialsJson from './all-materials';

export default () => {
	return (
		<div id="material-tester">
			{
				Object.entries(allMaterialsJson).map(([key]) => (
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
