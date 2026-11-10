<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const list = ref([])
const loading = ref(false)
const dialog = ref(false)
const saving = ref(false)
const id = ref(null)
const form = reactive({ title: '', content: '', published: true })
const keyword=ref(''),published=ref(''),detail=ref(null),detailVisible=ref(false),detailLoading=ref(false)
let user = {}
try { user = JSON.parse(localStorage.getItem('heritage_user') || '{}') } catch { user = {} }
const canManage = user.role === 'ADMIN'

async function load() {
  loading.value = true
  try {
    list.value = (await api.get('/announcements',{params:{keyword:keyword.value,published:published.value===''?undefined:published.value}})).data.data
  } catch {
    // 请求错误由 API 拦截器统一提示。
  } finally {
    loading.value = false
  }
}

function edit(row = {}) {
  id.value = row.id || null
  Object.assign(form, { title: '', content: '', published: true, ...row })
  dialog.value = true
}

async function save() {
  if (!form.title.trim() || !form.content.trim()) return ElMessage.warning('请填写公告标题和内容')
  saving.value = true
  try {
    id.value
      ? await api.put(`/announcements/${id.value}`, form)
      : await api.post('/announcements', form)
    dialog.value = false
    ElMessage.success('保存成功')
    await load()
  } catch {
    // 请求错误由 API 拦截器统一提示。
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确定删除公告“${row.title}”吗？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await api.delete(`/announcements/${row.id}`)
    ElMessage.success('删除成功')
    await load()
  } catch {
    // 请求错误由 API 拦截器统一提示。
  }
}

onMounted(load)
async function view(row){detailLoading.value=true;try{detail.value=(await api.get(`/announcements/${row.id}`)).data.data;detailVisible.value=true}catch{/* interceptor */}finally{detailLoading.value=false}}
</script>

<template>
  <div class="page">
    <div class="page-title">
      <div><h2>通知公告</h2><span class="muted">发布档案申报、活动与系统通知</span></div>
      <el-button v-if="canManage" type="primary" @click="edit()">发布公告</el-button>
    </div>
    <div class="panel">
      <div class="toolbar"><el-input v-model="keyword" placeholder="搜索公告标题" clearable style="width:240px" @keyup.enter="load" /><el-select v-if="canManage" v-model="published" clearable placeholder="全部发布状态" style="width:160px" @change="load"><el-option label="已发布" :value="true" /><el-option label="草稿" :value="false" /></el-select><el-button @click="load">查询</el-button></div>
      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="title" label="标题" />
        <el-table-column prop="publisher" label="发布人" width="130" />
        <el-table-column prop="createdAt" label="发布时间" width="190" />
        <el-table-column label="状态" width="90">
          <template #default="scope"><el-tag :type="scope.row.published ? 'success' : 'info'">{{ scope.row.published ? '已发布' : '草稿' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="scope">
            <el-button type="primary" link :loading="detailLoading" @click="view(scope.row)">查看内容</el-button>
            <el-button v-if="canManage" link @click="edit(scope.row)">编辑</el-button>
            <el-button v-if="canManage" link type="danger" @click="remove(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-dialog v-model="detailVisible" :title="detail?.title || '公告详情'" width="min(680px,92vw)"><p class="muted">{{detail?.publisher}} · {{detail?.createdAt}}</p><div style="white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.8">{{detail?.content}}</div></el-dialog>
    <el-dialog v-model="dialog" title="通知公告" width="min(600px, 92vw)">
      <el-form label-position="top">
        <el-form-item label="公告标题"><el-input v-model="form.title" maxlength="150" show-word-limit /></el-form-item>
        <el-form-item label="公告内容"><el-input v-model="form.content" type="textarea" :rows="7" /></el-form-item>
        <el-form-item label="立即发布"><el-switch v-model="form.published" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
