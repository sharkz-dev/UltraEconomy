import { DashboardPage } from './pages/dashboard.js'
import { PlayersPage } from './pages/players.js'

const routes = {
  '/': DashboardPage,
  '/players': PlayersPage
}

export function renderRoute () {
  const path = window.location.pathname
  const page = routes[path] || DashboardPage

  document.getElementById('app').innerHTML = page()
}
