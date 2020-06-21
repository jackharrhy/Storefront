import { createStore, createHook } from 'react-sweet-state';

const Store = createStore({
	initialState: {
		players: [],
		currentItem: null,
	},
	actions: {
		loadData: ({username} = {}) => async ({ setState, getState }) => {
			if (getState().loading === true) return;
			setState({
				loading: true,
				currentItem: null,
			});

			const filterOnUsername = username === null || username === undefined;

			const storefrontResponse = await fetch('./api/storefronts/');
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
					if (filterOnUsername || sf.owner.name === username) {
						players[sf.owner.uuid].push(sf)
					}
				} else {
					if (filterOnUsername || sf.owner.name === username) {
						players[sf.owner.uuid] = [sf];
					}
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
