<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const statusOptions = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'UPCOMING', label: '即将开始' },
  { value: 'ONGOING', label: '竞价中' },
  { value: 'ENDED', label: '已结束' },
  { value: 'CANCELLED', label: '已取消' }
]

const statusMeta = {
  DRAFT: { label: '草稿', type: 'info' },
  UPCOMING: { label: '即将开始', type: 'warning' },
  ONGOING: { label: '竞价中', type: 'success' },
  ENDED: { label: '已结束', type: '' },
  CANCELLED: { label: '已取消', type: 'danger' }
}

let currentUser = {}
try {
  currentUser = JSON.parse(localStorage.getItem('heritage_user') || '{}')
} catch {
  currentUser = {}
}

const auctions = ref([])
const works = ref([])
const artisanProfiles = ref([])
const activeWorkIds = ref(new Set())
const keyword = ref('')
const status = ref('')
const loading = ref(false)
const now = ref(Date.now())
const formDialog = ref(false)
const formRef = ref()
const editingId = ref(null)
const saving = ref(false)
const actionLoading = ref('')
const bidDialog = ref(false)
const bidLoading = ref(false)
const bidOpeningId = ref(null)
const selectedAuction = ref(null)
const bidForm = reactive({ amount: 0 })
const historyDrawer = ref(false)
const historyLoading = ref(false)
const bids = ref([])
const historyAuction = ref(null)
const settlementVisible=ref(false),settlement=ref(null)
async function showSettlement(row){try{settlement.value=(await api.get(`/auctions/${row.id}`)).data.data;settlementVisible.value=true}catch{/* interceptor */}}
const paymentMethods = {
  ALIPAY_SANDBOX: '支付宝沙箱', LOCAL: '本地模拟',
  ALIPAY: '支付宝（旧模拟）', WECHAT: '微信支付（旧模拟）', BANK: '银行卡（旧模拟）'
}
const paymentStorageKey = `heritage-demo-payments-${currentUser.id}`
const pendingSandboxKey = `heritage-sandbox-pending-${currentUser.id}`
const paymentRecords = ref({})
try {
  paymentRecords.value = JSON.parse(sessionStorage.getItem(paymentStorageKey) || '{}') || {}
} catch {
  paymentRecords.value = {}
}
const paymentVisible = ref(false)
const paymentAuction = ref(null)
const paymentMethod = ref('')
const paymentOpeningId = ref(null)
const paymentRecord = computed(() => paymentAuction.value && paymentRecords.value[paymentAuction.value.id])

async function openPayment(row) {
  paymentOpeningId.value = row.id
  try {
    const latest = (await api.get(`/auctions/${row.id}`)).data.data
    if (!latest || !canPay(latest)) {
      ElMessage.warning('当前订单不可支付，请刷新拍卖列表')
      await load()
      return
    }
    paymentAuction.value = latest
    paymentMethod.value = ''
    paymentVisible.value = true
  } catch {
    // 请求错误已由统一拦截器提示。
  } finally {
    paymentOpeningId.value = null
  }
}

async function confirmPayment() {
  if (!paymentAuction.value || paymentRecord.value || !paymentMethod.value) return
  if (paymentMethod.value === 'LOCAL') {
    savePaymentRecord(paymentAuction.value.id, 'LOCAL')
    ElMessage.success('本地模拟支付完成，未发生真实扣款')
    return
  }
  paymentOpeningId.value = paymentAuction.value.id
  try {
    const { data } = await api.get('/alipay/sandbox/page', {
      params: { auctionId: paymentAuction.value.id }
    })
    const formHtml = data.data
    if (!formHtml) throw new Error('支付宝沙箱跳转内容为空')
    sessionStorage.setItem(pendingSandboxKey, String(paymentAuction.value.id))
    document.open()
    document.write(formHtml)
    document.close()
  } catch {
    // 未配置沙箱参数时由统一拦截器提示，不改变本地演示结果。
  } finally {
    paymentOpeningId.value = null
  }
}

function savePaymentRecord(auctionId, method) {
  paymentRecords.value = {
    ...paymentRecords.value,
    [auctionId]: { method, paidAt: new Date().toLocaleString('zh-CN', { hour12: false }) }
  }
  try {
    sessionStorage.setItem(paymentStorageKey, JSON.stringify(paymentRecords.value))
  } catch {
    // 浏览器禁用会话存储时，结果仍保留到当前页面关闭。
  }
}

function resetPaymentRecord() {
  if (!paymentAuction.value) return
  const records = { ...paymentRecords.value }
  delete records[paymentAuction.value.id]
  paymentRecords.value = records
  paymentMethod.value = ''
  try {
    sessionStorage.setItem(paymentStorageKey, JSON.stringify(records))
  } catch {
    // 浏览器禁用会话存储时，当前页面仍可重新演示。
  }
}

