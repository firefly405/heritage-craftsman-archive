<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

let currentUser = {}
try {
  currentUser = JSON.parse(localStorage.getItem('heritage_user') || '{}')
} catch {
  currentUser = {}
}

const list = ref([])
const categories = ref([])
const dialog = ref(false)
const reviewDialog = ref(false)
const keyword = ref('')
const status = ref('')
const currentId = ref(null)
const loading = ref(false)
const saving = ref(false)
const reviewing = ref(false)
const rowAction = ref('')
const form = reactive({})
const detail=ref(null),detailVisible=ref(false),detailLoading=ref(false),history=ref([])
const review = reactive({ approved: true, comment: '' })

const labels = { DRAFT: '草稿', SUBMITTED: '待审核', REJECTED: '已退回', ARCHIVED: '已归档' }
const mainlandMobile = /^1[3-9]\d{9}$/
const canCreate = computed(() => ['ADMIN', 'ARTISAN'].includes(currentUser.role))
const canReview = computed(() => currentUser.role === 'REVIEWER')

async function load() {
  loading.value = true
  try {
    const [artisanResponse, categoryResponse] = await Promise.all([
      api.get('/artisans', { params: { keyword: keyword.value, status: status.value } }),
      api.get('/categories')
    ])
    list.value = artisanResponse.data.data || []
    categories.value = categoryResponse.data.data || []
  } catch {
    // 请求错误已由统一拦截器提示，保留当前页面数据。
  } finally {
    loading.value = false
  }
}

function canManage(row) {
  return currentUser.role === 'ADMIN'
    || (currentUser.role === 'ARTISAN' && currentUser.id != null && row.userId != null && String(row.userId) === String(currentUser.id))
}

function canSubmit(row) {
  return canManage(row) && ['DRAFT', 'REJECTED'].includes(row.status)
}

function hasAction(row) {
  return canManage(row) || (canReview.value && row.status === 'SUBMITTED')
}
async function view(row){detailLoading.value=true;try{detail.value=(await api.get(`/artisans/${row.id}`)).data.data;history.value=[];if(canReview.value || canManage(row))history.value=(await api.get(`/artisans/${row.id}/reviews`)).data.data;detailVisible.value=true}catch{/* interceptor */}finally{detailLoading.value=false}}

function edit(row = {}) {
  Object.keys(form).forEach(key => delete form[key])
  Object.assign(form, {
    name: '',
    gender: '男',
    birthYear: 1990,
    phone: '',
    region: '',
    categoryId: null,
    experienceYears: 0,
    skillDescription: '',
    honors: '',
    inheritanceStory: '',
    avatarUrl: '',
    ...row
  })
  currentId.value = row.id || null
  dialog.value = true
}

async function save() {
  if (!String(form.name || '').trim()) {
    ElMessage.warning('请填写艺人姓名')
    return
  }
  form.phone = String(form.phone || '').trim()
  if (form.phone && !mainlandMobile.test(form.phone)) {
    ElMessage.warning('请输入正确的11位中国大陆手机号')
    return
  }
  saving.value = true
  try {
    if (currentId.value) await api.put(`/artisans/${currentId.value}`, form)
    else await api.post('/artisans', form)
    ElMessage.success('档案已保存')
    dialog.value = false
    await load()
  } catch {
    // 请求错误已由统一拦截器提示。
  } finally {
    saving.value = false
  }
}

async function submit(row) {
  rowAction.value = `${row.id}-submit`
  try {
    await api.post(`/artisans/${row.id}/submit`)
    ElMessage.success('已提交审核')
    await load()
  } catch {
    // 请求错误已由统一拦截器提示。
  } finally {
    rowAction.value = ''
  }
}

function openReview(row) {
  currentId.value = row.id
  review.approved = true
  review.comment = ''
  reviewDialog.value = true
}

