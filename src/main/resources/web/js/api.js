export async function apiGet (path) {
  const res = await fetch(`${debugUrl()}${path}`)
  if (!res.ok) throw new Error('API error')
  return res.json()
}

function debugUrl () {
  return true ? 'http://127.0.0.1:8080' : ''
}
