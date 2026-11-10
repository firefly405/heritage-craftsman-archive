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
const artisans = ref([])
const dialog = ref(false)
const currentId = ref(null)
const loading = ref(false)
const saving = ref(false)
const deletingId = ref(null)
const form = reactive({ artisanId: null, title: '', description: '', imageUrl: '', price: 0, status: 'DISPLAY' })
const statusLabels = { DISPLAY: '正常展示', HIDDEN: '暂不展示', SOLD: '已售出' }

const canCreate = computed(() => ['ADMIN', 'ARTISAN'].includes(currentUser.role))
const availableArtisans = computed(() => {
  if (currentUser.role === 'ADMIN') return artisans.value
  return artisans.value.filter(artisan => String(artisan.userId) === String(currentUser.id))
})
const selectedArtisan = computed(() => artisans.value.find(artisan => artisan.id === form.artisanId))
const canChooseVisibility = computed(() => form.status !== 'SOLD' && selectedArtisan.value?.status === 'ARCHIVED')

async function load() {
  loading.value = true
  try {
    const [workResponse, artisanResponse] = await Promise.all([api.get('/works'), api.get('/artisans')])
    list.value = workResponse.data.data || []
    artisans.value = artisanResponse.data.data || []
  } catch {
    // 请求错误已由统一拦截器提示，保留当前页面数据。
  } finally {
    loading.value = false
  }
}

function artisanName(id) {
  return artisans.value.find(item => item.id === id)?.name || `档案 ${id}`
}

function canManage(row) {
  if (currentUser.role === 'ADMIN') return true
  const artisan = artisans.value.find(item => item.id === row.artisanId)
  return currentUser.role === 'ARTISAN' && currentUser.id != null && artisan?.userId != null && String(artisan.userId) === String(currentUser.id)
}

function edit(row = {}) {
  currentId.value = row.id || null
  Object.assign(form, {
    artisanId: availableArtisans.value[0]?.id || null,
    title: '',
    description: '',
    imageUrl: '',
    price: 0,
    status: 'DISPLAY',
    ...row
  })
  dialog.value = true
}

async function save() {
  if (!form.artisanId) {
    ElMessage.warning('请选择所属艺人档案')
    return
  }
  if (!String(form.title || '').trim()) {
    ElMessage.warning('请填写作品名称')
    return
  }
  saving.value = true
  try {
    if (currentId.value) await api.put(`/works/${currentId.value}`, form)
    else await api.post('/works', form)
    ElMessage.success('作品信息已保存')
    dialog.value = false
    await load()
  } catch {
    // 请求错误已由统一拦截器提示。
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确认删除作品“${row.title}”？`, '删除作品', {
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  deletingId.value = row.id
  try {
    await api.delete(`/works/${row.id}`)
    ElMessage.success('作品已删除')
    await load()
  } catch {
    // 请求错误已由统一拦截器提示。
  } finally {
    deletingId.value = null
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-title">
      <div><h2>作品展示与服务</h2><span class="muted">登记艺人代表作品和展示信息</span></div>
      <el-button v-if="canCreate" type="primary" @click="edit()">新增作品</el-button>
    </div>
    <div class="panel">
      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="title" label="作品名称" />
        <el-table-column label="所属艺人"><template #default="scope">{{ artisanName(scope.row.artisanId) }}</template></el-table-column>
        <el-table-column prop="description" label="作品简介" show-overflow-tooltip />
        <el-table-column prop="price" label="参考价格" width="110" />
        <el-table-column label="状态" width="110"><template #default="scope"><el-tag :type="scope.row.status === 'DISPLAY' ? 'success' : 'info'">{{ statusLabels[scope.row.status] || scope.row.status }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="scope">
            <template v-if="canManage(scope.row)">
              <el-button link :disabled="Boolean(deletingId)" @click="edit(scope.row)">编辑</el-button>
              <el-button link type="danger" :loading="deletingId === scope.row.id" :disabled="Boolean(deletingId)" @click="remove(scope.row)">删除</el-button>
            </template>
            <span v-else class="muted">仅查看</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog" title="作品信息" width="min(550px, calc(100vw - 24px))">
      <el-form label-position="top">
        <el-form-item label="所属艺人">
          <el-select v-model="form.artisanId" style="width:100%" placeholder="请选择艺人档案">
            <el-option v-for="artisan in availableArtisans" :key="artisan.id" :label="artisan.name" :value="artisan.id" />
          </el-select>
          <span v-if="!availableArtisans.length" class="muted">暂无可维护的艺人档案，请先创建档案。</span>
        </el-form-item>
        <el-form-item label="作品名称"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="作品简介"><el-input v-model="form.description" type="textarea" /></el-form-item>
        <el-form-item label="图片地址"><el-input v-model="form.imageUrl" /></el-form-item>
        <el-form-item label="参考价格"><el-input-number v-model="form.price" :min="0" :precision="2" style="width:100%" /></el-form-item>
        <el-form-item v-if="canChooseVisibility" label="展示状态">
          <el-select v-model="form.status" style="width:100%">
            <el-option label="正常展示（可参与拍卖）" value="DISPLAY" />
            <el-option label="暂不展示" value="HIDDEN" />
          </el-select>
        </el-form-item>
        <div v-else-if="selectedArtisan && selectedArtisan.status !== 'ARCHIVED'" class="muted">档案审核归档后，作品才可公开展示和参与拍卖。</div>
      </el-form>
      <template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>
