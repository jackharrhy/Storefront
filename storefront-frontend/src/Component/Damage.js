import React from 'react'
import interpolate from 'color-interpolate';

// TODO ingame one seems to clamp it at higher values during the transition
const colormap = interpolate(['#ff0000', '#00ff00']);

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
