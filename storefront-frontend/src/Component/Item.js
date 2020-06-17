import React from 'react'

import empty from '../assets/empty.png';
import missing from '../assets/missing.png';

const imageFallback = (event) => {
	event.target.src = missing;
	event.target.className = 'empty'
};

export default ({ item, setCurrentItem }) => {
	if (item === null) {
		return (
			<div className="item">
				<img title={"Empty"} alt={"Empty"} src={empty} />
			</div>
		);
	}

	const alt = `${item.name} (${item.amount})`;

	return (
		<div
			className="item"
			onClick={() => setCurrentItem(item)}
		>
			<img
				title={alt}
				alt={alt}
				src={`./images/${item.image}.png`}
				onError={imageFallback}
			/>
		</div>
	);
};