function applySandboxReturn() {
  const params = new URLSearchParams(window.location.search)
  if (params.get('alipay_demo') !== 'success') return
  const auctionId = params.get('auction_id')
  if (auctionId && sessionStorage.getItem(pendingSandboxKey) === auctionId) {
    sessionStorage.removeItem(pendingSandboxKey)
    savePaymentRecord(auctionId, 'ALIPAY_SANDBOX')
    ElMessage.success('支付宝沙箱已返回，本地已记录演示结果（未核验）')
  }
  window.history.replaceState({}, '', '/auctions')
}

const form = reactive({
  workId: null,
  title: '',
  description: '',
  startPrice: 100,
  minIncrement: 10,
  startTime: '',
  endTime: ''
})

const rules = {
  workId: [{ required: true, message: '请选择参与拍卖的作品', trigger: 'change' }],
  title: [{ required: true, message: '请输入拍卖标题', trigger: 'blur' }],
  startPrice: [{ required: true, message: '请输入起拍价', trigger: 'change' }],
  minIncrement: [{ required: true, message: '请输入最低加价幅度', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }]
}

const canCreate = computed(() => ['ADMIN', 'ARTISAN'].includes(currentUser.role))
const availableWorks = computed(() => {
  const archivedProfiles = artisanProfiles.value.filter(profile => profile.status === 'ARCHIVED')
  const archivedArtisanIds = new Set(archivedProfiles.map(profile => String(profile.id)))
  const eligibleWorks = works.value.filter(
    work => work.status === 'DISPLAY'
      && archivedArtisanIds.has(String(work.artisanId))
      && (!activeWorkIds.value.has(String(work.id)) || (editingId.value && String(work.id) === String(form.workId)))
  )
  if (currentUser.role !== 'ARTISAN') return eligibleWorks
  const ownArtisanIds = new Set(
    archivedProfiles
      .filter(profile => String(profile.userId) === String(currentUser.id))
      .map(profile => String(profile.id))
  )
  return eligibleWorks.filter(work => ownArtisanIds.has(String(work.artisanId)))
})
const bidMinimum = computed(() => selectedAuction.value ? minimumBid(selectedAuction.value) : 0)

let clockTimer
let lastBoundaryRefresh = 0
let lastLiveRefresh = 0
let listRequestId = 0
let historyRequestId = 0

onMounted(async () => {
  applySandboxReturn()
  clockTimer = window.setInterval(() => {
    now.value = Date.now()
    const crossedBoundary = auctions.value.some(auction => {
      if (auction.status === 'UPCOMING') return new Date(auction.startTime).getTime() <= now.value
      if (auction.status === 'ONGOING') return new Date(auction.endTime).getTime() <= now.value
      return false
    })
    if (crossedBoundary && now.value - lastBoundaryRefresh > 5000) {
      lastBoundaryRefresh = now.value
      load(true).catch(() => {})
    }
    const hasLiveAuction = auctions.value.some(auction => auction.status === 'ONGOING')
      || (bidDialog.value && selectedAuction.value?.status === 'ONGOING')
    if (hasLiveAuction && now.value - lastLiveRefresh > 8000) {
      lastLiveRefresh = now.value
      load(true).catch(() => {})
    }
  }, 1000)
  try {
    await Promise.all([load(), canCreate.value ? loadWorks() : Promise.resolve()])
  } catch {
    // 请求错误已由统一拦截器提示。
  }
})

onBeforeUnmount(() => window.clearInterval(clockTimer))

async function load(silent = false) {
  const isSilent = silent === true
  const requestId = ++listRequestId
  if (!isSilent) loading.value = true
  try {
    const { data } = await api.get('/auctions', {
      params: { keyword: keyword.value.trim(), status: status.value },
      silentError: isSilent
    })
    if (requestId !== listRequestId) return
    auctions.value = data.data || []
    if (selectedAuction.value) {
      const latest = auctions.value.find(item => item.id === selectedAuction.value.id)
      if (latest) {
        updateSelectedAuction(latest)
      } else if (bidDialog.value) {
        try {
          const detailResponse = await api.get(`/auctions/${selectedAuction.value.id}`, { silentError: isSilent })
          if (requestId !== listRequestId) return
          updateSelectedAuction(detailResponse.data.data)
        } catch {
          if (requestId === listRequestId) {
            bidDialog.value = false
            selectedAuction.value = null
            ElMessage.info('该拍卖已结束或当前不可参与')
          }
        }
      }
    }
  } catch {
    // 交互请求由统一拦截器提示；静默刷新不打扰用户。
  } finally {
    if (!isSilent) loading.value = false
  }
}

