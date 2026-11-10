<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import api from '../api'

const stats = ref({ regionData: {} })
const chart = ref()
const exportLoading = ref(false)
let currentUser = {}
try {
  currentUser = JSON.parse(localStorage.getItem('heritage_user') || '{}')
} catch {
  currentUser = {}
}
const canExport = ['ADMIN', 'REVIEWER'].includes(currentUser.role)
let chartInstance

function resizeChart() {
  chartInstance?.resize()
}

onMounted(async () => {
  stats.value = (await api.get('/stats/overview')).data.data
  await nextTick()
  chartInstance = echarts.init(chart.value)
  chartInstance.setOption({
    tooltip: {},
    xAxis: { type: 'category', data: Object.keys(stats.value.regionData) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      type: 'bar',
      data: Object.values(stats.value.regionData),
      itemStyle: { color: '#b2864b', borderRadius: [5, 5, 0, 0] }
    }]
  })
  window.addEventListener('resize', resizeChart)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  chartInstance?.dispose()
})

async function exportArchives() {
  exportLoading.value = true
  try {
    const response = await api.get('/stats/export', { responseType: 'blob' })
    const url = URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = url
    link.download = `非遗艺人档案-${new Date().toISOString().slice(0, 10)}.csv`
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
    ElMessage.success('档案 CSV 已导出')
  } finally {
    exportLoading.value = false
  }
}
</script>

<template>
  <div class="page">
    <div class="hero">
      <small>数字赋能非遗传承</small>
      <h1>让每一位艺人的故事，都被认真记录</h1>
      <p>覆盖档案采集、审核归档、作品展示、非遗拍卖与数据分析的完整管理流程。</p>
    </div>
    <div class="cards">
      <div class="stat">艺人档案<strong>{{ stats.artisanCount || 0 }}</strong></div>
      <div class="stat">已归档<strong>{{ stats.archivedCount || 0 }}</strong></div>
      <div class="stat">待审核<strong>{{ stats.pendingCount || 0 }}</strong></div>
      <div class="stat">作品总数<strong>{{ stats.workCount || 0 }}</strong></div>
      <div class="stat">拍卖总场次<strong>{{ stats.auctionCount || 0 }}</strong></div>
      <div class="stat">活跃拍卖<strong>{{ stats.activeAuctionCount || 0 }}</strong></div>
    </div>
    <div class="panel">
      <div class="page-title">
        <h2>艺人地区分布</h2>
        <el-button v-if="canExport" type="primary" :loading="exportLoading" @click="exportArchives">导出档案 CSV</el-button>
      </div>
      <div ref="chart" style="height:340px" />
    </div>
  </div>
</template>
