import { createApp } from 'vue'
import { createPinia } from 'pinia'
import router from './router'
import App from './App.vue'
import './style.css'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import { changeConfirmLocale } from 'ant-design-vue/es/modal/locale'

// 静态 Modal.confirm 使用独立语言设置，需与全局中文配置保持一致。
changeConfirmLocale(zhCN.Modal)

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
