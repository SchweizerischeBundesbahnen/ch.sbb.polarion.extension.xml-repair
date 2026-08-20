import React from 'react';
import ReactDOM from 'react-dom/client';
import { configureGenericModules } from '@sbb-polarion/react-sbb-polarion';
import '@sbb-polarion/react-sbb-polarion/style.css';
import App from './App';
import './App.css';
// The generic data-table look (.sbb-table); RSP defines the --sbb-table-* tokens it consumes but not
// these layout rules, so the app bundles the vendored copy itself. After style.css so tokens exist.
import './generic/tables.css';
import { GENERIC_MODULES_BASE } from './services/genericModules';

// BreadcrumbTopic loads the generic BreadcrumbBridge.js from this same base at runtime. (The dropdown
// factories are bundled and no longer need this.)
configureGenericModules(GENERIC_MODULES_BASE);

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
