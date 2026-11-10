import { createApp } from 'vue'
import { createPinia } from 'pinia'
import {
  ElAlert,
  ElAside,
  ElButton,
  ElCard,
  ElCol,
  ElContainer,
  ElDatePicker,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElDrawer,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElHeader,
  ElImage,
  ElInput,
  ElInputNumber,
  ElLoading,
  ElMain,
  ElMenu,
  ElMenuItem,
  ElOption,
  ElPagination,
  ElRadio,
  ElRadioButton,
  ElRadioGroup,
  ElRow,
  ElSelect,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTag
} from 'element-plus'
import 'element-plus/dist/index.css'
import './style.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)
const elementComponents = [
  ElAlert, ElAside, ElButton, ElCard, ElCol, ElContainer, ElDatePicker, ElDescriptions, ElDescriptionsItem, ElDialog, ElDrawer,
  ElEmpty, ElForm, ElFormItem, ElHeader, ElImage, ElInput, ElInputNumber, ElMain, ElMenu,
  ElMenuItem, ElOption, ElPagination, ElRadio, ElRadioButton, ElRadioGroup, ElRow, ElSelect, ElSwitch, ElTable,
  ElTableColumn, ElTag
]

elementComponents.forEach(component => app.use(component))
app.use(ElLoading)
app.use(createPinia()).use(router).mount('#app')
