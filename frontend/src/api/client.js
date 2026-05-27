const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080'

async function parseResponse(response) {
  if (!response.ok) {
    const text = await response.text()
    throw new Error(text || `HTTP ${response.status}`)
  }
  const data = await response.json()
  if (!data.success) {
    throw new Error(data.message || '请求失败')
  }
  return data.data
}

export async function uploadWorkbook(file) {
  const form = new FormData()
  form.append('file', file)
  return parseResponse(await fetch(`${API_BASE}/api/files/upload`, {
    method: 'POST',
    body: form
  }))
}

export async function startValidation(datasetId, enableAiAnalysis) {
  return parseResponse(await fetch(`${API_BASE}/api/validations`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ datasetId, enableAiAnalysis })
  }))
}

export async function fetchSummary(jobId) {
  return parseResponse(await fetch(`${API_BASE}/api/validations/${jobId}/summary`))
}

export async function fetchFindings(jobId, filters = {}) {
  const params = new URLSearchParams({ jobId })
  Object.entries(filters).forEach(([key, value]) => {
    if (value) params.append(key, value)
  })
  return parseResponse(await fetch(`${API_BASE}/api/findings?${params}`))
}

export async function fetchFindingDetail(findingId) {
  return parseResponse(await fetch(`${API_BASE}/api/findings/${findingId}`))
}

export async function analyzeFinding(findingId) {
  return parseResponse(await fetch(`${API_BASE}/api/ai/findings/${findingId}/analysis`))
}

export async function draftValidationSql(request) {
  return parseResponse(await fetch(`${API_BASE}/api/ai/sql-drafts`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request)
  }))
}

export async function recommendRuleBinding(datasetId, ruleId) {
  return parseResponse(await fetch(`${API_BASE}/api/ai/rule-binding/recommend`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ datasetId, ruleId })
  }))
}

export async function fetchRules(datasetId) {
  return parseResponse(await fetch(`${API_BASE}/api/rules?datasetId=${encodeURIComponent(datasetId)}`))
}

export async function updateRuleBinding(datasetId, ruleId, binding) {
  return parseResponse(await fetch(
    `${API_BASE}/api/rules/${encodeURIComponent(datasetId)}/${encodeURIComponent(ruleId)}/binding`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(binding)
    }
  ))
}

export async function createReport(jobId, format = 'MARKDOWN') {
  return parseResponse(await fetch(`${API_BASE}/api/reports`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ jobId, format })
  }))
}

export function reportDownloadUrl(reportId) {
  return `${API_BASE}/api/reports/${reportId}/download`
}
