<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const list = ref([])
const loading = ref(false)
const dialog = ref(false)
const saving = ref(false)
const id = ref(null)
const form = reactive({ name: '', description: '' })
let user = {}
try { user = JSON.parse(localStorage.getItem('heritage_user') || '{}') } catch { user = {} }
const canManage = user.role === 'ADMIN'

async function load() {
  loading.value = true
  try {
    list.value = (await api.get('/categories')).data.data
  } catch {
    // 请求错误由 API 拦截器统一提示。
  } finally {
    loading.value = false
  }
}

function edit(row = {}) {
  id.value = row.id || null
  form.name = row.name || ''
  form.description = row.description || ''
  dialog.value = true
}

async function save() {
  if (!form.name.trim()) return ElMessage.warning('请输入类目名称')
  saving.value = true
  try {
    id.value
      ? await api.put(`/categories/${id.value}`, form)
      : await api.post('/categories', form)
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
    await ElMessageBox.confirm(`确定删除类目“${row.name}”吗？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await api.delete(`/categories/${row.id}`)
    ElMessage.success('删除成功')
    await load()
  } catch {
    // 请求错误由 API 拦截器统一提示。
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-title">
      <div><h2>技艺类目</h2><span class="muted">维护非遗手工技艺的分类标准</span></div>
      <el-button v-if="canManage" type="primary" @click="edit()">新增类目</el-button>
    </div>
    <div class="panel">
      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="id" label="编号" width="90" />
        <el-table-column prop="name" label="类目名称" width="220" />
        <el-table-column prop="description" label="说明" />
        <el-table-column v-if="canManage" label="操作" width="150">
          <template #default="scope">
            <el-button link @click="edit(scope.row)">编辑</el-button>
            <el-button link type="danger" @click="remove(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-dialog v-model="dialog" title="技艺类目" width="min(480px, 92vw)">
      <el-form label-position="top">
        <el-form-item label="类目名称"><el-input v-model="form.name" maxlength="80" show-word-limit /></el-form-item>
        <el-form-item label="类目说明"><el-input v-model="form.description" type="textarea" :rows="4" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
