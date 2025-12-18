import { renderRoute } from './router.js'

function navigateTo (url) {
  history.pushState(null, null, url)
  renderRoute()
}

document.addEventListener('click', e => {
  if (e.target.matches('[data-link]')) {
    e.preventDefault()
    navigateTo(e.target.href)
  }
})

window.addEventListener('popstate', renderRoute)

// Render inicial
renderRoute()
