import React from 'react'

import Item from './Item';

export default ({ usersStorefronts, setCurrentItem }) => {
	return usersStorefronts.map(((sf) => (
		<div className="storefront" key={sf.id}>
			<div className="sign">
				{
					sf.description.slice(1).map((line, i) => (
						<p key={i}>{line}</p>
					))
				}
			</div>
			<div className="items">
				{
					sf.contents.map((item, i) => (
						<Item
							item={item}
							key={i}
							setCurrentItem={setCurrentItem}
						/>
					))
				}
			</div>
		</div>
	)));
}
