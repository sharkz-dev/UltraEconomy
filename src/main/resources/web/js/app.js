import { renderRoute } from './router.js'

window.addEventListener('popstate', renderRoute)

document.addEventListener('click', e => {
  if (e.target.matches('[data-link]')) {
    e.preventDefault()
    history.pushState(null, '', e.target.href)
    renderRoute()
  }
})

renderRoute()
