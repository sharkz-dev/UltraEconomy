import { Navbar } from '../components/navbar.js'
import { apiGet } from '../api.js'

/* ============================
   CONFIG & STATE
============================ */

const PAGE_SIZE = 50
let currentPage = 1
let allTransactions = []
let selectedCurrency = null
let chartInstance = null

const moneyFormatter = new Intl.NumberFormat('en-US', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
})

const dateFormatter = new Intl.DateTimeFormat('es-ES', {
  dateStyle: 'short',
  timeStyle: 'medium'
})

/* ============================
   PAGE TEMPLATE
============================ */

export async function PlayerPage () {
  return `
    ${Navbar()}
    <div class="mx-auto p-6 max-w-6xl">
      <div id="playerContent" class="text-white">
        <span class="animate-pulse text-blue-400 font-semibold">
          Loading player...
        </span>
      </div>
    </div>
  `
}

/* ============================
   AFTER RENDER
============================ */

PlayerPage.afterRender = async function ({ uuid }) {
  const container = document.getElementById('playerContent')
  if (!container) return

  try {
    const player = await apiGet(`/api/player/${uuid}`)
    allTransactions = await apiGet(`/api/transactions/player/${uuid}`)

    if (!player) {
      container.innerHTML = `<p class="text-red-500 text-center">Player not found</p>`
      return
    }

    const balanceCurrencies = Object.keys(player.balances || {})

    /* ---------- BALANCES ---------- */
    const balanceRows = Object.entries(player.balances || {})
      .map(
        ([currency, amount]) => `
          <tr class="border-b border-gray-700">
            <td class="px-4 py-2 font-medium text-blue-200">${currency}</td>
            <td class="px-4 py-2 text-right text-gray-200">
              ${moneyFormatter.format(amount)}
            </td>
          </tr>
        `
      )
      .join('')

    container.innerHTML = `
      <div class="flex flex-col gap-8">

        <!-- PLAYER CARD -->
        <div class="bg-gray-800 p-6 rounded-xl shadow-lg shadow-blue-500/30">
          <img src="https://minotar.net/helm/${player.playerName}/200.png"
            class="mx-auto mb-4 rounded-lg"/>

          <h2 class="text-2xl font-bold text-center text-blue-300 mb-6">
            ${player.playerName}
          </h2>

          <table class="w-full">
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

        <!-- GRAPH -->
        <div class="bg-gray-800 p-6 rounded-xl shadow-lg">
          <div class="flex flex-col md:flex-row md:items-center md:justify-between mb-4 gap-4">
            <h3 class="text-xl font-semibold text-blue-300">
              Money flow per hour
            </h3>

            <select id="currencySelect"
              class="bg-gray-700 text-white px-4 py-2 rounded">
            </select>
          </div>

          <canvas id="moneyChart" height="120"></canvas>
        </div>

        <!-- TRANSACTIONS -->
        <div class="bg-gray-800 p-6 rounded-xl shadow-lg">
          <h3 class="text-xl font-semibold text-blue-300 mb-4">
            Transactions
          </h3>

          <div class="overflow-x-auto">
            <table class="w-full text-sm text-gray-300">
              <thead class="border-b border-gray-600 text-blue-300">
                <tr>
                  <th class="px-3 py-2 text-left">Date</th>
                  <th class="px-3 py-2 text-center">Type</th>
                  <th class="px-3 py-2 text-left">Currency</th>
                  <th class="px-3 py-2 text-right">Amount</th>
                </tr>
              </thead>
              <tbody id="transactionsTable"></tbody>
            </table>
          </div>

          <div id="pagination"
            class="flex justify-center items-center gap-4 mt-4">
          </div>
        </div>

      </div>
    `

    initCurrencySelector(balanceCurrencies)
    renderTransactionsTable()
    renderMoneyChart()
  } catch (err) {
    console.error(err)
    container.innerHTML = `
      <p class="text-red-500 text-center">
        Error loading player data
      </p>
    `
  }
}

/* ============================
   CURRENCY SELECTOR
============================ */

function initCurrencySelector (balanceCurrencies = []) {
  const select = document.getElementById('currencySelect')
  if (!select) return

  const transactionCurrencies = [
    ...new Set(allTransactions.map(t => t.currency))
  ]

  const currencies = [
    ...new Set([...balanceCurrencies, ...transactionCurrencies])
  ]

  if (currencies.length === 0) return

  selectedCurrency = balanceCurrencies[0] || currencies[0]

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

/* ============================
   FILTER
============================ */

function getFilteredTransactions () {
  return allTransactions.filter(tx => tx.currency === selectedCurrency)
}

/* ============================
   TRANSACTIONS TABLE
============================ */

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
    <tr class="border-b border-gray-700">
      <td class="px-3 py-2">
        ${dateFormatter.format(new Date(tx.timestamp))}
      </td>
      <td class="px-3 py-2 text-center ${tx.type === 'DEPOSIT'
        ? 'text-green-400'
        : tx.type === 'WITHDRAW' ? 'text-red-400' : 'text-yellow-400'}">
        ${tx.type}
      </td>
      <td class="px-3 py-2">${tx.currency}</td>
      <td class="px-3 py-2 text-right">
        ${tx.type === 'WITHDRAW' ? '-' : '+'}
        ${moneyFormatter.format(tx.amount)}
      </td>
    </tr>
  `
    )
    .join('')

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))

  pagination.innerHTML = `
    <button
      class="px-3 py-1 bg-gray-700 rounded disabled:opacity-50"
      ${currentPage === 1 ? 'disabled' : ''}
      onclick="changePage(${currentPage - 1})">
      ←
    </button>

    <span>${currentPage} / ${totalPages}</span>

    <button
      class="px-3 py-1 bg-gray-700 rounded disabled:opacity-50"
      ${currentPage === totalPages ? 'disabled' : ''}
      onclick="changePage(${currentPage + 1})">
      →
    </button>
  `
}

window.changePage = page => {
  currentPage = page
  renderTransactionsTable()
}

/* ============================
   GRAPH
============================ */

function renderMoneyChart () {
  const ctx = document.getElementById('moneyChart')
  if (!ctx) return

  if (chartInstance) chartInstance.destroy()

  const map = {}
  const transactions = getFilteredTransactions()

  for (const tx of transactions) {
    if (!tx.processed || tx.type === 'SET') continue

    const d = new Date(tx.timestamp)
    d.setMinutes(0, 0, 0)
    const key = d.toISOString()

    if (!map[key]) map[key] = 0

    if (tx.type === 'DEPOSIT') map[key] += tx.amount
    if (tx.type === 'WITHDRAW') map[key] -= tx.amount
  }

  const entries = Object.entries(map).sort(
    ([a], [b]) => new Date(a) - new Date(b)
  )

  chartInstance = new Chart(ctx, {
    type: 'line',
    data: {
      labels: entries.map(([k]) =>
        new Date(k).toLocaleTimeString('es-ES', {
          hour: '2-digit',
          minute: '2-digit'
        })
      ),
      datasets: [
        {
          label: `Money flow (${selectedCurrency})`,
          data: entries.map(([, v]) => v),
          borderColor: '#60a5fa',
          backgroundColor: 'rgba(96,165,250,0.2)',
          fill: true,
          tension: 0.3
        }
      ]
    },
    options: {
      responsive: true,
      plugins: {
        legend: {
          labels: { color: '#e5e7eb' }
        }
      },
      scales: {
        x: { ticks: { color: '#9ca3af' } },
        y: {
          ticks: {
            color: '#9ca3af',
            callback: v => moneyFormatter.format(v)
          }
        }
      }
    }
  })
}
