import { Navbar } from '../components/navbar.js'
import { apiGet } from '../api.js'

const PAGE_SIZE = 50
let currentPage = 1
let allTransactions = []
let selectedCurrency = null
let selectedDays = 7
let chartInstance = null

const moneyFormatter = new Intl.NumberFormat('en-US', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
})

const dateFormatter = new Intl.DateTimeFormat('en-US', {
  year: '2-digit',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit'
})

export async function PlayerPage () {
  return `
    ${Navbar()}
    <div class="mx-auto px-6 py-8 max-w-7xl">
      <div id="playerContent" class="text-white">
        <span class="animate-pulse text-blue-400 font-semibold text-lg">
          Loading player...
        </span>
      </div>
    </div>
  `
}

PlayerPage.afterRender = async function ({ uuid }) {
  const container = document.getElementById('playerContent')
  if (!container) return

  try {
    const player = await apiGet(`/api/player/${uuid}`)
    allTransactions = await apiGet(`/api/transactions/player/${uuid}`)

    if (!player) {
      container.innerHTML = `<p class="text-red-500 text-center text-lg">Player not found</p>`
      return
    }

    // BALANCES
    const balanceRows = Object.entries(player.balances || {})
      .map(
        ([currency, amount]) => `
          <tr class="border-b border-gray-700 hover:bg-gray-700">
            <td class="px-4 py-2 font-medium text-blue-200">${currency}</td>
            <td class="px-4 py-2 text-right text-gray-200">${moneyFormatter.format(
              amount
            )}</td>
          </tr>
        `
      )
      .join('')

    container.innerHTML = `
      <!-- DASHBOARD TOP -->
      <div class="flex flex-col lg:flex-row gap-8 mb-10">

        <!-- LEFT: PLAYER INFO -->
        <div class="flex-1 bg-gray-800 p-8 rounded-2xl shadow-lg shadow-blue-500/30 flex flex-col items-center space-y-6">
          <img src="https://minotar.net/helm/${player.playerName}/200.png"
            class="rounded-lg border-2 border-blue-400 w-36 h-36"/>
          <h2 class="text-3xl font-bold text-blue-300">${player.playerName}</h2>
          <table class="w-full text-lg mt-4">
            <thead>
              <tr class="border-b border-gray-600 text-blue-300">
                <th class="px-4 py-2 text-left">Currency</th>
                <th class="px-4 py-2 text-right">Amount</th>
              </tr>
            </thead>
            <tbody>
              ${balanceRows}
            </tbody>
          </table>
        </div>

        <!-- RIGHT: MONEY FLOW -->
        <div class="flex-1 bg-gray-800 p-8 rounded-2xl shadow-lg shadow-blue-500/30 flex flex-col">
          <h3 class="text-2xl font-semibold text-blue-300 mb-4 text-center">
            Money Flow per Hour
          </h3>
          <div class="flex flex-wrap gap-4 justify-center mb-4">
            <select id="currencySelect" class="bg-gray-700 text-white px-5 py-2 rounded text-lg font-medium"></select>
            <select id="daysSelect" class="bg-gray-700 text-white px-5 py-2 rounded text-lg font-medium">
              <option value="1">Today</option>
              <option value="3">Last 3 days</option>
              <option value="7" selected>Last 7 days</option>
              <option value="30">Last 30 days</option>
              <option value="90">Last 90 days</option>
            </select>
          </div>
          <canvas id="moneyChart" class="flex-1 min-h-[350px]"></canvas>
        </div>

      </div>

      <!-- TRANSACTIONS -->
      <div class="bg-gray-800 p-6 rounded-2xl shadow-lg shadow-blue-500/30">
        <h3 class="text-2xl font-semibold text-blue-300 mb-4 text-center">
          Transactions
        </h3>
        <div class="overflow-x-auto max-h-[500px]">
          <table class="w-full text-lg text-gray-300">
            <thead class="border-b border-gray-600 text-blue-300 sticky top-0 bg-gray-800">
              <tr>
                <th class="px-4 py-2 text-left">Date</th>
                <th class="px-4 py-2 text-center">Type</th>
                <th class="px-4 py-2 text-left">Currency</th>
                <th class="px-4 py-2 text-right">Amount</th>
              </tr>
            </thead>
            <tbody id="transactionsTable"></tbody>
          </table>
        </div>
        <div id="pagination" class="flex justify-center items-center gap-6 mt-6 text-lg"></div>
      </div>
    `

    initCurrencySelector()
    initDaysSelector()
    renderTransactionsTable()
    renderMoneyChart()
  } catch (err) {
    console.error(err)
    container.innerHTML = `<p class="text-red-500 text-center text-lg">Error loading player data</p>`
  }
}

