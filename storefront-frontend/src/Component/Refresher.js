import React from 'react'

export default ({ loadData }) => {
	return (
		<div id="refresh">
			<button onClick={loadData}>
				Refresh
			</button>
		</div>
	)
}
