<template>
  <div class="layout-root" :class="{ 'is-collapsed': isSidebarCollapsed }">
    <aside class="sidebar" aria-label="主导航">
      <div class="sidebar-brand">
        <div class="brand-mark" aria-hidden="true"><span>Q</span><i /></div>
        <div class="brand-text">
          <span class="brand-name">知识工作台</span>
          <span class="brand-sub">Signal Desk / RAG</span>
        </div>
        <button class="collapse-button" type="button" :aria-label="isSidebarCollapsed ? '展开侧栏' : '折叠侧栏'" @click="toggleSidebar">
          <el-icon :size="16"><Expand v-if="isSidebarCollapsed" /><Fold v-else /></el-icon>
        </button>
      </div>

      <div class="nav-kicker">WORKSPACE / 01</div>
      <nav class="sidebar-nav">
        <el-tooltip v-for="item in navItems" :key="item.path" :content="item.label" placement="right" :disabled="!isSidebarCollapsed">
          <router-link :to="item.path" class="nav-item" :class="{ active: isActive(item.path) }">
            <span class="nav-icon"><el-icon :size="18"><component :is="item.icon" /></el-icon></span>
            <span class="nav-label">{{ item.label }}</span>
            <span v-if="isActive(item.path)" class="nav-indicator" />
          </router-link>
        </el-tooltip>
      </nav>

      <div class="sidebar-footer">
        <span class="status-dot" />
        <div class="footer-copy"><strong>Workspace ready</strong><span>本地知识工程环境</span></div>
      </div>
    </aside>

    <transition name="drawer-fade">
      <button v-if="isDrawerOpen" class="drawer-backdrop" type="button" aria-label="关闭导航" @click="closeDrawer" />
    </transition>
    <aside class="mobile-drawer" :class="{ open: isDrawerOpen }" aria-label="移动端主导航">
      <div class="mobile-brand">
        <div class="brand-mark" aria-hidden="true"><span>Q</span><i /></div>
        <div class="brand-text"><span class="brand-name">知识工作台</span><span class="brand-sub">Signal Desk / RAG</span></div>
        <button type="button" class="icon-button" aria-label="关闭导航" @click="closeDrawer"><el-icon><Close /></el-icon></button>
      </div>
      <nav class="mobile-nav">
        <router-link v-for="item in navItems" :key="item.path" :to="item.path" class="nav-item" :class="{ active: isActive(item.path) }" @click="closeDrawer">
          <span class="nav-icon"><el-icon :size="18"><component :is="item.icon" /></el-icon></span>
          <span class="nav-label">{{ item.label }}</span><span v-if="isActive(item.path)" class="nav-indicator" />
        </router-link>
      </nav>
    </aside>

    <div class="main-area">
      <header class="topbar">
        <div class="topbar-left">
          <button ref="menuButton" class="menu-button" type="button" aria-label="打开导航" :aria-expanded="isDrawerOpen" @click="isDrawerOpen = true">
            <el-icon :size="19"><Menu /></el-icon>
          </button>
          <div class="page-heading"><span class="page-index">{{ currentIndex }}</span><div><h1 class="page-title">{{ currentTitle }}</h1><span class="page-breadcrumb">{{ currentSubtitle }}</span></div></div>
        </div>
        <div class="topbar-right"><span class="clock-label">LOCAL</span><span class="topbar-clock">{{ currentTime }}</span></div>
      </header>

      <main class="main-content" :class="{ 'workspace-content': route.meta.layout === 'workspace' }">
        <router-view v-slot="{ Component }"><transition name="page-fade" mode="out-in"><component :is="Component" /></transition></router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Monitor, Document, FolderOpened, ChatDotSquare, DataAnalysis, Connection, Cpu, Fold, Expand, Menu, Close } from '@element-plus/icons-vue'

const route = useRoute()
const navItems = [
  { path: '/dashboard', label: '首页概览', icon: Monitor },
  { path: '/prompts', label: '提示词管理', icon: Document },
  { path: '/knowledge', label: '知识库管理', icon: FolderOpened },
  { path: '/chat', label: 'AI 问答对话', icon: ChatDotSquare },
  { path: '/evaluation', label: '自动化评测', icon: DataAnalysis },
  { path: '/ablation', label: '消融实验', icon: Connection },
  { path: '/train', label: '模型微调', icon: Cpu }
]
const subtitles = {
  '/dashboard': '知识检索、评测与模型迭代总览', '/prompts': '提示词模板版本管理与 A/B 测试', '/prompts/create': '创建新的提示词模板',
  '/knowledge': '文档解析、分块与向量化处理', '/chat': '基于 RAG 的检索增强生成问答', '/evaluation': 'LLM-as-Judge 自动化评测系统',
  '/ablation': '多维度参数消融对比实验', '/train': 'QLoRA 高效模型微调服务'
}
const currentTitle = computed(() => route.meta.title || '首页')
const currentSubtitle = computed(() => subtitles[route.path] || (route.path.includes('/prompts/') ? '提示词模板编辑' : ''))
const currentIndex = computed(() => String(Math.max(1, navItems.findIndex(item => isActive(item.path)) + 1)).padStart(2, '0'))
const isSidebarCollapsed = ref(false)
const isDrawerOpen = ref(false)
const menuButton = ref(null)
const currentTime = ref('')
let timer
let mobileQuery
let compactQuery

