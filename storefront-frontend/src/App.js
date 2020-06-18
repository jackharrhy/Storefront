import 'regenerator-runtime/runtime';

import React, { useEffect } from 'react';
import { useLocation } from "react-router-dom";
import classNames from "classnames";

import { useStorefront } from './storefront.js';
import Refesher from './Component/Refresher';
import Storefront from './Component/Storefront';
import ItemViewer from './Component/ItemViewer';

import ironPickaxe from './assets/iron_pickaxe.png';

function useQuery() {
  return new URLSearchParams(useLocation().search);
}

function App() {
	const [state, actions] = useStorefront();

	const query = useQuery();

	const simpleUI = query.get('simpleUI') === 'true';
	const username = query.get('username');

	useEffect(() => {
		actions.loadData({username});
	}, []);

	return (
		<div id="app-root">
			{!simpleUI && (
				<header>
					<h1>Storefront</h1>
				</header>
			)}
			{state.loading ? (
				<img id="loading-pickaxe" src={ironPickaxe} />
			) : (
				<>
					{!simpleUI && (
						<Refesher
							loadData={() => actions.loadData()}
						/>
					)}
					{Object.entries(state.players).map(([userUUID, usersStorefronts]) => (
						<div className="user"key={userUUID}>
							<div className="user-name">
								<p title={userUUID}>{usersStorefronts[0].owner.name}</p>
							</div>
							<div className={classNames(
								"storefront-container",
								{ "storefront-container-full": username !== null }
							)}>
								<Storefront
									usersStorefronts={usersStorefronts}
									setCurrentItem={actions.setCurrentItem}
								/>
							</div>
						</div>
					))}
				</>
			)}
			{state.currentItem && (
				<ItemViewer
					currentItem={state.currentItem}
					clearCurrentItem={actions.clearCurrentItem}
				/>
			)}
		</div>
	);
}

export default App;