async function loadWorks() {
  const [workResponse, artisanResponse, auctionResponse] = await Promise.all([
    api.get('/works'),
    api.get('/artisans'),
    api.get('/auctions')
  ])
  works.value = workResponse.data.data || []
  artisanProfiles.value = artisanResponse.data.data || []
  activeWorkIds.value = new Set(
    (auctionResponse.data.data || [])
      .filter(auction => ['DRAFT', 'UPCOMING', 'ONGOING'].includes(auction.status))
      .map(auction => String(auction.workId))
  )
}

function localDateTime(date) {
  const pad = value => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function normalizeDateTime(value) {
  return value ? String(value).slice(0, 19) : ''
}

async function openCreate() {
  try {
    await loadWorks()
  } catch {
    return
  }
  editingId.value = null
  const start = new Date(Date.now() + 60 * 60 * 1000)
  const end = new Date(Date.now() + 25 * 60 * 60 * 1000)
  start.setSeconds(0, 0)
  end.setSeconds(0, 0)
  Object.assign(form, {
    workId: null,
    title: '',
    description: '',
    startPrice: 100,
    minIncrement: 10,
    startTime: localDateTime(start),
    endTime: localDateTime(end)
  })
  formDialog.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    workId: row.workId,
    title: row.title || '',
    description: row.description || '',
    startPrice: Number(row.startPrice),
    minIncrement: Number(row.minIncrement),
    startTime: normalizeDateTime(row.startTime),
    endTime: normalizeDateTime(row.endTime)
  })
  formDialog.value = true
}

function useWorkDefaults(workId) {
  if (editingId.value) return
  const work = works.value.find(item => item.id === workId)
  if (!work) return
  form.title = work.title || ''
  form.description = work.description || ''
  if (Number(work.price) > 0) form.startPrice = Number(work.price)
}

async function save() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (new Date(form.endTime).getTime() <= new Date(form.startTime).getTime()) {
    ElMessage.warning('结束时间必须晚于开始时间')
    return
  }

  saving.value = true
  try {
    const payload = {
      workId: form.workId,
      title: form.title.trim(),
      description: form.description.trim(),
      startPrice: form.startPrice,
      minIncrement: form.minIncrement,
      startTime: form.startTime,
      endTime: form.endTime
    }
    if (editingId.value) await api.put(`/auctions/${editingId.value}`, payload)
    else await api.post('/auctions', payload)
    ElMessage.success(editingId.value ? '拍卖草稿已更新' : '拍卖草稿已创建')
    formDialog.value = false
    await Promise.all([load(), loadWorks()])
  } catch {
    // 请求错误已由统一拦截器提示。
  } finally {
    saving.value = false
  }
}

function isManager(row) {
  return currentUser.role === 'ADMIN'
    || (currentUser.role === 'ARTISAN' && String(row.sellerId) === String(currentUser.id))
}

function canBid(row) {
  return ['ARTISAN', 'VIEWER'].includes(currentUser.role)
    && row.status === 'ONGOING'
    && String(row.sellerId) !== String(currentUser.id)
}

function canPay(row) {
  return ['ARTISAN', 'VIEWER'].includes(currentUser.role)
    && row.status === 'ENDED'
    && Number(row.bidCount) > 0
    && (row.winnerId != null ? String(row.winnerId) === String(currentUser.id) : row.winnerName === '我')
}

function minimumBid(row) {
  const current = Number(row.currentPrice || row.startPrice || 0)
  return Number(row.bidCount || 0) > 0
    ? Number((current + Number(row.minIncrement || 0)).toFixed(2))
    : Number(row.startPrice || 0)
}

function updateSelectedAuction(auction) {
  selectedAuction.value = auction
  if (!auction || auction.status !== 'ONGOING') {
    if (bidDialog.value) ElMessage.info('本场拍卖已结束竞价')
    bidDialog.value = false
    return
  }
  if (Number(bidForm.amount) < minimumBid(auction)) bidForm.amount = minimumBid(auction)
}

async function openBid(row) {
  bidOpeningId.value = row.id
  try {
    const { data } = await api.get(`/auctions/${row.id}`)
    const latest = data.data
    if (!latest || latest.status !== 'ONGOING') {
      ElMessage.warning('本场拍卖已不在竞价时间内')
      await load()
      return
    }
    selectedAuction.value = latest
    bidForm.amount = minimumBid(latest)
    bidDialog.value = true
  } catch {
    // 请求错误已由统一拦截器提示。
  } finally {
    bidOpeningId.value = null
  }
}

