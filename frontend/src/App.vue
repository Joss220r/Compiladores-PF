<script setup>
import { computed, ref } from 'vue'
import { validateQuery } from './services/queryValidationApi'

const engines = [
  { label: 'SQL generico', value: 'SQL' },
  { label: 'NoSQL', value: 'NOSQL' },
  { label: 'MongoDB', value: 'MONGODB' },
  { label: 'SQL Server', value: 'SQL_SERVER' },
  { label: 'MySQL', value: 'MYSQL' },
  { label: 'PostgreSQL', value: 'POSTGRESQL' },
  { label: 'Redis', value: 'REDIS' }
]

const selectedEngine = ref('SQL')
const query = ref('')
const result = ref(null)
const errorMessage = ref('')
const loading = ref(false)

const resultClass = computed(() => {
  if (!result.value) {
    return ''
  }

  return result.value.success ? 'result-ok' : 'result-error'
})

const groupedErrors = computed(() => groupIssues(result.value?.errors || []))
const groupedWarnings = computed(() => groupIssues(result.value?.warnings || []))

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
      output: null
    }
  } finally {
    loading.value = false
  }
}

function clearForm() {
  query.value = ''
  result.value = null
  errorMessage.value = ''
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
