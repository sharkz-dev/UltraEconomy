import { Navbar } from '../components/navbar.js'
import { apiGet } from '../api.js'
import { Card as playerCard } from '../components/playerCard.js'

let currentPage = 1
const PAGE_SIZE = 50
let playersData = []

export function PlayersPage () {
  // Do not call loadPlayers() immediately, call it after DOM is inserted
  setTimeout(() => loadPlayers(currentPage), 0)

  return `
    ${Navbar()}

    <div class="players_container container">

      <!-- Top Buttons -->
      <div class="flex justify-center mt-4 mb-4 gap-2">
        <button id="prevTop" onclick="prevPage()" class="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700">
          Previous
        </button>
        <button id="nextTop" onclick="nextPage()" class="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700">
          Next
        </button>
      </div>

      <!-- Search -->
      <div class="player_search mb-4 flex items-center gap-2 rounded-lg bg-gray-800 p-3">
        <span class="material-icons text-gray-400">search</span>
        <input
          id="searchInput"
          type="text"
          placeholder="Search player and press Enter..."
          onkeydown="handleSearch(event)"
          class="w-full bg-gray-900 text-white rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
      </div>

      <!-- Players container -->
      <div id="players" class="grid gap-4">
        <div>Loading...</div>
      </div>

      <!-- Bottom Buttons -->
      <div class="flex justify-center mt-4 mb-4 gap-2">
        <button id="prevBottom" onclick="prevPage()" class="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700">
          Previous
        </button>
        <button id="nextBottom" onclick="nextPage()" class="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700">
          Next
        </button>
      </div>

    </div>
  `
}

/* ===========================
   Load Players from API
=========================== */
async function loadPlayers (page) {
  const container = document.getElementById('players')
  if (!container) return

  container.innerHTML = '<div>Loading...</div>'

  try {
    const data = await apiGet(`http://localhost:8080/api/players?page=${page}`)

    if (!Array.isArray(data)) {
      container.innerHTML = '<div>Invalid data</div>'
      return
    }

    playersData = data
    renderPlayers()
    updateButtons()
  } catch (err) {
    container.innerHTML = '<div>Error loading players</div>'
    console.error(err)
  }
}

/* ===========================
   Render Players
=========================== */
function renderPlayers () {
  const container = document.getElementById('players')
  if (!container) return

  if (!playersData.length) {
    container.innerHTML = '<div>No players found</div>'
    return
  }

  container.innerHTML = playersData.map(player => playerCard(player)).join('')
}

/* ===========================
   Pagination buttons
=========================== */
function updateButtons () {
  const hasPrev = currentPage > 1
  const hasNext = playersData.length === PAGE_SIZE

  toggleButton('prevTop', hasPrev)
  toggleButton('prevBottom', hasPrev)
  toggleButton('nextTop', hasNext)
  toggleButton('nextBottom', hasNext)
}

function toggleButton (id, show) {
  const btn = document.getElementById(id)
  if (!btn) return
  btn.style.display = show ? 'inline-block' : 'none'
}

/* ===========================
   Pagination actions
=========================== */
window.nextPage = () => {
  if (playersData.length < PAGE_SIZE) return
  currentPage++
  loadPlayers(currentPage)
}

window.prevPage = () => {
  if (currentPage <= 1) return
  currentPage--
  loadPlayers(currentPage)
}

/* ===========================
   Search player
=========================== */
window.handleSearch = async event => {
  if (event.key !== 'Enter') return

  const name = event.target.value.trim()
  if (!name) return

  try {
    // Esperamos la respuesta de la API
    const player = await apiGet(`/api/player/${encodeURIComponent(name)}`)

    if (!player || !player.playerUUID) {
      alert('Player not found')
      return
    }

    // Navegar a la página del jugador usando UUID
    window.location.href = `/player/${player.playerUUID}`
  } catch (err) {
    console.error(err)
    alert('Error fetching player')
  }
}