async function submitBid() {
  if (!selectedAuction.value) return
  if (Number(bidForm.amount) < bidMinimum.value) {
    ElMessage.warning(`本次最低出价为 ${currency(bidMinimum.value)}`)
    return
  }
  bidLoading.value = true
  try {
    await api.post(`/auctions/${selectedAuction.value.id}/bid`, { amount: bidForm.amount })
    ElMessage.success('出价成功，请留意后续竞价动态')
    bidDialog.value = false
    await load()
  } catch {
    await load(true).catch(() => {})
  } finally {
    bidLoading.value = false
  }
}

async function showHistory(row) {
  const requestId = ++historyRequestId
  historyAuction.value = row
  historyDrawer.value = true
  historyLoading.value = true
  bids.value = []
  try {
    const { data } = await api.get(`/auctions/${row.id}/bids`)
    if (requestId !== historyRequestId || historyAuction.value?.id !== row.id) return
    bids.value = data.data || []
  } catch {
    // 请求错误已由统一拦截器提示。
  } finally {
    if (requestId === historyRequestId) historyLoading.value = false
  }
}

async function perform(row, action) {
  const configurations = {
    publish: {
      title: '发布拍卖',
      message: `确认发布“${row.title}”？发布后价格与时间规则将不能修改。`,
      confirmButtonText: '确认发布',
      success: '拍卖已发布'
    },
    close: {
      title: '结束拍卖',
      message: `确认现在结束“${row.title}”？有有效出价时将按当前最高价成交。`,
      confirmButtonText: '确认结束',
      success: '拍卖已结束'
    },
    cancel: {
      title: '取消拍卖',
      message: `确认取消“${row.title}”？此操作无法撤销。`,
      confirmButtonText: '确认取消',
      success: '拍卖已取消'
    }
  }
  const config = configurations[action]
  try {
    await ElMessageBox.confirm(config.message, config.title, {
      confirmButtonText: config.confirmButtonText,
      cancelButtonText: '暂不操作',
      type: 'warning'
    })
  } catch {
    return
  }

  actionLoading.value = `${row.id}-${action}`
  try {
    await api.post(`/auctions/${row.id}/${action}`)
    ElMessage.success(config.success)
    await Promise.all([load(), loadWorks()])
  } catch {
    // 请求错误已由统一拦截器提示。
  } finally {
    actionLoading.value = ''
  }
}

function currency(value) {
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY',
    minimumFractionDigits: 2
  }).format(Number(value || 0))
}

function formatTime(value) {
  if (!value) return '--'
  return String(value).replace('T', ' ').slice(0, 16)
}

