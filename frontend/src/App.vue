<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { fetchHistory, fetchHistoryStats, login, validateQuery } from './services/queryValidationApi'

const THEME_STORAGE_KEY = 'sql-platform-theme'
const AUTH_STORAGE_KEY = 'sql-platform-user'

const engines = [
  { label: 'NoSQL', value: 'NOSQL' },
  { label: 'MongoDB', value: 'MONGODB' },
  { label: 'SQL Server', value: 'SQL_SERVER' },
  { label: 'MySQL', value: 'MYSQL' },
  { label: 'PostgreSQL', value: 'POSTGRESQL' },
  { label: 'Redis', value: 'REDIS' }
]

const selectedEngine = ref('MYSQL')
const query = ref('')
const result = ref(null)
const errorMessage = ref('')
const loading = ref(false)
const theme = ref(getStoredTheme())
const queryInput = ref(null)
const activeView = ref('validator')
const dashboardLoading = ref(false)
const dashboardError = ref('')
const dashboardStats = ref(null)
const dashboardItems = ref([])
const authUser = ref(getStoredUser())
const loginUsername = ref('')
const loginPassword = ref('')
const loginLoading = ref(false)
const loginError = ref('')

const resultClass = computed(() => {
  if (!result.value) {
    return ''
  }

  return result.value.success ? 'result-ok' : 'result-error'
})

const groupedErrors = computed(() => groupIssues(result.value?.errors || []))
const groupedWarnings = computed(() => groupIssues(result.value?.warnings || []))
const primarySuggestion = computed(() => result.value?.suggestions?.[0] || null)

watch(theme, (value) => {
  document.documentElement.dataset.theme = value
  saveStoredTheme(value)
}, { immediate: true })

function getStoredTheme() {
  try {
    const savedTheme = localStorage.getItem(THEME_STORAGE_KEY)
    return savedTheme === 'dark' || savedTheme === 'light' ? savedTheme : 'light'
  } catch {
    return 'light'
  }
}

function saveStoredTheme(value) {
  try {
    localStorage.setItem(THEME_STORAGE_KEY, value)
  } catch {
    // The theme still changes visually if storage is blocked by the browser.
  }
}

function getStoredUser() {
  try {
    const savedUser = localStorage.getItem(AUTH_STORAGE_KEY)
    return savedUser ? JSON.parse(savedUser) : null
  } catch {
    return null
  }
}

function saveStoredUser(user) {
  try {
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(user))
  } catch {
    // Login still works for the current session if storage is blocked.
  }
}

function groupIssues(issues) {
  return issues.reduce((groups, issue) => {
    const phase = issue.phase || 'SYSTEM'
    groups[phase] = groups[phase] || []
    groups[phase].push(issue)
    return groups
  }, {})
}

async function submitQuery() {
  loading.value = true
  errorMessage.value = ''
  result.value = null

  try {
    result.value = await validateQuery({
      engine: selectedEngine.value,
      query: query.value
    })
  } catch (error) {
    result.value = {
      success: false,
      message: error.message || 'Ocurrio un error al comunicarse con el backend.',
      errors: Array.isArray(error.cause?.errors)
        ? error.cause.errors
        : [{ phase: 'SYSTEM', severity: 'ERROR', message: error.message }],
      warnings: [],
      tokens: [],
      ast: null,
      semanticResult: null,
      output: null,
      suggestions: Array.isArray(error.cause?.suggestions) ? error.cause.suggestions : []
    }
  } finally {
    loading.value = false
  }
}

async function submitLogin() {
  loginLoading.value = true
  loginError.value = ''

  try {
    const response = await login({
      username: loginUsername.value,
      password: loginPassword.value
    })
    authUser.value = {
      username: response.username,
      displayName: response.displayName,
      role: response.role
    }
    saveStoredUser(authUser.value)
    loginPassword.value = ''
  } catch (error) {
    loginError.value = error.message || 'No se pudo iniciar sesion.'
  } finally {
    loginLoading.value = false
  }
}

function logout() {
  authUser.value = null
  activeView.value = 'validator'
  try {
    localStorage.removeItem(AUTH_STORAGE_KEY)
  } catch {
    // Ignore storage cleanup errors.
  }
}

function clearForm() {
  query.value = ''
  result.value = null
  errorMessage.value = ''
  nextTick(() => {
    queryInput.value?.focus()
  })
}

function toggleTheme() {
  theme.value = theme.value === 'light' ? 'dark' : 'light'
}

function applySuggestion(fixedQuery) {
  if (!fixedQuery) {
    return
  }
  query.value = fixedQuery
  nextTick(() => {
    queryInput.value?.focus()
  })
}

async function showDashboard() {
  activeView.value = 'dashboard'
  await loadDashboard()
}

function showValidator() {
  activeView.value = 'validator'
}

