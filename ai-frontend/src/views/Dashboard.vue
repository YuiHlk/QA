<template>
  <div class="dashboard">
    <!-- Hero Banner -->
    <div class="hero-banner stagger-1">
      <div class="hero-content">
        <div class="hero-badge">Enterprise AI Platform</div>
        <h2 class="hero-title">AI 知识库平台</h2>
        <p class="hero-desc">
          基于 RAG 检索增强生成的企业级智能问答与自动化评测系统，支持提示词工程化管理、
          文档解析与向量检索、LLM-as-Judge 多维度评测、消融实验对比分析以及 QLoRA 模型微调。
        </p>
        <div class="hero-actions">
          <router-link to="/chat" class="hero-btn hero-btn-primary">
            <el-icon><ChatDotSquare /></el-icon>
            开始对话
          </router-link>
          <router-link to="/knowledge" class="hero-btn hero-btn-secondary">
            <el-icon><FolderOpened /></el-icon>
            管理知识库
          </router-link>
        </div>
      </div>
      <div class="hero-visual">
        <div class="hero-orb orb-1" />
        <div class="hero-orb orb-2" />
        <div class="hero-orb orb-3" />
        <div class="hero-grid">
          <div class="grid-cell" v-for="i in 16" :key="i" :style="{ animationDelay: `${i * 0.05}s` }" />
        </div>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-row stagger-2">
      <div class="stat-card" v-for="(card, idx) in statCards" :key="idx"
        :class="`stat-card-${idx}`"
        @click="$router.push(card.path)"
      >
        <div class="stat-icon" :style="{ background: card.gradient }">
          <el-icon :size="22" color="#fff"><component :is="card.icon" /></el-icon>
        </div>
        <div class="stat-body">
          <span class="stat-label">{{ card.label }}</span>
          <span class="stat-desc">{{ card.desc }}</span>
        </div>
        <div class="stat-arrow">
          <el-icon :size="16"><ArrowRight /></el-icon>
        </div>
      </div>
    </div>

    <!-- 功能模块展示 -->
    <div class="features-section stagger-3">
      <h3 class="section-heading">核心能力</h3>
      <div class="features-grid">
        <div class="feature-card" v-for="(feat, idx) in features" :key="idx"
          :style="{ animationDelay: `${0.15 + idx * 0.08}s` }"
        >
          <div class="feature-icon-wrap" :style="{ background: feat.bg, color: feat.color }">
            <el-icon :size="24"><component :is="feat.icon" /></el-icon>
          </div>
          <div class="feature-body">
            <h4 class="feature-name">{{ feat.title }}</h4>
            <p class="feature-desc">{{ feat.desc }}</p>
          </div>
          <ul class="feature-tags">
            <li v-for="tag in feat.tags" :key="tag" class="feature-tag">{{ tag }}</li>
          </ul>
        </div>
      </div>
    </div>

    <!-- 底部状态栏 -->
    <div class="status-bar stagger-4">
      <div class="status-item">
        <span class="status-val">RAG 2.0</span>
        <span class="status-lbl">检索引擎</span>
      </div>
      <div class="status-divider" />
      <div class="status-item">
        <span class="status-val">ChromaDB</span>
        <span class="status-lbl">向量数据库</span>
      </div>
      <div class="status-divider" />
      <div class="status-item">
        <span class="status-val">QLoRA</span>
        <span class="status-lbl">微调方案</span>
      </div>
      <div class="status-divider" />
      <div class="status-item">
        <span class="status-val">LLM Judge</span>
        <span class="status-lbl">评测体系</span>
      </div>
      <div class="status-divider" />
      <div class="status-item">
        <span class="status-val">Multi-Model</span>
        <span class="status-lbl">模型支持</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import {
  Document, FolderOpened, DataAnalysis, Connection,
  ChatDotSquare, ArrowRight, Cpu, Promotion,
  EditPen, Search, TrendCharts, Setting
} from '@element-plus/icons-vue'

