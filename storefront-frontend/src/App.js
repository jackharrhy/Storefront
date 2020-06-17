import 'regenerator-runtime/runtime';

import React, { useEffect } from 'react';

import Refesher from './Component/Refresher';
import Storefront from './Component/Storefront';
import { useStorefront } from './storefront.js';

import ironPickaxe from './assets/iron_pickaxe.png';

function App() {
	const [state, actions] = useStorefront();

	useEffect(() => {
		actions.loadData();
	}, []);

	return (
		<div id="app-root">
			<Refesher
				onClick={() => actions.loadData()}
			/>
			<header>
				<h1>Storefront</h1>
			</header>
			{state.loading ? (
				<img id="loading-pickaxe" src={ironPickaxe} />
			) : Object.entries(state.players).map(([userUUID, usersStorefronts]) => (
				<div className="user" key={userUUID}>
					<div className="user-name">
						<p title={userUUID}>{usersStorefronts[0].owner.name}</p>
					</div>
					<div className="storefront-container">
						<Storefront
							usersStorefronts={usersStorefronts}
							setCurrentItem={actions.setCurrentItem}
						/>
					</div>
				</div>
			))}
      )}
		</div>
	);
}

export default App;
