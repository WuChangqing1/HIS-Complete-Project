import axios from 'axios'
const api = axios.create({ baseURL: '/api', timeout: 15000 })
api.interceptors.request.use(config => { const token = sessionStorage.getItem('his_token'); if (token) config.headers.Authorization = `Bearer ${token}`; return config })
api.interceptors.response.use(response => response.data, error => { if (error.response?.status === 401 && !error.config.url.includes('/auth/login')) { sessionStorage.removeItem('his_token'); window.dispatchEvent(new Event('his-expired')) } return Promise.reject(new Error(error.response?.data?.error || (error.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : '网络错误，请检查服务状态'))) })
export default api
