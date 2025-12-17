export function Navbar () {
  return `
    <header class="bg-gradient-to-r from-blue-900 via-blue-800 to-blue-900 text-white shadow-lg shadow-black/30">
      <div class="container mx-auto flex items-center justify-between p-4">

        <!-- Título a la izquierda -->
        <h1 class="text-2xl font-bold text-blue-100 select-none">UltraEconomy</h1>

        <!-- Links centrados -->
        <nav class="flex-1 flex justify-center space-x-10">
          <a href="/" data-link class="relative text-blue-200 hover:text-white font-medium transition-colors duration-300">
            Dashboard
            <span class="absolute left-0 -bottom-1 w-0 h-0.5 bg-white transition-all duration-300"></span>
          </a>
          <a href="/players" data-link class="relative text-blue-200 hover:text-white font-medium transition-colors duration-300">
            Players
            <span class="absolute left-0 -bottom-1 w-0 h-0.5 bg-white transition-all duration-300"></span>
          </a>
        </nav>

        <!-- Logo GitHub a la derecha -->
        <a href="https://github.com/zonary123" target="_blank" class="text-blue-200 hover:text-white transition-transform duration-300 transform hover:scale-110">
          <i class="fab fa-github text-2xl"></i>
        </a>
      </div>
    </header>
  `
}
