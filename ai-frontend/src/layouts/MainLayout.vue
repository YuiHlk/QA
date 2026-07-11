<template>
  <div class="layout-root">
    <!-- 侧边栏 -->
    <aside class="sidebar">
      <!-- Logo -->
      <div class="sidebar-brand">
        <div class="brand-icon">
          <svg viewBox="0 0 32 32" fill="none" width="28" height="28">
            <rect width="32" height="32" rx="8" fill="url(#brandGrad)" />
            <path d="M10 20V12l6 4-6 4z" fill="#fff" />
            <path d="M16 20V12l6 4-6 4z" fill="rgba(255,255,255,0.7)" />
            <defs>
              <linearGradient id="brandGrad" x1="0" y1="0" x2="32" y2="32">
                <stop stop-color="#6366f1" />
                <stop offset="1" stop-color="#8b5cf6" />
              </linearGradient>
            </defs>
          </svg>
        </div>
        <div class="brand-text">
          <span class="brand-name">AI 知识库</span>
          <span class="brand-sub">Enterprise RAG Platform</span>
        </div>
      </div>

      <!-- 导航菜单 -->
      <nav class="sidebar-nav">
        <router-link
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: isActive(item.path) }"
        >
          <span class="nav-icon">
            <el-icon :size="18"><component :is="item.icon" /></el-icon>
          </span>
          <span class="nav-label">{{ item.label }}</span>
          <span v-if="isActive(item.path)" class="nav-indicator" />
        </router-link>
      </nav>

      <!-- 底部信息 -->
      <div class="sidebar-footer">
        <div class="footer-status">
          <span class="status-dot" />
          <span class="status-text">System Online</span>
        </div>
      </div>
    </aside>

    <!-- 主内容区 -->
    <div class="main-area">
      <!-- 顶栏 -->
      <header class="topbar">
        <div class="topbar-left">
          <h1 class="page-title">{{ currentTitle }}</h1>
          <span class="page-breadcrumb">{{ currentSubtitle }}</span>
        </div>
        <div class="topbar-right">
          <span class="topbar-clock">{{ currentTime }}</span>
        </div>
      </header>

      <!-- 页面内容 -->
      <main class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import {
  Monitor, Document, FolderOpened, ChatDotSquare,
  DataAnalysis, Connection, Cpu
} from '@element-plus/icons-vue'

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
  '/dashboard': '平台运行状态与核心功能概览',
  '/prompts': '提示词模板版本管理与 A/B 测试',
  '/prompts/create': '创建新的提示词模板',
  '/knowledge': '文档解析、分块与向量化处理',
  '/chat': '基于 RAG 的检索增强生成问答',
  '/evaluation': 'LLM-as-Judge 自动化评测系统',
  '/ablation': '多维度参数消融对比实验',
  '/train': 'QLoRA 高效模型微调服务'
}

const activeMenu = computed(() => route.path)
const currentTitle = computed(() => route.meta.title || '首页')
const currentSubtitle = computed(() => {
  const exact = subtitles[route.path]
  if (exact) return exact
  if (route.path.includes('/prompts/')) return '提示词模板编辑'
  return ''
})

function isActive(path) {
  if (path === '/dashboard') return route.path === '/dashboard'
  return route.path.startsWith(path)
}

// 实时时钟
const currentTime = ref('')
let timer = null

function updateTime() {
  const now = new Date()
  const h = String(now.getHours()).padStart(2, '0')
  const m = String(now.getMinutes()).padStart(2, '0')
  currentTime.value = `${h}:${m}`
}

onMounted(() => {
  updateTime()
  timer = setInterval(updateTime, 30000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.layout-root {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

/* ========== 侧边栏 ========== */
.sidebar {
  width: var(--sidebar-width);
  background: var(--sidebar-bg);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  position: relative;
  z-index: 10;
}

.sidebar::after {
  content: '';
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: 1px;
  background: var(--sidebar-border);
}

/* Brand */
.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 16px;
  border-bottom: 1px solid var(--sidebar-border);
}

.brand-icon {
  flex-shrink: 0;
}

.brand-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.brand-name {
  font-size: 15px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 0.02em;
  white-space: nowrap;
}

.brand-sub {
  font-size: 10px;
  color: var(--sidebar-text);
  letter-spacing: 0.05em;
  text-transform: uppercase;
  white-space: nowrap;
}

/* Navigation */
.sidebar-nav {
  flex: 1;
  padding: 12px 0;
  overflow-y: auto;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  height: 42px;
  margin: 2px 8px;
  border-radius: var(--radius-md);
  color: var(--sidebar-text);
  text-decoration: none;
  font-size: 13.5px;
  font-weight: 500;
  position: relative;
  transition: all var(--transition-fast);
  cursor: pointer;
}

.nav-item:hover {
  background: var(--sidebar-item-hover);
  color: #e2e8f0;
}

.nav-item.active {
  background: var(--sidebar-item-active);
  color: #fff;
  font-weight: 600;
}

.nav-icon {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  opacity: 0.8;
}

.nav-item.active .nav-icon {
  opacity: 1;
}

.nav-label {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.nav-indicator {
  position: absolute;
  left: -8px;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 20px;
  background: var(--color-primary);
  border-radius: 0 3px 3px 0;
}

/* Footer */
.sidebar-footer {
  padding: 12px 16px;
  border-top: 1px solid var(--sidebar-border);
}

.footer-status {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--color-success);
  box-shadow: 0 0 6px rgba(16, 185, 129, 0.6);
  animation: pulse-glow 2s infinite;
}

.status-text {
  font-size: 11px;
  color: var(--sidebar-text);
  letter-spacing: 0.03em;
}

/* ========== 主区域 ========== */
.main-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--color-bg-page);
}

/* 顶栏 */
.topbar {
  height: var(--header-height);
  background: var(--color-bg-surface);
  border-bottom: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  flex-shrink: 0;
  z-index: 5;
}

.topbar-left {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.page-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text-primary);
  letter-spacing: 0.01em;
}

.page-breadcrumb {
  font-size: 12px;
  color: var(--color-text-tertiary);
  font-weight: 400;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.topbar-clock {
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--color-text-secondary);
  font-weight: 500;
}

/* 主内容 */
.main-content {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  min-height: 0;
}

/* ========== 页面过渡动画 ========== */
.page-fade-enter-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.page-fade-leave-active {
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.page-fade-enter-from {
  opacity: 0;
  transform: translateY(12px);
}

.page-fade-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
