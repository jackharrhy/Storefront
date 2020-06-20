import 'regenerator-runtime/runtime';

import React from 'react';
import ReactDOM from 'react-dom';
import { BrowserRouter as Router } from "react-router-dom";

import './index.css';
import 'normalize.css';

import App from './App';

ReactDOM.render(<Router>
	<App />
</Router>, document.getElementById('root'));
