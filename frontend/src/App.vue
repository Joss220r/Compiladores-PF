<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { validateQuery } from './services/queryValidationApi'

const THEME_STORAGE_KEY = 'sql-platform-theme'

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
</script>

<template>
  <main class="app-shell">
    <section class="workspace">
      <div class="editor-panel">
        <div class="title-row">
          <div>
            <h1>Validador de querys</h1>
          </div>
          <button class="theme-toggle" type="button" :aria-pressed="theme === 'dark'" @click="toggleTheme">
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
              {{ loading ? 'Validando...' : 'Validar query' }}
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
          Ejecuta una validacion para ver la respuesta del backend.
        </div>
      </div>
    </section>
  </main>
</template>