async function doReview() {
  reviewing.value = true
  try {
    await api.post(`/artisans/${currentId.value}/review`, review)
    ElMessage.success('审核完成')
    reviewDialog.value = false
    await load()
  } catch {
    // 请求错误已由统一拦截器提示。
  } finally {
    reviewing.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确认删除 ${row.name} 的档案？`, '删除档案', {
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  rowAction.value = `${row.id}-delete`
  try {
    await api.delete(`/artisans/${row.id}`)
    ElMessage.success('档案已删除')
    await load()
  } catch {
    // 请求错误已由统一拦截器提示。
  } finally {
    rowAction.value = ''
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-title">
      <div><h2>艺人档案管理</h2><span class="muted">采集、维护并审核归档非遗艺人资料</span></div>
      <el-button v-if="canCreate" type="primary" @click="edit()">新增档案</el-button>
    </div>
    <div class="panel">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="姓名或地区" clearable style="width:240px" @keyup.enter="load" @clear="load" />
        <el-select v-model="status" placeholder="全部状态" clearable style="width:160px" @change="load">
          <el-option v-for="(value, key) in labels" :key="key" :label="value" :value="key" />
        </el-select>
        <el-button @click="load">查询</el-button>
      </div>
      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="id" label="编号" width="75" />
        <el-table-column prop="name" label="姓名" />
        <el-table-column prop="region" label="所在地区" />
        <el-table-column prop="experienceYears" label="从艺年限" width="100" />
        <el-table-column label="状态" width="100"><template #default="scope"><el-tag>{{ labels[scope.row.status] || scope.row.status }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="300">
          <template #default="scope">
            <el-button link type="primary" :loading="detailLoading" @click="view(scope.row)">查看详情</el-button>
            <template v-if="hasAction(scope.row)">
              <el-button v-if="canManage(scope.row) && (currentUser.role==='ADMIN' || ['DRAFT','REJECTED'].includes(scope.row.status))" link type="primary" :disabled="Boolean(rowAction)" @click="edit(scope.row)">编辑补正</el-button>
              <el-button v-if="canSubmit(scope.row)" link :loading="rowAction === `${scope.row.id}-submit`" :disabled="Boolean(rowAction)" @click="submit(scope.row)">提交</el-button>
              <el-button v-if="canReview && scope.row.status === 'SUBMITTED'" link type="success" :disabled="Boolean(rowAction)" @click="openReview(scope.row)">审核</el-button>
              <el-button v-if="canManage(scope.row) && ['DRAFT','REJECTED'].includes(scope.row.status)" link type="danger" :loading="rowAction === `${scope.row.id}-delete`" :disabled="Boolean(rowAction)" @click="remove(scope.row)">删除</el-button>
            </template>
            <span v-else class="muted">仅查看</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="detailVisible" title="艺人档案详情与审核记录" width="min(800px,94vw)">
      <template v-if="detail"><el-descriptions :column="2" border><el-descriptions-item label="艺人姓名">{{detail.name}}</el-descriptions-item><el-descriptions-item label="档案状态">{{labels[detail.status]}}</el-descriptions-item><el-descriptions-item label="地区">{{detail.region || '-'}}</el-descriptions-item><el-descriptions-item label="技艺类目">{{categories.find(c=>c.id===detail.categoryId)?.name || '-'}}</el-descriptions-item><el-descriptions-item label="性别">{{detail.gender || '-'}}</el-descriptions-item><el-descriptions-item label="出生年份">{{detail.birthYear || '-'}}</el-descriptions-item><el-descriptions-item label="从艺年限">{{detail.experienceYears ?? '-'}}</el-descriptions-item><el-descriptions-item label="联系电话">{{detail.phone || '未提供或已隐藏'}}</el-descriptions-item><el-descriptions-item label="技艺简介" :span="2"><div style="white-space:pre-wrap">{{detail.skillDescription || '-'}}</div></el-descriptions-item><el-descriptions-item label="传承故事" :span="2"><div style="white-space:pre-wrap">{{detail.inheritanceStory || '-'}}</div></el-descriptions-item><el-descriptions-item label="荣誉成果" :span="2">{{detail.honors || '-'}}</el-descriptions-item></el-descriptions><el-alert v-if="detail.status==='REJECTED' && detail.rejectReason" :title="`补正意见：${detail.rejectReason}`" type="warning" :closable="false" style="margin-top:12px" /><h3 v-if="canReview || canManage(detail)">审核流转记录</h3><el-table v-if="canReview || canManage(detail)" :data="history" border><el-table-column label="操作"><template #default="{row}">{{({SUBMIT:'提交审核',APPROVE:'审核通过并归档',REJECT:'退回补正'})[row.action] || row.action}}</template></el-table-column><el-table-column prop="comment" label="意见" /><el-table-column prop="createdAt" label="时间" width="180" /></el-table></template>
    </el-dialog>
    <el-dialog v-model="dialog" :title="currentId ? '编辑艺人档案' : '新增艺人档案'" width="min(720px, calc(100vw - 24px))">
      <el-form :model="form" label-width="90px">
        <el-row :gutter="18">
          <el-col :xs="24" :sm="12"><el-form-item label="姓名"><el-input v-model="form.name" /></el-form-item></el-col>
          <el-col :xs="24" :sm="12"><el-form-item label="性别"><el-select v-model="form.gender" style="width:100%"><el-option label="男" value="男" /><el-option label="女" value="女" /></el-select></el-form-item></el-col>
          <el-col :xs="24" :sm="12"><el-form-item label="出生年份"><el-input-number v-model="form.birthYear" :min="1900" :max="new Date().getFullYear()" /></el-form-item></el-col>
          <el-col :xs="24" :sm="12"><el-form-item label="手机号"><el-input v-model="form.phone" type="tel" maxlength="11" inputmode="numeric" autocomplete="tel" placeholder="11位中国大陆手机号" /></el-form-item></el-col>
          <el-col :xs="24" :sm="12"><el-form-item label="所在地区"><el-input v-model="form.region" /></el-form-item></el-col>
          <el-col :xs="24" :sm="12"><el-form-item label="技艺类目"><el-select v-model="form.categoryId" style="width:100%"><el-option v-for="category in categories" :key="category.id" :label="category.name" :value="category.id" /></el-select></el-form-item></el-col>
          <el-col :xs="24" :sm="12"><el-form-item label="从艺年限"><el-input-number v-model="form.experienceYears" :min="0" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="技艺简介"><el-input v-model="form.skillDescription" type="textarea" :rows="3" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="荣誉成果"><el-input v-model="form.honors" type="textarea" :rows="2" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="传承故事"><el-input v-model="form.inheritanceStory" type="textarea" :rows="3" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="reviewDialog" title="档案审核" width="min(500px, calc(100vw - 24px))">
      <el-radio-group v-model="review.approved"><el-radio :value="true">通过并归档</el-radio><el-radio :value="false">退回修改</el-radio></el-radio-group>
      <el-input v-model="review.comment" type="textarea" :rows="4" placeholder="填写审核意见" style="margin-top:16px" />
      <template #footer><el-button @click="reviewDialog = false">取消</el-button><el-button type="primary" :loading="reviewing" @click="doReview">确认审核</el-button></template>
    </el-dialog>
  </div>
</template>

