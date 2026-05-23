import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App.vue'
import { validateQuery } from './services/queryValidationApi'

vi.mock('./services/queryValidationApi', () => ({
  validateQuery: vi.fn()
}))

function mountApp() {
  return mount(App)
}

describe('App', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    delete document.documentElement.dataset.theme
  })

  it('renderiza lista de errores y warnings por fase', async () => {
    validateQuery.mockResolvedValueOnce({
      success: false,
      message: 'La query contiene errores.',
      errors: [
        {
          phase: 'PARSER',
          severity: 'ERROR',
          message: 'Se esperaba FROM.',
          line: 1,
          column: 15,
          fragment: 'WHERE'
        }
      ],
      warnings: [
        {
          phase: 'SEMANTIC',
          severity: 'WARNING',
          message: 'UPDATE sin WHERE.',
          line: 1,
          column: 1,
          fragment: 'UPDATE'
        }
      ],
      tokens: [],
      ast: null,
      semanticResult: null,
      output: null
    })

    const wrapper = mountApp()
    await wrapper.find('textarea').setValue('SELECT nombre WHERE edad > 18;')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Se encontraron errores en la consulta.')
    expect(wrapper.text()).toContain('PARSER')
    expect(wrapper.text()).toContain('Se esperaba FROM.')
    expect(wrapper.text()).toContain('SEMANTIC')
    expect(wrapper.text()).toContain('UPDATE sin WHERE.')
    expect(wrapper.text()).toContain('L 1')
    expect(wrapper.text()).toContain('C 15')
  })

  it('renderiza errores HTTP desde el body', async () => {
    validateQuery.mockRejectedValueOnce(new Error('Request invalido.', {
      cause: {
        errors: [
          {
            phase: 'SYSTEM',
            severity: 'ERROR',
            message: 'query: La query no puede estar vacia.',
            line: 1,
            column: 1,
            fragment: 'request'
          }
        ]
      }
    }))

    const wrapper = mountApp()
    await wrapper.find('textarea').setValue('')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('SYSTEM')
    expect(wrapper.text()).toContain('query: La query no puede estar vacia.')
  })

  it('no muestra AST ni tokens abiertos por defecto', async () => {
    validateQuery.mockResolvedValueOnce({
      success: true,
      message: 'Consulta valida.',
      errors: [],
      warnings: [],
      tokens: [{ type: 'KEYWORD', lexeme: 'SELECT', line: 1, column: 1 }],
      ast: { type: 'Query' },
      semanticResult: { valid: true },
      output: null
    })

    const wrapper = mountApp()
    await wrapper.find('textarea').setValue('SELECT * FROM usuarios;')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    const details = wrapper.findAll('details')
    expect(details.length).toBeGreaterThan(0)
    expect(details.every((item) => !item.attributes('open'))).toBe(true)
  })

  it('desactiva botones mientras carga', async () => {
    let resolveValidation
    validateQuery.mockReturnValueOnce(new Promise((resolve) => {
      resolveValidation = resolve
    }))

    const wrapper = mountApp()
    await wrapper.find('textarea').setValue('SELECT * FROM usuarios;')
    await wrapper.find('form').trigger('submit.prevent')

    expect(wrapper.find('.primary-button').attributes('disabled')).toBeDefined()
    expect(wrapper.find('.secondary-button').attributes('disabled')).toBeDefined()

    resolveValidation({
      success: true,
      message: 'Consulta valida.',
      errors: [],
      warnings: [],
      tokens: [],
      ast: null,
      semanticResult: null,
      output: null
    })
    await flushPromises()

    expect(wrapper.find('.primary-button').attributes('disabled')).toBeUndefined()
  })

  it('restaura el modo oscuro guardado', () => {
    localStorage.setItem('sql-platform-theme', 'dark')

    const wrapper = mountApp()

    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(wrapper.find('.theme-toggle').attributes('aria-pressed')).toBe('true')
  })

  it('muestra y aplica una sugerencia de correccion', async () => {
    validateQuery.mockResolvedValueOnce({
      success: false,
      message: 'La query contiene errores.',
      errors: [
        {
          phase: 'DIALECT',
          severity: 'ERROR',
          message: 'TOP no es valido para MySQL. Usa LIMIT.',
          line: 1,
          column: 8,
          fragment: 'TOP'
        }
      ],
      warnings: [],
      tokens: [],
      ast: null,
      semanticResult: null,
      output: null,
      suggestions: [
        {
          title: 'Cambiar TOP por LIMIT',
          explanation: 'MySQL usa LIMIT para limitar resultados.',
          fixedQuery: 'SELECT * FROM usuarios LIMIT 10;',
          confidence: 0.95,
          sourcePhase: 'DIALECT'
        }
      ]
    })

    const wrapper = mountApp()
    await wrapper.find('textarea').setValue('SELECT TOP 10 * FROM usuarios;')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Sugerencia de corrección')
    expect(wrapper.text()).toContain('Cambiar TOP por LIMIT')

    await wrapper.find('.apply-button').trigger('click')

    expect(wrapper.find('textarea').element.value).toBe('SELECT * FROM usuarios LIMIT 10;')
  })
})
