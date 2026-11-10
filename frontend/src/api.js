import axios from 'axios'
import { ElMessage } from 'element-plus'
const api=axios.create({baseURL:'/api',timeout:10000})
api.interceptors.request.use(c=>{const t=localStorage.getItem('heritage_token');if(t)c.headers.Authorization=`Bearer ${t}`;return c})
api.interceptors.response.use(r=>{if(r.data?.success===false){if(!r.config?.silentError)ElMessage.error(r.data.message);return Promise.reject(new Error(r.data.message))}return r},e=>{if(e.response?.status===401){localStorage.removeItem('heritage_token');localStorage.removeItem('heritage_user');if(location.pathname!=='/login')location.href='/login'}if(!e.config?.silentError)ElMessage.error(e.response?.data?.message||e.message||'请求失败');return Promise.reject(e)})
export default api
