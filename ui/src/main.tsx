import React from 'react';
import ReactDOM from 'react-dom/client';
import '@grigoriev/react-sbb-polarion/style.css';
import App from './App';
import './App.css';
// The generic data-table look (.sbb-table); RSP defines the --sbb-table-* tokens it consumes but not
// these layout rules, so the app bundles the vendored copy itself. After style.css so tokens exist.
import './generic/tables.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
