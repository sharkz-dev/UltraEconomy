export function Card (number) {
  return `
    <div class="card">
      <h3>Stat ${number}</h3>
      <p id="${number}">Loading...</p>
    </div>
  `
}
