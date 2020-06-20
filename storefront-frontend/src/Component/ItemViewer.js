import React from 'react';
import JSONPretty from 'react-json-pretty';
import 'react-json-pretty/themes/monikai.css';

import Item from '../Component/Item';

// TODO nice non-json view of most data, with json view always there but more hidden
const showJson = true;

export default ({ currentItem, clearCurrentItem }) => {
	return (
		<div id="item-viewer">
			<div>
				<Item
					item={currentItem}
					setCurrentItem={() => {}}
				/>
				{showJson && (
					<div className="json">
						<JSONPretty data={currentItem}></JSONPretty>
					</div>
				)}
				<p>{currentItem.name}</p>
				<button className="close" onClick={clearCurrentItem}>×</button>
			</div>
		</div>
	);
};
