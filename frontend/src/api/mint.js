import { api } from './client'

export const getWireInstructions = () => api.get('/api/mint/wire-instructions').then((r) => r.data)

export const buyUsdc = (payload) => api.post('/api/mint/buy', payload).then((r) => r.data)

export const sellUsdc = (payload) => api.post('/api/mint/sell', payload).then((r) => r.data)

export const listMintOrders = () => api.get('/api/mint/orders').then((r) => r.data)