function countdown(row) {
  if (row.status === 'DRAFT') return '等待发布'
  if (row.status === 'ENDED') return '本场拍卖已结束'
  if (row.status === 'CANCELLED') return '本场拍卖已取消'

  const isUpcoming = row.status === 'UPCOMING'
  const target = new Date(isUpcoming ? row.startTime : row.endTime).getTime()
  const remaining = target - now.value
  if (!Number.isFinite(target) || remaining <= 0) return isUpcoming ? '即将开始' : '即将结束'

  const totalSeconds = Math.floor(remaining / 1000)
  const days = Math.floor(totalSeconds / 86400)
  const hours = Math.floor((totalSeconds % 86400) / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  const parts = []
  if (days) parts.push(`${days}天`)
  parts.push(`${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`)
  return `${isUpcoming ? '距开拍' : '距结束'} ${parts.join(' ')}`
}
</script>

<template>
  <div class="page auction-page">
    <section class="auction-hero">
      <div>
        <span class="eyebrow">HERITAGE AUCTION</span>
        <h2>非遗拍卖</h2>
        <p>让匠心作品被看见，也让每一次出价成为非遗传承的支持。</p>
      </div>
      <el-button v-if="canCreate" class="create-button" type="primary" size="large" @click="openCreate">
        发起拍卖
      </el-button>
    </section>

    <section class="filter-panel">
      <div class="toolbar auction-toolbar">
        <el-input
          v-model="keyword"
          clearable
          placeholder="搜索拍卖标题或卖家"
          class="keyword-input"
          @keyup.enter="load"
          @clear="load"
        />
        <el-select v-model="status" clearable placeholder="全部状态" class="status-select" @change="load">
          <el-option v-for="option in statusOptions" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
        <el-button type="primary" plain @click="load">查询</el-button>
        <span class="result-count">共 {{ auctions.length }} 场</span>
      </div>
    </section>

    <div v-loading="loading" class="auction-list-wrap">
      <div v-if="auctions.length" class="auction-grid">
        <article v-for="auction in auctions" :key="auction.id" class="auction-card">
          <div class="cover-wrap">
            <el-image v-if="auction.coverUrl" :src="auction.coverUrl" fit="cover" class="cover-image">
              <template #error><div class="cover-placeholder">匠心<br>拍卖</div></template>
            </el-image>
            <div v-else class="cover-placeholder">匠心<br>拍卖</div>
            <el-tag class="status-tag" :type="statusMeta[auction.status]?.type || 'info'" effect="dark">
              {{ statusMeta[auction.status]?.label || auction.status }}
            </el-tag>
            <div class="countdown" :class="{ live: auction.status === 'ONGOING' }">
              <i v-if="auction.status === 'ONGOING'" />{{ countdown(auction) }}
            </div>
          </div>

          <div class="auction-card-body">
            <div class="seller-row">
              <span class="seller-avatar">{{ (auction.sellerName || '匠').slice(0, 1) }}</span>
              <span>卖家 · {{ auction.sellerName || '非遗艺人' }}</span>
            </div>
            <h3 :title="auction.title">{{ auction.title }}</h3>
            <p class="description">{{ auction.description || '一件凝聚匠心与传承温度的非遗作品。' }}</p>

            <div class="price-row">
              <div>
                <small>{{ Number(auction.bidCount || 0) ? '当前价' : '起拍价' }}</small>
                <strong>{{ currency(auction.currentPrice || auction.startPrice) }}</strong>
              </div>
              <div class="bid-count"><b>{{ auction.bidCount || 0 }}</b> 次出价</div>
            </div>

            <div class="auction-facts">
              <span>每次加价 <b>{{ currency(auction.minIncrement) }}</b></span>
              <span>{{ formatTime(auction.startTime) }} 开始</span>
              <span>{{ formatTime(auction.endTime) }} 结束</span>
            </div>

            <div v-if="auction.status === 'ENDED' && auction.winnerName" class="winner-line">
              成交人：{{ auction.winnerName }}
            </div>

            <div class="card-actions">
              <el-button @click="showHistory(auction)">竞价记录</el-button>
              <el-button v-if="auction.status==='ENDED'" type="success" plain @click="showSettlement(auction)">结算结果</el-button>
              <el-button v-if="canPay(auction)" type="primary" :loading="paymentOpeningId === auction.id" @click="openPayment(auction)">{{ paymentRecords[auction.id] ? '查看支付结果' : '去支付' }}</el-button>
              <el-button v-if="canBid(auction)" type="primary" :loading="bidOpeningId === auction.id" :disabled="Boolean(bidOpeningId)" @click="openBid(auction)">立即出价</el-button>
              <template v-if="isManager(auction)">
                <el-button v-if="auction.status === 'DRAFT'" type="primary" plain :disabled="Boolean(actionLoading)" @click="openEdit(auction)">编辑</el-button>
                <el-button
                  v-if="auction.status === 'DRAFT'"
                  type="success"
                  :disabled="Boolean(actionLoading)"
                  :loading="actionLoading === `${auction.id}-publish`"
                  @click="perform(auction, 'publish')"
                >发布</el-button>
                <el-button
                  v-if="currentUser.role === 'ADMIN' && ['UPCOMING', 'ONGOING'].includes(auction.status)"
                  type="warning"
                  plain
                  :disabled="Boolean(actionLoading)"
                  :loading="actionLoading === `${auction.id}-close`"
                  @click="perform(auction, 'close')"
                >结束</el-button>
                <el-button
                  v-if="['DRAFT', 'UPCOMING'].includes(auction.status)"
                  type="danger"
                  plain
                  :disabled="Boolean(actionLoading)"
                  :loading="actionLoading === `${auction.id}-cancel`"
                  @click="perform(auction, 'cancel')"
                >取消</el-button>
              </template>
            </div>
          </div>
        </article>
      </div>

      <el-empty v-else-if="!loading" description="暂时没有符合条件的拍卖">
        <el-button v-if="canCreate" type="primary" @click="openCreate">创建第一场拍卖</el-button>
      </el-empty>
    </div>

    <el-dialog
      v-model="formDialog"
      :title="editingId ? '编辑拍卖草稿' : '发起非遗拍卖'"
      width="min(680px, calc(100vw - 32px))"
      destroy-on-close
      @closed="formRef?.clearValidate()"
    >
      <el-alert title="保存后将生成草稿，确认作品、价格和时间无误后再发布。" type="info" :closable="false" show-icon />
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="auction-form">
        <el-form-item label="拍卖作品" prop="workId">
          <el-select v-model="form.workId" filterable placeholder="请选择正常展示中的作品" style="width: 100%" @change="useWorkDefaults">
            <el-option
              v-for="work in availableWorks"
              :key="work.id"
              :label="`${work.title}${Number(work.price) ? ` · 参考价 ${currency(work.price)}` : ''}`"
              :value="work.id"
            />
          </el-select>
          <span v-if="!availableWorks.length" class="form-tip">暂无可拍卖作品，请先完成艺人档案归档，并登记正常展示的作品。</span>
        </el-form-item>
        <el-form-item label="拍卖标题" prop="title">
          <el-input v-model="form.title" maxlength="150" show-word-limit placeholder="为本场拍卖起一个标题" />
        </el-form-item>
        <el-form-item label="拍卖说明">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="800" show-word-limit placeholder="介绍作品特色、工艺和拍卖说明" />
        </el-form-item>
        <el-row :gutter="18">
          <el-col :xs="24" :sm="12">
            <el-form-item label="起拍价（元）" prop="startPrice">
              <el-input-number v-model="form.startPrice" :min="0.01" :precision="2" :step="10" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="最低加价幅度（元）" prop="minIncrement">
              <el-input-number v-model="form.minIncrement" :min="0.01" :precision="2" :step="10" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="开始时间" prop="startTime">
              <el-date-picker
                v-model="form.startTime"
                type="datetime"
                placeholder="选择开始时间"
                format="YYYY-MM-DD HH:mm:ss"
                value-format="YYYY-MM-DDTHH:mm:ss"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="结束时间" prop="endTime">
              <el-date-picker
                v-model="form.endTime"
                type="datetime"
                placeholder="选择结束时间"
                format="YYYY-MM-DD HH:mm:ss"
                value-format="YYYY-MM-DDTHH:mm:ss"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="formDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存草稿</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="settlementVisible" title="拍卖结算结果（演示）" width="min(560px,92vw)"><template v-if="settlement"><el-alert title="系统按截止时的最高有效出价确定成交人和金额，本演示不扣款、不转账，不代表实际资金支付。" type="info" :closable="false" /><el-descriptions :column="1" border style="margin-top:16px"><el-descriptions-item label="拍卖编号">{{settlement.id}}</el-descriptions-item><el-descriptions-item label="作品">{{settlement.title}}</el-descriptions-item><el-descriptions-item label="结算状态">{{settlement.bidCount>0?'已成交':'流拍（无有效出价）'}}</el-descriptions-item><el-descriptions-item label="成交金额">{{settlement.bidCount>0?currency(settlement.currentPrice):'不产生交易金额'}}</el-descriptions-item><el-descriptions-item label="成交人">{{settlement.winnerName || '无'}}</el-descriptions-item><el-descriptions-item label="结算时间">{{formatTime(settlement.updatedAt)}}</el-descriptions-item></el-descriptions></template></el-dialog>
    <el-dialog v-model="paymentVisible" title="演示支付" class="payment-dialog" width="min(520px, calc(100vw - 24px))" top="5vh">
      <template v-if="paymentAuction">
        <el-alert title="支付宝沙箱返回后不验签；本地模拟不连接支付平台。两者均仅作演示记录。" type="warning" :closable="false" />
        <el-descriptions :column="1" border class="payment-order">
          <el-descriptions-item label="订单编号">DEMO-{{ paymentAuction.id }}</el-descriptions-item>
          <el-descriptions-item label="拍卖项目">{{ paymentAuction.title }}</el-descriptions-item>
          <el-descriptions-item label="卖家">{{ paymentAuction.sellerName }}</el-descriptions-item>
          <el-descriptions-item label="买家">{{ currentUser.realName }}</el-descriptions-item>
          <el-descriptions-item label="应付金额"><strong class="payment-amount">{{ currency(paymentAuction.currentPrice) }}</strong></el-descriptions-item>
        </el-descriptions>
        <div v-if="paymentRecord" class="payment-success">
          <el-alert title="演示支付已记录（未核验），仅保存在当前浏览器会话中" type="success" :closable="false" show-icon />
          <p>{{ paymentMethods[paymentRecord.method] }} · {{ paymentRecord.paidAt }}</p>
        </div>
        <div v-else class="payment-methods">
          <div class="payment-label">支付方式</div>
          <el-radio-group v-model="paymentMethod">
            <el-radio value="ALIPAY_SANDBOX">支付宝沙箱</el-radio>
            <el-radio value="LOCAL">本地模拟</el-radio>
          </el-radio-group>
        </div>
      </template>
      <template #footer>
        <el-button @click="paymentVisible = false">{{ paymentRecord ? '完成' : '取消' }}</el-button>
        <el-button v-if="paymentRecord" type="primary" plain @click="resetPaymentRecord">重新演示</el-button>
        <el-button v-if="!paymentRecord" type="primary" :loading="paymentOpeningId === paymentAuction?.id" :disabled="!paymentMethod" @click="confirmPayment">{{ paymentMethod === 'ALIPAY_SANDBOX' ? '跳转支付宝沙箱' : '确认演示支付' }}</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="bidDialog" title="参与竞价" width="min(460px, calc(100vw - 32px))">
      <div v-if="selectedAuction" class="bid-dialog-content">
        <div class="bid-work-title">{{ selectedAuction.title }}</div>
        <div class="bid-summary">
          <span>当前价 <b>{{ currency(selectedAuction.currentPrice) }}</b></span>
          <span>加价幅度 <b>{{ currency(selectedAuction.minIncrement) }}</b></span>
        </div>
        <el-form label-position="top" @submit.prevent="submitBid">
          <el-form-item label="您的出价">
            <el-input-number
              v-model="bidForm.amount"
              :min="bidMinimum"
              :step="Number(selectedAuction.minIncrement)"
              :precision="2"
              controls-position="right"
              style="width: 100%"
            />
          </el-form-item>
          <p class="bid-hint">最低有效出价：{{ currency(bidMinimum) }}。提交后出价不可撤回。</p>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="bidDialog = false">取消</el-button>
        <el-button type="primary" :loading="bidLoading" @click="submitBid">确认出价</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="historyDrawer" :size="'min(520px, 92vw)'" destroy-on-close>
      <template #header>
        <div class="drawer-title">
          <b>竞价记录</b>
          <span>{{ historyAuction?.title }}</span>
        </div>
      </template>
      <div v-loading="historyLoading">
        <div v-if="bids.length" class="bid-history">
          <div v-for="(bid, index) in bids" :key="bid.id" class="history-item">
            <span class="history-rank">{{ index === 0 ? '领先' : `#${bids.length - index}` }}</span>
            <div>
              <b>{{ bid.bidderName }}</b>
              <small>{{ formatTime(bid.bidTime) }}</small>
            </div>
            <strong>{{ currency(bid.bidAmount) }}</strong>
          </div>
        </div>
        <el-empty v-else-if="!historyLoading" description="暂无出价记录" />
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.auction-page{min-height:calc(100vh - 70px)}
.auction-hero{position:relative;overflow:hidden;display:flex;align-items:center;justify-content:space-between;gap:24px;padding:28px 32px;border-radius:18px;background:linear-gradient(120deg,#183f2c 0%,#286447 62%,#9a713d 140%);color:#fff;box-shadow:0 14px 34px rgba(24,63,44,.16)}
.auction-hero::after{content:"拍";position:absolute;right:150px;top:-52px;font-family:serif;font-size:190px;color:rgba(255,255,255,.055);transform:rotate(8deg)}
.eyebrow{font-size:11px;letter-spacing:3px;color:#e2bd7d}
.auction-hero h2{margin:8px 0 5px;font-size:28px}
.auction-hero p{margin:0;color:#dce9e1;line-height:1.7}
.create-button{position:relative;z-index:1;background:#bd8a49;border-color:#bd8a49;box-shadow:0 8px 20px rgba(19,40,28,.2)}
.filter-panel{margin:18px 0;padding:16px 18px;background:#fff;border:1px solid #e7ede9;border-radius:14px;box-shadow:0 6px 20px rgba(35,73,54,.055)}
.auction-toolbar{align-items:center;flex-wrap:wrap;margin:0}
.keyword-input{width:290px}.status-select{width:160px}.result-count{margin-left:auto;color:#7b8b82;font-size:13px}
.auction-list-wrap{min-height:260px}.auction-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:20px}
.auction-card{min-width:0;overflow:hidden;background:#fff;border:1px solid #e3ebe6;border-radius:16px;box-shadow:0 8px 28px rgba(35,73,54,.07);transition:transform .2s ease,box-shadow .2s ease}
.auction-card:hover{transform:translateY(-3px);box-shadow:0 14px 36px rgba(35,73,54,.12)}
.cover-wrap{position:relative;height:220px;overflow:hidden;background:#e5eee8}.cover-image{width:100%;height:100%;display:block}
.cover-placeholder{width:100%;height:100%;display:flex;align-items:center;justify-content:center;text-align:center;font-family:serif;font-size:30px;line-height:1.35;letter-spacing:8px;color:#d9bd8e;background:radial-gradient(circle at 70% 20%,rgba(198,153,84,.32),transparent 30%),linear-gradient(145deg,#244e38,#132f22)}
.status-tag{position:absolute;left:14px;top:14px;border:0}.countdown{position:absolute;left:0;right:0;bottom:0;padding:10px 14px;background:linear-gradient(transparent,rgba(8,25,16,.78));color:#fff;font-size:13px;text-align:right}
.countdown.live i{display:inline-block;width:7px;height:7px;margin-right:7px;border-radius:50%;background:#55d98b;box-shadow:0 0 0 4px rgba(85,217,139,.2);animation:pulse 1.4s infinite}
@keyframes pulse{50%{opacity:.45}}
.auction-card-body{padding:18px}.seller-row{display:flex;align-items:center;gap:8px;color:#6d7e74;font-size:13px}.seller-avatar{display:inline-grid;place-items:center;width:24px;height:24px;border-radius:50%;background:#e8f1eb;color:#316348;font-weight:700}
.auction-card h3{overflow:hidden;margin:12px 0 8px;color:#20392c;font-size:19px;line-height:1.4;white-space:nowrap;text-overflow:ellipsis}.description{display:-webkit-box;overflow:hidden;min-height:42px;margin:0;color:#738179;font-size:13px;line-height:1.65;-webkit-line-clamp:2;-webkit-box-orient:vertical}
.price-row{display:flex;align-items:flex-end;justify-content:space-between;margin:18px 0 12px;padding-bottom:14px;border-bottom:1px solid #edf1ee}.price-row small{display:block;margin-bottom:3px;color:#829087}.price-row strong{color:#a46728;font-size:23px}.bid-count{color:#7b8981;font-size:12px}.bid-count b{color:#355e47;font-size:18px}
.auction-facts{display:grid;gap:7px;color:#7b8981;font-size:12px}.auction-facts span:first-child{color:#53685c}.auction-facts b{color:#8c602f}.winner-line{margin-top:12px;padding:8px 10px;border-radius:8px;background:#f2f7f3;color:#346347;font-size:13px}
.card-actions{display:flex;flex-wrap:wrap;gap:8px;margin-top:17px}.card-actions :deep(.el-button){margin-left:0}.card-actions :deep(.el-button:first-child){margin-right:auto}
.auction-form{margin-top:20px}.form-tip{display:block;margin-top:5px;color:#9a6c35;font-size:12px}.bid-work-title{margin-bottom:15px;color:#233d2f;font-size:18px;font-weight:700}.bid-summary{display:flex;justify-content:space-between;gap:16px;margin-bottom:18px;padding:14px;border-radius:10px;background:#f3f7f4;color:#69786f;font-size:13px}.bid-summary b{display:block;margin-top:5px;color:#976129;font-size:16px}.bid-hint{margin:-6px 0 0;color:#8a958e;font-size:12px;line-height:1.6}
.payment-order{margin-top:16px}.payment-amount{color:#a46728;font-size:20px}.payment-methods{margin-top:22px}.payment-label{margin-bottom:10px;color:#405549;font-size:14px;font-weight:600}.payment-methods :deep(.el-radio-group){display:grid;gap:8px}.payment-methods :deep(.el-radio){height:auto;margin:0;padding:10px 12px;border:1px solid #e3ebe6;border-radius:6px}
.payment-success{margin-top:16px}.payment-success p{margin:10px 0 0;color:#69786f;font-size:13px}
.drawer-title{display:flex;flex-direction:column;gap:5px;color:#263d30}.drawer-title span{max-width:400px;overflow:hidden;color:#7d8a82;font-size:13px;white-space:nowrap;text-overflow:ellipsis}.bid-history{display:grid;gap:10px}.history-item{display:grid;grid-template-columns:48px 1fr auto;align-items:center;gap:12px;padding:14px;border:1px solid #e7ece9;border-radius:12px}.history-rank{padding:4px 6px;border-radius:12px;background:#edf4ef;color:#39704f;font-size:11px;text-align:center}.history-item:first-child{border-color:#dfc192;background:#fffbf4}.history-item:first-child .history-rank{background:#ba8747;color:#fff}.history-item div{display:flex;min-width:0;flex-direction:column;gap:4px}.history-item small{color:#89958e}.history-item>strong{color:#a46829}
@media(max-width:1200px){.auction-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}
@media(max-width:720px){.auction-page{min-height:auto;padding:16px}.auction-hero{align-items:flex-start;padding:22px;flex-direction:column}.auction-hero::after{right:-20px}.create-button{width:100%}.filter-panel{margin:14px 0}.auction-toolbar>*{width:100%!important}.result-count{margin-left:0}.auction-grid{grid-template-columns:1fr;gap:15px}.cover-wrap{height:210px}.card-actions :deep(.el-button){flex:1}.card-actions :deep(.el-button:first-child){margin-right:0}}
</style>

<style>
.payment-dialog .el-dialog__body{max-height:calc(100vh - 160px);overflow-y:auto}
</style>
