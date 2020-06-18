import { createStore, createHook } from 'react-sweet-state';

const Store = createStore({
	initialState: {
		players: [],
		currentItem: null,
	},
	actions: {
		loadData: () => async ({ setState, getState }) => {
			if (getState().loading === true) return;
			setState({
				loading: true,
				currentItem: null,
			});

			const storefrontResponse = await fetch('./sf/');
			const storefrontJson = await storefrontResponse.json();
			const players = {};
			storefrontJson.map(async (sf) => {
				sf.id = btoa(Math.random()).substring(0, 12);
				sf.contents.filter((item) => {
					if (item !== null) {
						item.image = item.key.slice(10, item.key.length);
					}
				});

				if (players[sf.owner.uuid]) {
					players[sf.owner.uuid].push(sf)
				} else {
					players[sf.owner.uuid] = [sf];
				}
			})

			setState({
				loading: false,
				players,
			});
		},
		setCurrentItem: (item) => ({ setState }) => {
			setState({ currentItem: item });
		},
		clearCurrentItem: () => ({ setState }) => {
			setState({ currentItem: null,});
		},
	},
	name: 'storefront',
});

export const useStorefront = createHook(Store);
