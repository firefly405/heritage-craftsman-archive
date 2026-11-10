<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '../api'

const router = useRouter()
const route = useRoute()
let storedUser = {}
try {
  storedUser = JSON.parse(localStorage.getItem('heritage_user') || '{}')
} catch {
  storedUser = {}
}
const user = ref(storedUser)
const logoutLoading = ref(false)
const roleNames = { ADMIN: '管理员', REVIEWER: '审核员', ARTISAN: '非遗艺人', VIEWER: '普通用户' }

const menus = computed(() => {
  if (user.value.role === 'VIEWER') return [
    ['/', '数据总览'],
    ['/artisans', '非遗艺人'],
    ['/works', '非遗作品'],
    ['/auctions', '拍卖作品'],
    ['/announcements', '通知公告']
  ]
  const items = [
    ['/', '数据总览'],
    ['/artisans', '艺人档案'],
    ['/categories', '技艺类目'],
    ['/works', '作品服务'],
    ['/auctions', '非遗拍卖'],
    ['/insights', '档案完整度检查'],
    ['/announcements', '通知公告']
  ]
  if (user.value.role === 'ADMIN') items.push(['/users', '用户权限'],['/auction-logs','拍卖日志'])
  return items
})

function syncUser(event) {
  user.value = event.detail || {}
}

onMounted(() => window.addEventListener('heritage-user-updated', syncUser))
onBeforeUnmount(() => window.removeEventListener('heritage-user-updated', syncUser))

async function logout() {
  logoutLoading.value = true
  try {
    await api.post('/auth/logout')
  } catch {
    // 即使服务端暂不可用，也要完成本地退出。
  } finally {
    localStorage.removeItem('heritage_token')
    localStorage.removeItem('heritage_user')
    logoutLoading.value = false
    router.push('/login')
  }
}
</script>

<template>
  <el-container class="shell">
    <el-aside width="230px">
      <div class="logo"><b>非遗艺人档案</b><small>HERITAGE ARCHIVE</small></div>
      <el-menu router :default-active="route.path">
        <el-menu-item v-for="menu in menus" :key="menu[0]" :index="menu[0]">{{ menu[1] }}</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container class="content-shell">
      <el-header>
        <span class="system-name">民间非遗手工艺人档案管理系统</span>
        <div class="account">
          <span class="who">{{ user.realName }} · {{ roleNames[user.role] || user.role }}</span>
          <el-button link :loading="logoutLoading" @click="logout">退出登录</el-button>
        </div>
      </el-header>
      <el-main><router-view /></el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.shell{min-height:100vh}.el-aside{background:#173d2a;color:white}.logo{height:86px;padding:20px 24px;border-bottom:1px solid #345945}.logo b,.logo small{display:block}.logo small{font-size:10px;color:#a8c2b1;letter-spacing:2px;margin-top:7px}.el-menu{border:0;background:transparent}.el-menu-item{color:#c8d9ce;margin:5px 12px;border-radius:8px}.el-menu-item:hover,.el-menu-item.is-active{background:#b2864b;color:#fff}.content-shell{min-width:0}.el-header{height:70px;background:white;display:flex;justify-content:space-between;align-items:center;padding:0 26px;border-bottom:1px solid #e8ece9}.account{display:flex;align-items:center;white-space:nowrap}.who{margin-right:18px;color:#637169}.el-main{padding:0}
@media(max-width:760px){.shell{display:block}.el-aside{width:100%!important}.logo{height:68px;padding:14px 18px}.logo small{margin-top:4px}.el-menu{display:flex;overflow-x:auto;padding:5px 6px}.el-menu-item{height:42px;margin:2px 4px;padding:0 14px!important;flex:0 0 auto}.el-header{height:58px;padding:0 16px}.system-name{display:none}.who{max-width:190px;overflow:hidden;text-overflow:ellipsis}.el-main{overflow:hidden}}
</style>