function isActive(path) { return path === '/dashboard' ? route.path === path : route.path.startsWith(path) }
function toggleSidebar() { isSidebarCollapsed.value = !isSidebarCollapsed.value; localStorage.setItem('qa_sidebar_collapsed', String(isSidebarCollapsed.value)) }
function closeDrawer(restoreFocus = false) { isDrawerOpen.value = false; if (restoreFocus) nextTick(() => menuButton.value?.focus()) }
function updateTime() { currentTime.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false }) }
function syncViewport() { if (mobileQuery.matches) isDrawerOpen.value = false; else { const stored = localStorage.getItem('qa_sidebar_collapsed'); isSidebarCollapsed.value = stored === null ? compactQuery.matches : stored === 'true' } }
function handleKeydown(event) { if (event.key === 'Escape' && isDrawerOpen.value) closeDrawer(true) }

watch(() => route.path, () => closeDrawer())
onMounted(() => {
  mobileQuery = window.matchMedia('(max-width: 767px)')
  compactQuery = window.matchMedia('(min-width: 768px) and (max-width: 1279px)')
  syncViewport(); mobileQuery.addEventListener('change', syncViewport); compactQuery.addEventListener('change', syncViewport)
  document.addEventListener('keydown', handleKeydown); updateTime(); timer = setInterval(updateTime, 30000)
})
onUnmounted(() => {
  clearInterval(timer); mobileQuery?.removeEventListener('change', syncViewport); compactQuery?.removeEventListener('change', syncViewport); document.removeEventListener('keydown', handleKeydown)
})
</script>

