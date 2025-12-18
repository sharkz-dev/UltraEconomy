import { Navbar } from '../components/navbar.js'
import { apiGet } from '../api.js'

export function DashboardPage () {
  loadStats()

  return `
    ${Navbar()}
    <div class="container">
    </div>
  `
}
async function loadStats() {
  try {
    const data = await apiGet("/api/stats");
    document.getElementById("balance").textContent = data.balance;
    document.getElementById("online").textContent = data.online;
  } catch {
    document.getElementById("balance").textContent = "Error";
    document.getElementById("online").textContent = "Error";
  }
}