const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

export async function validateQuery(payload) {
  let response

  try {
    response = await fetch(`${API_BASE_URL}/api/validate`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(payload)
    })
  } catch (error) {
    throw new Error(
      `No se pudo conectar con el backend en ${API_BASE_URL}. Verifica que Spring Boot este corriendo.`,
      { cause: error }
    )
  }

  const data = await response.json()

  if (!response.ok) {
    const message = data?.message || 'No se pudo validar la query.'
    throw new Error(message, { cause: data })
  }

  return data
}
