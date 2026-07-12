import { api } from './client'

export const sendTransfer = (payload) => api.post('/api/transfers', payload).then((r) => r.data)

export const refreshTransfer = (id) => api.post(`/api/transfers/${id}/refresh`).then((r) => r.data)

export const listTransactions = () => api.get('/api/transactions').then((r) => r.data)
