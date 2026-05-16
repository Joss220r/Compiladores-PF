const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

export async function validateQuery(payload) {
  const response = await fetch(`${API_BASE_URL}/api/validate`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  })

  const data = await response.json()

  if (!response.ok) {
    const message = data?.message || 'No se pudo validar la query.'
    throw new Error(message, { cause: data })
  }

  return data
}
