import { createRouter, createWebHistory } from 'vue-router'
import api from './api'

const Login = () => import('./views/Login.vue')
const Layout = () => import('./views/Layout.vue')
const Dashboard = () => import('./views/Dashboard.vue')
const Artisans = () => import('./views/Artisans.vue')
const Categories = () => import('./views/Categories.vue')
const Works = () => import('./views/Works.vue')
const Auctions = () => import('./views/Auctions.vue')
const Announcements = () => import('./views/Announcements.vue')
const Users = () => import('./views/Users.vue')
const Insights = () => import('./views/Insights.vue')

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: Login },
    {
      path: '/',
      component: Layout,
      children: [
        { path: '', component: Dashboard },
        { path: 'artisans', component: Artisans },
        { path: 'categories', component: Categories, meta: { roles: ['ADMIN', 'REVIEWER', 'ARTISAN'] } },
        { path: 'works', component: Works },
        { path: 'auctions', component: Auctions },
        { path: 'insights', component: Insights, meta: { roles: ['ADMIN', 'REVIEWER', 'ARTISAN'] } },
        { path: 'announcements', component: Announcements },
        { path: 'users', component: Users, meta: { roles: ['ADMIN'] } }
        ,{ path: 'auction-logs', component: () => import('./views/AuctionLogs.vue'), meta: { roles: ['ADMIN'] } }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})

router.beforeEach(async to => {
  const token = localStorage.getItem('heritage_token')
  if (!token) return to.path === '/login' ? true : '/login'

  try {
    const { data } = await api.get('/auth/me', { silentError: true })
    const user = data.data || {}
    localStorage.setItem('heritage_user', JSON.stringify(user))
    window.dispatchEvent(new CustomEvent('heritage-user-updated', { detail: user }))

    if (to.path === '/login') return '/'
    if (to.meta.roles && !to.meta.roles.includes(user.role)) return '/'
  } catch {
    localStorage.removeItem('heritage_token')
    localStorage.removeItem('heritage_user')
    return to.path === '/login' ? true : '/login'
  }
})

export default router
