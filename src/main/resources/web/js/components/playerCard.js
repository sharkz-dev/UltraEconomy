export function Card (playerData) {
  return `
    <a href="/player/${playerData.playerUUID}"
      class="
        player-card
        flex items-center
        bg-gray-800 hover:bg-gray-700
        text-gray-100
        rounded-xl p-4
        transition-transform transform hover:-translate-y-1
        gap-4 w-full
        focus:outline-none focus:ring-2 focus:ring-blue-500
        shadow-[0_4px_15px_rgba(59,130,246,0.5)]
        hover:shadow-[0_6px_20px_rgba(59,130,246,0.7)]
      "
    >
      <img
        src="https://minotar.net/helm/${playerData.playerName}/600.png"
        class="w-20 h-20 rounded-lg flex-shrink-0"
      />
      <h3 class="text-lg font-semibold truncate">${playerData.playerName}</h3>
    </a>
  `
}
