<script setup>
import {onMounted,ref} from 'vue'
import api from '../api'
const rows=ref([]),total=ref(0),page=ref(1),pageSize=ref(20),keyword=ref(''),loading=ref(false)
async function load(){loading.value=true;try{const {data}=await api.get('/operation-logs/page',{params:{page:page.value,pageSize:pageSize.value,keyword:keyword.value}});rows.value=data.data.records;total.value=data.data.total}catch{/* interceptor */}finally{loading.value=false}}
function search(){page.value=1;void load()}
onMounted(load)
</script>
<template><div class="page"><div class="page-title"><div><h2>拍卖日志</h2><span class="muted">只读查看创建、发布、竞价、取消、手动结束和到时自动结算的操作轨迹</span></div></div><div class="panel"><div class="toolbar"><el-input v-model="keyword" placeholder="操作类型、操作人或详情" clearable style="width:280px" @keyup.enter="search" /><el-button @click="search">查询</el-button></div><el-table :data="rows" v-loading="loading" stripe><el-table-column prop="createdAt" label="操作时间" width="185" /><el-table-column prop="operatorName" label="操作人" width="120" /><el-table-column prop="operationType" label="操作类型" width="150" /><el-table-column prop="targetId" label="拍卖编号" width="100" /><el-table-column prop="operationDetail" label="详情" /><el-table-column prop="resultStatus" label="结果" width="100" /></el-table><el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[20,50,100]" :total="total" layout="total,sizes,prev,pager,next" style="margin-top:16px" @current-change="load" @size-change="search" /></div></div></template>