<style scoped>
.layout-root { display: flex; height: 100vh; height: 100dvh; overflow: hidden; }
.sidebar { width: var(--sidebar-width-expanded); background: var(--sidebar-bg); color: var(--sidebar-text); display: flex; flex-direction: column; flex-shrink: 0; border-right: 1px solid var(--sidebar-border); transition: width var(--transition-base); overflow: hidden; z-index: 10; }
.is-collapsed .sidebar { width: var(--sidebar-width-collapsed); }
.sidebar-brand, .mobile-brand { min-height: var(--header-height); display: flex; align-items: center; gap: 11px; padding: 0 15px; border-bottom: 1px solid var(--sidebar-border); }
.brand-mark { width: 34px; height: 34px; flex: 0 0 34px; border: 1px solid rgba(133, 236, 203, .42); display: grid; place-items: center; position: relative; color: var(--color-primary-light); font: 700 15px var(--font-mono); }
.brand-mark i { position: absolute; width: 7px; height: 7px; right: -1px; bottom: -1px; background: var(--color-primary); border: 2px solid var(--sidebar-bg-solid); }
.brand-text { min-width: 0; display: flex; flex: 1; flex-direction: column; white-space: nowrap; transition: opacity var(--transition-fast); }
.brand-name { color: #f5f4ed; font-size: 14px; font-weight: 700; letter-spacing: .04em; }
.brand-sub, .nav-kicker { font: 500 9px var(--font-mono); color: #7e918b; letter-spacing: .11em; text-transform: uppercase; }
.collapse-button, .icon-button, .menu-button { border: 0; background: transparent; color: inherit; cursor: pointer; display: grid; place-items: center; }
.collapse-button { width: 28px; height: 28px; color: #9eb0aa; }
.is-collapsed .brand-text, .is-collapsed .collapse-button { opacity: 0; visibility: hidden; width: 0; }
.is-collapsed .sidebar-brand { padding-inline: 18px; }
.nav-kicker { padding: 20px 20px 8px; white-space: nowrap; }
.is-collapsed .nav-kicker { opacity: 0; }
.sidebar-nav, .mobile-nav { flex: 1; overflow-y: auto; padding: 5px 9px 12px; }
.nav-item { height: 44px; display: flex; align-items: center; gap: 11px; padding: 0 12px; margin: 2px 0; position: relative; color: var(--sidebar-text); text-decoration: none; font-size: 13px; font-weight: 550; white-space: nowrap; transition: color var(--transition-fast), background-color var(--transition-fast); }
.nav-item:hover, .nav-item:focus-visible { color: #f4f6f2; background: var(--sidebar-item-hover); }
.nav-item.active { color: #fff; background: var(--sidebar-item-active); }
.nav-icon { width: 22px; flex: 0 0 22px; display: grid; place-items: center; }
.nav-label { overflow: hidden; text-overflow: ellipsis; }
.is-collapsed .nav-item { justify-content: center; padding-inline: 0; }
.is-collapsed .nav-label { width: 0; opacity: 0; }
.nav-indicator { position: absolute; left: 0; width: 3px; height: 22px; background: var(--color-primary); }
.sidebar-footer { min-height: 62px; padding: 12px 18px; border-top: 1px solid var(--sidebar-border); display: flex; align-items: center; gap: 9px; white-space: nowrap; }
.status-dot { width: 7px; height: 7px; background: var(--color-primary); flex: 0 0 7px; }
.footer-copy { display: flex; flex-direction: column; color: #91a39d; font-size: 10px; }
.footer-copy strong { color: #dce5e1; font: 500 10px var(--font-mono); text-transform: uppercase; letter-spacing: .05em; }
.is-collapsed .footer-copy { display: none; }
.main-area { flex: 1; min-width: 0; min-height: 0; display: flex; flex-direction: column; background: var(--color-bg-page); }
.topbar { height: var(--header-height); flex: 0 0 var(--header-height); display: flex; align-items: center; justify-content: space-between; padding: 0 28px; background: rgba(252, 251, 247, .92); border-bottom: 1px solid var(--color-border); }
.topbar-left, .page-heading, .topbar-right { display: flex; align-items: center; }
.page-heading { gap: 12px; }
.page-index { font: 600 11px var(--font-mono); color: var(--color-primary-dark); border-right: 1px solid var(--color-border); padding-right: 12px; }
.page-title { font-size: 15px; line-height: 1.25; color: var(--color-text-primary); letter-spacing: .02em; }
.page-breadcrumb { display: block; margin-top: 2px; font-size: 11px; color: var(--color-text-tertiary); }
.topbar-right { gap: 7px; font-family: var(--font-mono); }
.clock-label { font-size: 9px; color: var(--color-text-tertiary); letter-spacing: .12em; }
.topbar-clock { font-size: 12px; color: var(--color-text-secondary); }
.menu-button { display: none; width: 36px; height: 36px; margin-right: 8px; color: var(--color-text-primary); }
.main-content { flex: 1; min-width: 0; min-height: 0; overflow: auto; padding: var(--content-padding-y) var(--content-padding-x); }
.main-content.workspace-content { overflow: hidden; display: flex; }
.main-content.workspace-content > :deep(*) { flex: 1; min-height: 0; }
.page-fade-enter-active, .page-fade-leave-active { transition: opacity var(--transition-base), transform var(--transition-base); }
.page-fade-enter-from { opacity: 0; transform: translateY(5px); }
.page-fade-leave-to { opacity: 0; transform: translateY(-3px); }
.mobile-drawer, .drawer-backdrop { display: none; }

@media (max-width: 767px) {
  .sidebar { display: none; }
  .topbar { height: var(--header-height-mobile); flex-basis: var(--header-height-mobile); padding: 0 14px; }
  .menu-button { display: grid; }
  .page-breadcrumb, .clock-label { display: none; }
  .main-content { padding: 14px; }
  .main-content.workspace-content { padding: 10px; }
  .drawer-backdrop { display: block; position: fixed; inset: 0; border: 0; background: rgba(15, 24, 22, .56); z-index: 49; }
  .mobile-drawer { display: flex; position: fixed; inset: 0 auto 0 0; width: min(84vw, 320px); background: var(--sidebar-bg); color: var(--sidebar-text); z-index: 50; flex-direction: column; transform: translateX(-101%); transition: transform var(--transition-base); padding-bottom: env(safe-area-inset-bottom); }
  .mobile-drawer.open { transform: translateX(0); }
  .mobile-brand { min-height: 64px; }
  .icon-button { width: 34px; height: 34px; }
  .mobile-nav { padding-top: 14px; }
  .drawer-fade-enter-active, .drawer-fade-leave-active { transition: opacity var(--transition-fast); }
  .drawer-fade-enter-from, .drawer-fade-leave-to { opacity: 0; }
}
@media (max-width: 420px) { .topbar-right { display: none; } }
</style>
