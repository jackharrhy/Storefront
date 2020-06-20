import React, { useEffect, useState } from 'react';
import JSONPretty from 'react-json-pretty';
import nbtParser from 'prismarine-nbt';
import { Buffer } from 'buffer';

import 'react-json-pretty/themes/monikai.css';

import Item from '../Component/Item';

// TODO nice non-json view of most data, with json view always there but more hidden
const showJson = true;

function base64ToBuffer(base64) {
	const binaryString = atob(base64);
	const len = binaryString.length;
	const bytes = new Uint8Array(len);
	for (let i = 0; i < len; i++) {
		bytes[i] = binaryString.charCodeAt(i);
	}
	return Buffer.from(bytes.buffer);
}

export default ({ currentItem: item, clearCurrentItem }) => {
	const [nbt, setNbt] = useState(null);

	useEffect(() => {
		if (item.meta?.internal) {
			const buf = base64ToBuffer(item.meta.internal);
			nbtParser.parse(buf, (error, result) => {
				if (error) {
					console.error(error);
				} else {
					setNbt(result);
				}
			});
		}
	}, [item]);

	return (
		<div id="item-viewer">
			<div>
				<Item
					item={item}
					setCurrentItem={() => {}}
				/>
				{showJson && (
					<div className="json">
						<JSONPretty data={{
							item,
							nbt,
						}}/>
					</div>
				)}
				<p>{item.name}</p>
				<button className="close" onClick={clearCurrentItem}>×</button>
			</div>
		</div>
	);
};
