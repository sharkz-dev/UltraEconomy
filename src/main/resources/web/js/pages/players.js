import { Navbar } from '../components/navbar.js'
import { apiGet } from '../api.js'
import { Card as playerCard } from '../components/playerCard.js'

let currentPage = 1
const pageSize = 50 // siempre mostrar 50 jugadores
let playersDataOriginal = [] // lista completa
let playersDataFiltered = [] // lista filtrada por búsqueda

export function PlayersPage () {
  loadPlayers()

  return `
    ${Navbar()}
    <div class="players_container container">

      <!-- Botones arriba -->
      <div id="buttonContainerTop" class="flex justify-center mt-4 mb-4">
        <button id="prevPageBtnTop" class="mx-2 px-4 py-2 bg-blue-600 text-white rounded-lg shadow hover:bg-blue-700 transition-colors duration-300" onclick="prevPage()">Previous</button>
        <button id="nextPageBtnTop" class="mx-2 px-4 py-2 bg-blue-600 text-white rounded-lg shadow hover:bg-blue-700 transition-colors duration-300" onclick="nextPage()">Next</button>
      </div>

      <!-- Input de búsqueda -->
      <div class="player_search mb-4 flex items-center gap-2 rounded-lg bg-gray-800 p-3 shadow-md shadow-black/20">
        <span class="material-icons text-gray-400">search</span>
        <input 
          type="text" 
          id="searchInput" 
          placeholder="Search players..." 
          onkeyup="filterPlayers()" 
          class="w-full bg-gray-900 text-white rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
      </div>

      <!-- Contenedor de jugadores -->
      <div id="players" class="grid gap-4">
        <div>Loading...</div>
      </div>

      <!-- Botones abajo -->
      <div id="buttonContainerBottom" class="flex justify-center mt-4 mb-4">
        <button id="prevPageBtnBottom" class="mx-2 px-4 py-2 bg-blue-600 text-white rounded-lg shadow hover:bg-blue-700 transition-colors duration-300" onclick="prevPage()">Previous</button>
        <button id="nextPageBtnBottom" class="mx-2 px-4 py-2 bg-blue-600 text-white rounded-lg shadow hover:bg-blue-700 transition-colors duration-300" onclick="nextPage()">Next</button>
      </div>
    </div>
  `
}

async function loadPlayers () {
  const data = await apiGet('/api/players')
  console.log(data)

  if (!Array.isArray(data)) {
    document.getElementById('players').innerHTML = '<div>Invalid data</div>'
    return
  }

  playersDataOriginal = data
  playersDataFiltered = [...playersDataOriginal]
  renderPlayers()
  updateButtons()
}

function renderPlayers () {
  const start = (currentPage - 1) * pageSize
  const end = start + pageSize
  const pagePlayers = playersDataFiltered.slice(start, end)

  if (pagePlayers.length === 0) {
    document.getElementById('players').innerHTML =
      '<div>No players found</div>'
    return
  }

  document.getElementById('players').innerHTML = pagePlayers
    .map(player => playerCard(player))
    .join('')
}

function updateButtons () {
  const totalPages = Math.ceil(playersDataFiltered.length / pageSize)

  const showPrev = currentPage > 1
  const showNext = currentPage < totalPages

  document.getElementById('prevPageBtnTop').style.display = showPrev
    ? 'inline-block'
    : 'none'
  document.getElementById('prevPageBtnBottom').style.display = showPrev
    ? 'inline-block'
    : 'none'
  document.getElementById('nextPageBtnTop').style.display = showNext
    ? 'inline-block'
    : 'none'
  document.getElementById('nextPageBtnBottom').style.display = showNext
    ? 'inline-block'
    : 'none'
}

// Funciones globales para onclick
window.prevPage = () => {
  if (currentPage > 1) {
    currentPage--
    renderPlayers()
    updateButtons()
  }
}

window.nextPage = () => {
  const totalPages = Math.ceil(playersDataFiltered.length / pageSize)
  if (currentPage < totalPages) {
    currentPage++
    renderPlayers()
    updateButtons()
  }
}

// Filtrado de búsqueda
window.filterPlayers = () => {
  const search = document.getElementById('searchInput').value.toLowerCase()
  currentPage = 1
  playersDataFiltered = playersDataOriginal.filter(p =>
    p.name.toLowerCase().includes(search)
  )
  renderPlayers()
  updateButtons()
}
