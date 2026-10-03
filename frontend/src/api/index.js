import axios from 'axios'

const http = axios.create({
  baseURL: '/api',
  timeout: 15000
})

http.interceptors.response.use(
  (res) => res.data,
  (err) => {
    const message = err?.response?.data?.message || err.message || '请求失败'
    return Promise.reject(new Error(message))
  }
)

export const intakeApi = {
  list: () => http.get('/intake/today'),
  all: () => http.get('/intake'),
  create: (data) => http.post('/intake', data),
  supplierQuality: () => http.get('/intake/supplier-quality'),
  vocab: () => http.get('/intake/vocab')
}

export const witheringApi = {
  list: () => http.get('/withering'),
  ready: () => http.get('/withering/ready'),
  open: (data) => http.post('/withering', data),
  log: (troughNo, data) => http.put(`/withering/${troughNo}/log`, data),
  release: (troughNo) => http.post(`/withering/${troughNo}/release`)
}

export const processApi = {
  list: () => http.get('/process'),
  detail: (batchNo) => http.get(`/process/${batchNo}`),
  create: (data) => http.post('/process', data),
  start: (batchNo, data) => http.post(`/process/${batchNo}/start`, data),
  finish: (batchNo, data) => http.post(`/process/${batchNo}/finish`, data),
  steps: (batchNo) => http.get(`/process/${batchNo}/steps`),
  yieldByTeaType: () => http.get('/process/yield')
}

export const equipmentApi = {
  list: () => http.get('/equipment'),
  board: () => http.get('/equipment/board'),
  byType: (type) => http.get(`/equipment/type/${type}`),
  updateStatus: (code, status, tempC) =>
    http.put(`/equipment/${code}/status`, null, { params: { status, tempC } }),
  reportFault: (code, data) => http.post(`/equipment/${code}/fault`, data),
  rooms: () => http.get('/equipment/rooms'),
  load: (roomNo, params) => http.post(`/equipment/rooms/${roomNo}/load`, null, { params }),
  unload: (roomNo) => http.post(`/equipment/rooms/${roomNo}/unload`)
}

export const handoverApi = {
  list: (status) => http.get('/handover', { params: status ? { status } : {} }),
  detail: (handoverNo) => http.get(`/handover/${handoverNo}`),
  startClean: (handoverNo, data) => http.post(`/handover/${handoverNo}/start-clean`, data),
  finishClean: (handoverNo, data) => http.post(`/handover/${handoverNo}/finish-clean`, data),
  reportFault: (handoverNo, data) => http.post(`/handover/${handoverNo}/fault`, data),
  repairDone: (handoverNo, data) => http.post(`/handover/${handoverNo}/repair-done`, data),
  confirm: (handoverNo, data) => http.post(`/handover/${handoverNo}/confirm`, data)
}

export const evaluationApi = {
  list: () => http.get('/evaluation'),
  create: (data) => http.post('/evaluation', data),
  ranking: () => http.get('/evaluation/ranking'),
  products: () => http.get('/evaluation/product'),
  store: (data) => http.post('/evaluation/product', data)
}

export const statsApi = {
  dashboard: () => http.get('/stats/dashboard'),
  supplierQuality: () => http.get('/stats/supplier-quality'),
  teaYield: () => http.get('/stats/tea-yield'),
  evalRanking: () => http.get('/stats/eval-ranking')
}

export default http
