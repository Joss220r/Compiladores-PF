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
const query = ref('SELECT * FROM usuarios WHERE edad > 18;')
const result = ref(null)
const errorMessage = ref('')
const loading = ref(false)

const resultClass = computed(() => {
  if (!result.value) {
    return ''
  }

  return result.value.valid ? 'result-ok' : 'result-error'
})

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
    const details = Array.isArray(error.cause?.errors)
      ? error.cause.errors.join(' ')
      : error.message
    errorMessage.value = details
  } finally {
    loading.value = false
  }
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
            <textarea v-model="query" rows="12" spellcheck="false" />
          </label>

          <button class="primary-button" type="submit" :disabled="loading">
            {{ loading ? 'Validando...' : 'Validar query' }}
          </button>
        </form>
      </div>

      <div class="result-panel">
        <div class="panel-header">
          <h2>Resultado</h2>
          <span v-if="result" class="result-badge" :class="resultClass">
            {{ result.valid ? 'Valida' : 'Invalida' }}
          </span>
        </div>

        <div v-if="errorMessage" class="error-box">
          {{ errorMessage }}
        </div>

        <div v-else-if="result" class="result-content">
          <p class="message">{{ result.message }}</p>

          <section v-if="result.errors?.length" class="result-section">
            <h3>Errores</h3>
            <ul>
              <li v-for="item in result.errors" :key="item">{{ item }}</li>
            </ul>
          </section>

          <section class="result-section">
            <h3>Tokens</h3>
            <div class="token-grid">
              <span v-for="token in result.tokens" :key="`${token.lexeme}-${token.column}`">
                {{ token.type }}: {{ token.lexeme }}
              </span>
            </div>
          </section>

          <section class="result-section">
            <h3>AST</h3>
            <pre>{{ JSON.stringify(result.ast, null, 2) }}</pre>
          </section>

          <section class="result-section">
            <h3>Semantico</h3>
            <pre>{{ JSON.stringify(result.semanticResult, null, 2) }}</pre>
          </section>
        </div>

        <div v-else class="empty-state">
          Ejecuta una validacion para ver la respuesta del backend.
        </div>
      </div>
    </section>
  </main>
</template>
