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

  let data = null
  try {
    data = await response.json()
  } catch (error) {
    data = null
  }

  if (!response.ok) {
    const message = data?.message || 'Ocurrio un error al comunicarse con el backend.'
    throw new Error(message, { cause: data || { errors: [{ phase: 'SYSTEM', severity: 'ERROR', message }] } })
  }

  return data
}
