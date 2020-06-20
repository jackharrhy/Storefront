import React from 'react';
import JSONPretty from 'react-json-pretty';
import 'react-json-pretty/themes/monikai.css';

import Item from '../Component/Item';

export default ({ currentItem, clearCurrentItem }) => {
	return (
		<div id="item-viewer">
			<div>
				<Item
					item={currentItem}
					setCurrentItem={() => {}}
				/>
				<div className="json">
					<JSONPretty data={currentItem}></JSONPretty>
				</div>
				<button className="close" onClick={clearCurrentItem}>×</button>
			</div>
		</div>
	);
};
