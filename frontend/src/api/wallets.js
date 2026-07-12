import { api } from './client'

export const listWallets = () => api.get('/api/wallets').then((r) => r.data)

export const createWallet = (label) => api.post('/api/wallets', { label }).then((r) => r.data)

export const getBalances = (walletId) => api.get(`/api/wallets/${walletId}/balances`).then((r) => r.data)

export const requestFaucet = (walletId) => api.post(`/api/wallets/${walletId}/faucet`).then((r) => r.data)
