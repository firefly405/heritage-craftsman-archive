<script setup>
import {ref,onMounted} from 'vue'
import api from '../api'
const rows=ref([]),loading=ref(false),error=ref(''),studentId=ref(null)
async function load(){if(false&&!studentId.value){error.value='请输入学生编号';return}loading.value=true;error.value='';try{const r=await api.get('/artisans/quality',{params:{}});rows.value=r.data.data||[]}catch(e){rows.value=[];error.value=e.message||'加载失败'}finally{loading.value=false}}
onMounted(load)
function display(v){return Array.isArray(v)?(v.join('；')||'无'):typeof v==='boolean'?(v?'是':'否'):v}
</script>
<template><div style="padding:28px"><h2>档案完整度检查</h2><p>依据现有业务记录计算，规则和原因公开展示；结果仅供辅助判断。正式提交还需处于草稿或退回状态。</p><el-button :loading="loading" type="primary" @click="load">刷新检查</el-button><el-alert v-if="error" :title="error" :closable="false" style="margin:16px 0"/><el-table :data="rows" v-loading="loading" style="margin-top:18px"><el-table-column label="艺人" min-width="130"><template #default="{row}">{{display(row.name)}}</template></el-table-column><el-table-column label="完整度百分比" min-width="130"><template #default="{row}">{{display(row.score)}}</template></el-table-column><el-table-column label="待补充字段" min-width="130"><template #default="{row}">{{display(row.missing)}}</template></el-table-column><el-table-column label="一致性问题" min-width="130"><template #default="{row}">{{display(row.warnings)}}</template></el-table-column><el-table-column label="预检通过" min-width="130"><template #default="{row}">{{display(row.ready)}}</template></el-table-column></el-table><p v-if="!loading&&!rows.length">暂无结果，请检查筛选条件或补充业务数据。</p></div></template>
