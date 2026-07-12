import axios from 'axios'

// In dev, Vite proxies /api to the Spring backend (see vite.config.js).
// In prod, set VITE_API_BASE_URL to the deployed backend origin.
const baseURL = import.meta.env.VITE_API_BASE_URL || ''
const apiKey = import.meta.env.VITE_APP_API_KEY || 'local-dev-key'

export const api = axios.create({
  baseURL,
  headers: {
    'X-App-Api-Key': apiKey,
    'Content-Type': 'application/json'
  }
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const message =
      error.response?.data?.message || error.message || 'Something went wrong talking to the backend.'
    return Promise.reject(new Error(message))
  }
)
