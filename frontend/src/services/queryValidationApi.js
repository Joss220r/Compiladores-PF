const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

export async function login(payload) {
  return postJson('/api/auth/login', payload, 'No se pudo iniciar sesion.')
}

export async function validateQuery(payload) {
  return postJson('/api/validate', payload, `No se pudo conectar con el backend en ${API_BASE_URL}. Verifica que Spring Boot este corriendo.`)
}

async function postJson(path, payload, fallbackMessage) {
  let response

  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(payload)
    })
  } catch (error) {
    throw new Error(fallbackMessage, { cause: error })
  }

  let data = null
  try {
    data = await response.json()
  } catch (error) {
    data = null
  }

  if (!response.ok) {
    const message = data?.message || fallbackMessage || 'Ocurrio un error al comunicarse con el backend.'
    throw new Error(message, { cause: data || { errors: [{ phase: 'SYSTEM', severity: 'ERROR', message }] } })
  }

  return data
}

export async function fetchHistoryStats() {
  return fetchJson('/api/history/stats')
}

export async function fetchHistory(limit = 20) {
  return fetchJson(`/api/history?limit=${limit}`)
}

async function fetchJson(path) {
  let response

  try {
    response = await fetch(`${API_BASE_URL}${path}`)
  } catch (error) {
    throw new Error('No se pudo cargar el historial.', { cause: error })
  }

  let data = null
  try {
    data = await response.json()
  } catch (error) {
    data = null
  }

  if (!response.ok) {
    throw new Error(data?.message || 'No se pudo cargar el historial.', { cause: data })
  }

  return data
}