const statCards = [
  {
    label: '提示词模板',
    desc: '版本管理与 A/B 测试',
    icon: Document,
    gradient: 'linear-gradient(135deg, #6366f1, #8b5cf6)',
    path: '/prompts'
  },
  {
    label: '知识库文档',
    desc: '文档解析与向量化检索',
    icon: FolderOpened,
    gradient: 'linear-gradient(135deg, #10b981, #34d399)',
    path: '/knowledge'
  },
  {
    label: '自动化评测',
    desc: 'LLM-as-Judge 多维评分',
    icon: DataAnalysis,
    gradient: 'linear-gradient(135deg, #f59e0b, #fbbf24)',
    path: '/evaluation'
  },
  {
    label: '消融实验',
    desc: '笛卡尔积参数组合对比',
    icon: Connection,
    gradient: 'linear-gradient(135deg, #ef4444, #f87171)',
    path: '/ablation'
  }
]

const features = [
  {
    icon: EditPen,
    title: '提示词工程系统化',
    desc: '场景级模板版本管理，支持 Few-Shot 示例、温度与 Top-P 调控、结构化约束与幻觉抑制配置。',
    tags: ['版本管理', 'A/B 测试', '参数调优'],
    bg: '#eef2ff',
    color: '#6366f1'
  },
  {
    icon: Search,
    title: 'RAG 检索增强生成',
    desc: 'PDF / MD / TXT 文档自动解析，可配置分块策略与重叠窗口，向量化入库后支持 Top-K 语义检索与重排序。',
    tags: ['文档解析', '分块策略', '向量检索'],
    bg: '#ecfdf5',
    color: '#10b981'
  },
  {
    icon: TrendCharts,
    title: 'LLM-as-Judge 自动化评测',
    desc: '构建标准评测问题集，按相关性、上下文忠实度、幻觉检测、检索精准/召回率五大维度自动评分。',
    tags: ['相关性', '忠实度', '幻觉检测'],
    bg: '#fffbeb',
    color: '#f59e0b'
  },
  {
    icon: Connection,
    title: '消融实验系统',
    desc: '多维度变量笛卡尔积自动组合，每组独立运行完整评测流程，生成可对比的指标报告与最优参数推荐。',
    tags: ['参数对比', '笛卡尔积', '指标报告'],
    bg: '#fef2f2',
    color: '#ef4444'
  },
  {
    icon: Cpu,
    title: 'Java + Python 微调联动',
    desc: 'Java 管理任务编排与状态跟踪，Python 执行 QLoRA 高效微调，支持 Qwen2 / Llama3 / Mistral 系列模型。',
    tags: ['QLoRA', '多模型', '端到端'],
    bg: '#eff6ff',
    color: '#3b82f6'
  },
  {
    icon: Setting,
    title: '全链路 DevOps 集成',
    desc: 'Docker Compose 一键部署，Nginx 反向代理，Vite 前端热更新，Spring Boot 后端 + FastAPI 微服务协作。',
    tags: ['Docker', 'Nginx', 'CI/CD'],
    bg: '#f5f3ff',
    color: '#8b5cf6'
  }
]
</script>

<style scoped>
.dashboard {
  animation: fadeIn 0.4s ease;
  max-width: 1200px;
}

/* ========== Hero Banner ========== */
.hero-banner {
  background: linear-gradient(135deg, #0f172a 0%, #1e1b4b 40%, #312e81 100%);
  border-radius: var(--radius-xl);
  padding: 40px 48px;
  display: flex;
  align-items: center;
  gap: 48px;
  position: relative;
  overflow: hidden;
  margin-bottom: 24px;
}

.hero-content {
  flex: 1;
  position: relative;
  z-index: 2;
}

.hero-badge {
  display: inline-block;
  padding: 4px 12px;
  background: rgba(99, 102, 241, 0.3);
  border: 1px solid rgba(129, 140, 248, 0.4);
  border-radius: 20px;
  font-size: 11px;
  font-weight: 600;
  color: #a5b4fc;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  margin-bottom: 16px;
}

.hero-title {
  font-size: 32px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 0.02em;
  margin-bottom: 12px;
  line-height: 1.2;
}

.hero-desc {
  font-size: 14px;
  color: #94a3b8;
  line-height: 1.7;
  max-width: 520px;
  margin-bottom: 24px;
}

.hero-actions {
  display: flex;
  gap: 12px;
}

.hero-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border-radius: var(--radius-md);
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;
  transition: all var(--transition-fast);
  cursor: pointer;
}

