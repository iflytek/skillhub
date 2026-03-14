import ReactDOM from 'react-dom/client'
import { App } from './app/providers'
import './i18n/config'
import './index.css'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <App />,
)