async function loadDashboard() {
  dashboardLoading.value = true
  dashboardError.value = ''

  try {
    const [stats, history] = await Promise.all([
      fetchHistoryStats(),
      fetchHistory(20)
    ])
    dashboardStats.value = stats
    dashboardItems.value = history.items || []
  } catch (error) {
    dashboardError.value = error.message || 'No se pudo cargar el historial.'
    dashboardStats.value = null
    dashboardItems.value = []
  } finally {
    dashboardLoading.value = false
  }
}

function formatDate(value) {
  if (!value) {
    return '-'
  }
  return new Date(value).toLocaleString()
}

function shortQuery(value) {
  if (!value) {
    return ''
  }
  return value.length > 90 ? `${value.slice(0, 90)}...` : value
}
</script>

<template>
  <main class="app-shell">
    <section v-if="!authUser" class="login-shell">
      <div class="login-panel">
        <div>
          <h1>SQL Platform Validator</h1>
          <p class="message">Ingresa para analizar querys y consultar el dashboard.</p>
        </div>

        <form class="query-form" @submit.prevent="submitLogin">
          <label class="field">
            <span>Usuario</span>
            <input v-model="loginUsername" type="text" autocomplete="username" placeholder="admin" />
          </label>

          <label class="field">
            <span>Contrasena</span>
            <input v-model="loginPassword" type="password" autocomplete="current-password" placeholder="admin123" />
          </label>

          <div v-if="loginError" class="error-box">
            {{ loginError }}
          </div>

          <button class="primary-button" type="submit" :disabled="loginLoading">
            {{ loginLoading ? 'Ingresando...' : 'Ingresar' }}
          </button>
        </form>
      </div>
    </section>

    <div v-if="authUser && loading" class="loader-overlay" role="status" aria-live="polite">
      <div class="loader-box">
        <img src="/loading.gif" alt="" />
        <span>Validando query...</span>
      </div>
    </div>

    <nav v-if="authUser" class="view-tabs" aria-label="Vistas">
      <button type="button" :class="{ active: activeView === 'validator' }" @click="showValidator">
        Validador
      </button>
      <button type="button" :class="{ active: activeView === 'dashboard' }" @click="showDashboard">
        Dashboard
      </button>
      <button type="button" class="logout-tab" @click="logout">
        Salir
      </button>
    </nav>

    <section v-if="authUser && activeView === 'validator'" class="workspace">
      <div class="editor-panel">
        <div class="title-row">
          <div>
            <h1>Validador de querys</h1>
          </div>
          <button class="theme-toggle" type="button" title="Cambiar tema" :aria-pressed="theme === 'dark'" @click="toggleTheme">
            <span class="toggle-track">
              <span class="toggle-thumb" />
            </span>
            <span>{{ theme === 'dark' ? 'Oscuro' : 'Claro' }}</span>
          </button>
        </div>

        <form class="query-form" @submit.prevent="submitQuery">
          <label class="field">
            <span>Motor</span>
            <select v-model="selectedEngine">
              <option v-for="engine in engines" :key="engine.value" :value="engine.value">
                {{ engine.label }}
              </option>
            </select>
          </label>

          <label class="field">
            <span>Query</span>
            <textarea
              v-model="query"
              ref="queryInput"
              rows="12"
              spellcheck="false"
              placeholder="Escribe aqui la query que quieres validar"
            />
          </label>

          <div class="button-row">
            <button class="primary-button" type="submit" :disabled="loading">
              {{ loading ? 'Analizando...' : 'Analizar query' }}
            </button>
            <button class="secondary-button" type="button" :disabled="loading" @click="clearForm">
              Limpiar
            </button>
          </div>
        </form>
      </div>

      <div class="result-panel">
        <div class="panel-header">
          <h2>Resultado</h2>
          <span v-if="result" class="result-badge" :class="resultClass">
            {{ result.success ? 'Valida' : 'Invalida' }}
          </span>
        </div>

        <div v-if="errorMessage" class="error-box">
          {{ errorMessage }}
        </div>

        <div v-else-if="result" class="result-content">
          <p class="message">
            {{ result.success ? 'Consulta valida.' : 'Se encontraron errores en la consulta.' }}
          </p>

          <section v-if="result.errors?.length" class="result-section issue-section">
            <h3>Errores</h3>
            <div v-for="(items, phase) in groupedErrors" :key="phase" class="issue-group">
              <h4>{{ phase }}</h4>
              <ul>
                <li v-for="(item, index) in items" :key="`${phase}-${index}`" class="issue-item">
                  <span class="issue-message">{{ item.message }}</span>
                  <span class="issue-badges">
                    <span>{{ item.severity || 'ERROR' }}</span>
                    <span>L {{ item.line || 1 }}</span>
                    <span>C {{ item.column || 1 }}</span>
                    <span v-if="item.fragment">{{ item.fragment }}</span>
                  </span>
                </li>
              </ul>
            </div>
          </section>

          <section v-if="result.warnings?.length" class="result-section issue-section">
            <h3>Warnings</h3>
            <div v-for="(items, phase) in groupedWarnings" :key="phase" class="issue-group warning-group">
              <h4>{{ phase }}</h4>
              <ul>
                <li v-for="(item, index) in items" :key="`${phase}-${index}`" class="issue-item">
                  <span class="issue-message">{{ item.message }}</span>
                  <span class="issue-badges">
                    <span>{{ item.severity || 'WARNING' }}</span>
                    <span>L {{ item.line || 1 }}</span>
                    <span>C {{ item.column || 1 }}</span>
                    <span v-if="item.fragment">{{ item.fragment }}</span>
                  </span>
                </li>
              </ul>
            </div>
          </section>

          <section v-if="primarySuggestion" class="result-section suggestion-section">
            <h3>Sugerencia de corrección</h3>
            <div class="suggestion-card">
              <div>
                <h4>{{ primarySuggestion.title }}</h4>
                <p>{{ primarySuggestion.explanation }}</p>
              </div>
              <span class="suggestion-meta">
                {{ primarySuggestion.sourcePhase }} · {{ Math.round((primarySuggestion.confidence || 0) * 100) }}%
              </span>
              <pre>{{ primarySuggestion.fixedQuery }}</pre>
              <button class="secondary-button apply-button" type="button" @click="applySuggestion(primarySuggestion.fixedQuery)">
                Aplicar sugerencia
              </button>
            </div>
          </section>

          <details class="result-section">
            <summary>Tokens</summary>
            <div class="token-grid">
              <span v-for="(token, index) in result.tokens" :key="`${token.lexeme}-${token.line}-${token.column}-${index}`">
                {{ token.type }}: {{ token.lexeme }}
              </span>
            </div>
          </details>

          <details class="result-section">
            <summary>AST</summary>
            <pre>{{ JSON.stringify(result.ast, null, 2) }}</pre>
          </details>

          <details class="result-section">
            <summary>Resultado semantico</summary>
            <pre>{{ JSON.stringify(result.semanticResult, null, 2) }}</pre>
          </details>
        </div>

        <div v-else class="empty-state">
          Ejecuta una validacion para ver la respuesta del backend. Los tokens, AST y resultado semantico apareceran despues del analisis.
        </div>
      </div>
    </section>

    <section v-else-if="authUser" class="dashboard-panel">
      <div class="panel-header">
        <h2>Dashboard</h2>
        <button class="secondary-button refresh-button" type="button" :disabled="dashboardLoading" @click="loadDashboard">
          Actualizar
        </button>
      </div>

      <div v-if="dashboardLoading" class="empty-state">
        Cargando historial...
      </div>

      <div v-else-if="dashboardError" class="error-box">
        {{ dashboardError }}
      </div>

      <div v-else>
        <div class="stats-grid">
          <div class="stat-card">
            <span>Total de consultas</span>
            <strong>{{ dashboardStats?.total || 0 }}</strong>
          </div>
          <div class="stat-card">
            <span>Consultas validas</span>
            <strong>{{ dashboardStats?.valid || 0 }}</strong>
          </div>
          <div class="stat-card">
            <span>Consultas invalidas</span>
            <strong>{{ dashboardStats?.invalid || 0 }}</strong>
          </div>
          <div class="stat-card">
            <span>Warnings</span>
            <strong>{{ dashboardStats?.warningTotal || 0 }}</strong>
          </div>
          <div class="stat-card">
            <span>Motor mas usado</span>
            <strong>{{ dashboardStats?.mostUsedEngine || '-' }}</strong>
          </div>
        </div>

        <section class="result-section">
          <h3>Ultimas consultas</h3>
          <div v-if="!dashboardItems.length" class="empty-state">
            No hay consultas analizadas todavía. Valida una query para comenzar a generar estadísticas.
          </div>
          <div v-else class="history-table-wrap">
            <table class="history-table">
              <thead>
                <tr>
                  <th>Fecha</th>
                  <th>Motor</th>
                  <th>Estado</th>
                  <th>Errores</th>
                  <th>Warnings</th>
                  <th>Query</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in dashboardItems" :key="item.id">
                  <td>{{ formatDate(item.createdAt) }}</td>
                  <td>{{ item.engine }}</td>
                  <td>
                    <span class="result-badge" :class="item.valid ? 'result-ok' : 'result-error'">
                      {{ item.valid ? 'Valida' : 'Invalida' }}
                    </span>
                  </td>
                  <td>{{ item.errorCount }}</td>
                  <td>{{ item.warningCount }}</td>
                  <td class="query-cell" :title="item.originalQuery">{{ shortQuery(item.originalQuery) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </section>
  </main>
</template>