// Selector and rendering functions same as before
function initCurrencySelector () {
  const select = document.getElementById('currencySelect')
  const currencies = [...new Set(allTransactions.map(t => t.currency))]
  selectedCurrency = currencies[0] || ''
  select.innerHTML = currencies
    .map(c => `<option value="${c}">${c}</option>`)
    .join('')
  select.value = selectedCurrency
  select.onchange = () => {
    selectedCurrency = select.value
    currentPage = 1
    renderTransactionsTable()
    renderMoneyChart()
  }
}

function initDaysSelector () {
  const select = document.getElementById('daysSelect')
  select.value = selectedDays
  select.onchange = () => {
    selectedDays = parseInt(select.value)
    currentPage = 1
    renderTransactionsTable()
    renderMoneyChart()
  }
}

function getFilteredTransactions () {
  const now = new Date()
  const startDate = new Date(now)
  startDate.setDate(now.getDate() - selectedDays + 1)
  startDate.setHours(0, 0, 0, 0)
  return allTransactions.filter(
    tx =>
      tx.currency === selectedCurrency && new Date(tx.timestamp) >= startDate
  )
}

function renderTransactionsTable () {
  const table = document.getElementById('transactionsTable')
  const pagination = document.getElementById('pagination')
  const filtered = getFilteredTransactions()
  const start = (currentPage - 1) * PAGE_SIZE
  const end = start + PAGE_SIZE
  const pageData = filtered.slice(start, end)

  table.innerHTML = pageData
    .map(
      tx => `
    <tr class="border-b border-gray-700 hover:bg-gray-700">
      <td class="px-4 py-2">${dateFormatter.format(new Date(tx.timestamp))}</td>
      <td class="px-4 py-2 text-center ${tx.type === 'DEPOSIT'
        ? 'text-green-400'
        : tx.type === 'WITHDRAW'
          ? 'text-red-400'
          : 'text-yellow-400'}">${tx.type}</td>
      <td class="px-4 py-2">${tx.currency}</td>
      <td class="px-4 py-2 text-right">${tx.type === 'WITHDRAW'
        ? '-'
        : '+'}${moneyFormatter.format(tx.amount)}</td>
    </tr>
  `
    )
    .join('')

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  pagination.innerHTML = `
    <button class="px-4 py-2 bg-gray-700 rounded disabled:opacity-50" ${currentPage ===
    1
      ? 'disabled'
      : ''} onclick="changePage(${currentPage - 1})">←</button>
    <span>${currentPage} / ${totalPages}</span>
    <button class="px-4 py-2 bg-gray-700 rounded disabled:opacity-50" ${currentPage ===
    totalPages
      ? 'disabled'
      : ''} onclick="changePage(${currentPage + 1})">→</button>
  `
}

window.changePage = page => {
  currentPage = page
  renderTransactionsTable()
}

function renderMoneyChart () {
  const ctx = document.getElementById('moneyChart')
  if (!ctx) return
  if (chartInstance) chartInstance.destroy()

  const map = {}
  const transactions = getFilteredTransactions()
  const now = new Date()
  const startDate = new Date(now)
  startDate.setDate(now.getDate() - selectedDays + 1)
  startDate.setHours(0, 0, 0, 0)

  for (const tx of transactions) {
    if (!tx.processed || tx.type === 'SET') continue
    const d = new Date(tx.timestamp)
    if (d < startDate) continue
    const key = d.toISOString().slice(0, 13) + ':00'
    if (!map[key]) map[key] = 0
    if (tx.type === 'DEPOSIT') map[key] += tx.amount
    if (tx.type === 'WITHDRAW') map[key] -= tx.amount
  }

  const entries = Object.entries(map).sort(
    ([a], [b]) => new Date(a) - new Date(b)
  )
  const labels = entries.map(([k]) =>
    new Date(k).toLocaleString('en-US', {
      year: '2-digit',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    })
  )
  const values = entries.map(([, v]) => v)

  chartInstance = new Chart(ctx, {
    type: 'line',
    data: {
      labels,
      datasets: [
        {
          label: `Money Flow (${selectedCurrency})`,
          data: values,
          borderColor: '#60a5fa',
          backgroundColor: 'rgba(96,165,250,0.2)',
          tension: 0.3,
          fill: true
        }
      ]
    },
    options: {
      responsive: true,
      plugins: {
        legend: { labels: { color: '#e5e7eb', font: { size: 16 } } },
        tooltip: { mode: 'index', intersect: false }
      },
      scales: {
        x: {
          ticks: { color: '#9ca3af', font: { size: 14 } },
          grid: { color: 'rgba(255,255,255,0.05)' }
        },
        y: {
          ticks: {
            color: '#9ca3af',
            font: { size: 14 },
            callback: v => moneyFormatter.format(v)
          },
          grid: { color: 'rgba(255,255,255,0.05)' }
        }
      }
    }
  })
}
