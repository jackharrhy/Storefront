import React from 'react'
import interpolate from 'color-interpolate';

const colormap = interpolate(['#ff0000', '#ffa500', '#00ff00']);

export default ({ maxDurability, damage }) => {
	const damageNormalized = ((maxDurability - damage) / maxDurability) * 100;

	return (
		<div className="damage">
			<div
				className="health"
				style={{
					width: `${damageNormalized}%`,
					backgroundColor: colormap(damageNormalized / 100),
				}}
			/>
		</div>
	);
};
