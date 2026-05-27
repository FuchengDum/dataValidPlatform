import { reactive, computed } from 'vue'

const state = reactive({
  dataset: null,
  job: null,
  summary: null,
  rules: [],
  findings: [],
  selectedRuleId: '',
  selectedFindingId: '',
  loading: false,
  message: '',
  error: ''
})

const getters = {
  selectedRule: computed(() => {
    return state.rules.find((rule) => rule.ruleId === state.selectedRuleId) || state.rules[0] || null
  }),
  selectedFinding: computed(() => {
    return state.findings.find((f) => f.findingId === state.selectedFindingId) || null
  })
}

const actions = {
  setDataset(data) {
    state.dataset = data
  },
  setJob(job) {
    state.job = job
  },
  setSummary(summary) {
    state.summary = summary
  },
  setRules(rules) {
    state.rules = rules
    if (rules.length > 0 && !state.selectedRuleId) {
      state.selectedRuleId = rules[0].ruleId
    }
  },
  setFindings(findings) {
    state.findings = findings
  },
  setSelectedRuleId(ruleId) {
    state.selectedRuleId = ruleId
  },
  setSelectedFindingId(findingId) {
    state.selectedFindingId = findingId
  },
  setLoading(loading) {
    state.loading = loading
  },
  setMessage(message) {
    state.message = message
  },
  setError(error) {
    state.error = error
  },
  clearState() {
    state.dataset = null
    state.job = null
    state.summary = null
    state.rules = []
    state.findings = []
    state.selectedRuleId = ''
    state.selectedFindingId = ''
    state.message = ''
    state.error = ''
  }
}

export default {
  state,
  getters,
  actions
}