.hero-btn-primary {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  box-shadow: 0 4px 16px rgba(99, 102, 241, 0.4);
}

.hero-btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 24px rgba(99, 102, 241, 0.55);
}

.hero-btn-secondary {
  background: rgba(255, 255, 255, 0.08);
  color: #e2e8f0;
  border: 1px solid rgba(255, 255, 255, 0.12);
}

.hero-btn-secondary:hover {
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
}

/* Hero Visual */
.hero-visual {
  flex-shrink: 0;
  width: 200px;
  height: 200px;
  position: relative;
}

.hero-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
  opacity: 0.5;
  animation: gentle-float 6s ease-in-out infinite;
}

.orb-1 {
  width: 140px;
  height: 140px;
  background: #6366f1;
  top: 10px;
  left: 20px;
}

.orb-2 {
  width: 100px;
  height: 100px;
  background: #8b5cf6;
  bottom: 10px;
  right: 10px;
  animation-delay: -2s;
}

.orb-3 {
  width: 80px;
  height: 80px;
  background: #a78bfa;
  top: 60px;
  right: 40px;
  animation-delay: -4s;
}

.hero-grid {
  position: absolute;
  inset: 0;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  grid-template-rows: repeat(4, 1fr);
  gap: 1px;
}

.grid-cell {
  background: rgba(255, 255, 255, 0.03);
  border-radius: 2px;
  animation: fadeIn 0.3s ease both;
}

/* ========== Stats Row ========== */
.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  cursor: pointer;
  transition: all var(--transition-base);
  position: relative;
  overflow: hidden;
}

.stat-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-lg);
  border-color: var(--color-primary-lighter);
}

.stat-card:hover .stat-arrow {
  opacity: 1;
  transform: translateX(0);
}

.stat-icon {
  width: 42px;
  height: 42px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stat-label {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.stat-desc {
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.stat-arrow {
  position: absolute;
  right: 16px;
  top: 50%;
  transform: translateY(-50%) translateX(-6px);
  opacity: 0;
  color: var(--color-text-tertiary);
  transition: all var(--transition-base);
}

/* ========== Features ========== */
.features-section {
  margin-bottom: 24px;
}

.section-heading {
  font-size: 18px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin-bottom: 16px;
  letter-spacing: 0.01em;
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.feature-card {
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 20px;
  transition: all var(--transition-base);
  animation: fadeInUp 0.5s var(--ease-spring) both;
}

.feature-card:hover {
  box-shadow: var(--shadow-md);
  border-color: var(--color-primary-lighter);
}

.feature-icon-wrap {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 14px;
}

.feature-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 8px;
}

.feature-desc {
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.6;
  margin-bottom: 14px;
}

.feature-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  list-style: none;
}

.feature-tag {
  font-size: 11px;
  padding: 3px 10px;
  background: var(--color-bg-muted);
  color: var(--color-text-secondary);
  border-radius: 20px;
  font-weight: 500;
}

/* ========== Status Bar ========== */
.status-bar {
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 16px 24px;
  display: flex;
  align-items: center;
  gap: 0;
}

.status-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.status-val {
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 600;
  color: var(--color-primary);
}

.status-lbl {
  font-size: 11px;
  color: var(--color-text-tertiary);
  font-weight: 500;
}

.status-divider {
  width: 1px;
  height: 32px;
  background: var(--color-border);
  flex-shrink: 0;
}

/* ========== Responsive ========== */
@media (max-width: 1200px) {
  .features-grid { grid-template-columns: repeat(2, 1fr); }
  .stats-row { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 900px) {
  .hero-banner { flex-direction: column; text-align: center; padding: 32px 24px; }
  .hero-desc { max-width: 100%; }
  .hero-actions { justify-content: center; }
  .hero-visual { display: none; }
}
</style>
