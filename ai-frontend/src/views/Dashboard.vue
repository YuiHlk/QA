<template>
  <div class="dashboard">
    <section class="hero-board stagger-1">
      <div class="hero-copy">
        <span class="eyebrow">KNOWLEDGE OPERATIONS / 01</span>
        <h2>把企业知识转化为<br><em>可检索、可评测、可迭代</em>的 AI 能力</h2>
        <p>从文档向量化、RAG 问答到 LLM-as-Judge 自动化评测与 QLoRA 微调，在同一工作台完成知识工程闭环。</p>
        <div class="hero-actions">
          <router-link to="/chat" class="action primary"><el-icon><ChatDotSquare /></el-icon>开始对话<el-icon class="action-arrow"><ArrowRight /></el-icon></router-link>
          <router-link to="/knowledge" class="action secondary"><el-icon><FolderOpened /></el-icon>管理知识库</router-link>
        </div>
      </div>
      <div class="pipeline-panel" aria-label="知识处理管线示意图">
        <div class="panel-head"><span>KNOWLEDGE PIPELINE</span><span>READY</span></div>
        <div class="pipeline">
          <div v-for="(step, index) in pipelineSteps" :key="step.label" class="pipeline-step">
            <span class="step-index">{{ String(index + 1).padStart(2, '0') }}</span>
            <el-icon :size="18"><component :is="step.icon" /></el-icon>
            <span>{{ step.label }}</span>
          </div>
        </div>
        <div class="signal-line"><i v-for="i in 18" :key="i" :style="{ height: `${8 + (i * 7) % 24}px` }" /></div>
      </div>
    </section>

    <section class="quick-section stagger-2">
      <div class="section-header"><div><span class="section-kicker">DIRECT ACCESS / 02</span><h3>快速进入工作流</h3></div><p>围绕知识生产链路组织的核心模块</p></div>
      <div class="quick-grid">
        <router-link v-for="(card, idx) in quickActions" :key="card.path" :to="card.path" class="quick-card">
          <span class="quick-index">0{{ idx + 1 }}</span>
          <div class="quick-icon"><el-icon :size="20"><component :is="card.icon" /></el-icon></div>
          <div class="quick-copy"><strong>{{ card.label }}</strong><span>{{ card.desc }}</span></div>
          <el-icon class="quick-arrow"><ArrowRight /></el-icon>
        </router-link>
      </div>
    </section>

    <section class="capabilities stagger-3">
      <div class="section-header"><div><span class="section-kicker">SYSTEM CAPABILITIES / 03</span><h3>核心能力矩阵</h3></div><p>从提示词工程到模型训练的端到端支持</p></div>
      <div class="capability-grid">
        <article v-for="(feat, idx) in features" :key="feat.title" class="capability-card">
          <div class="capability-top"><span class="capability-index">{{ String(idx + 1).padStart(2, '0') }}</span><div class="capability-icon"><el-icon :size="20"><component :is="feat.icon" /></el-icon></div></div>
          <h4>{{ feat.title }}</h4><p>{{ feat.desc }}</p>
          <ul><li v-for="tag in feat.tags" :key="tag">{{ tag }}</li></ul>
        </article>
      </div>
    </section>

    <section class="baseline stagger-4">
      <div class="baseline-title"><span>TECH BASELINE / 04</span><strong>平台能力栈</strong></div>
      <div class="baseline-items"><div v-for="item in baseline" :key="item.value"><strong>{{ item.value }}</strong><span>{{ item.label }}</span></div></div>
    </section>
  </div>
</template>

<script setup>
import { Document, FolderOpened, DataAnalysis, Connection, ChatDotSquare, ArrowRight, Cpu, EditPen, Search, TrendCharts, Setting, Files, Scissor, MagicStick, Check } from '@element-plus/icons-vue'
const pipelineSteps = [{ label: '文档', icon: Files }, { label: '分块', icon: Scissor }, { label: '检索', icon: Search }, { label: '生成', icon: MagicStick }, { label: '评测', icon: Check }]
const quickActions = [
  { label: '提示词模板', desc: '版本管理与 A/B 测试', icon: Document, path: '/prompts' },
  { label: '知识库文档', desc: '文档解析与向量化检索', icon: FolderOpened, path: '/knowledge' },
  { label: '自动化评测', desc: 'LLM-as-Judge 多维评分', icon: DataAnalysis, path: '/evaluation' },
  { label: '消融实验', desc: '笛卡尔积参数组合对比', icon: Connection, path: '/ablation' }
]
const features = [
  { icon: EditPen, title: '提示词工程系统化', desc: '场景级模板版本管理，支持 Few-Shot 示例、温度与 Top-P 调控、结构化约束与幻觉抑制配置。', tags: ['版本管理', 'A/B 测试', '参数调优'] },
  { icon: Search, title: 'RAG 检索增强生成', desc: 'PDF / MD / TXT 文档自动解析，可配置分块策略与重叠窗口，支持 Top-K 语义检索与重排序。', tags: ['文档解析', '分块策略', '向量检索'] },
  { icon: TrendCharts, title: 'LLM-as-Judge 自动化评测', desc: '构建标准评测问题集，按相关性、忠实度、幻觉检测、检索精准与召回率自动评分。', tags: ['相关性', '忠实度', '幻觉检测'] },
  { icon: Connection, title: '消融实验系统', desc: '多维度变量笛卡尔积自动组合，每组独立运行完整评测流程，生成可对比的指标报告。', tags: ['参数对比', '笛卡尔积', '指标报告'] },
  { icon: Cpu, title: 'Java + Python 微调联动', desc: 'Java 管理任务编排与状态跟踪，Python 执行 QLoRA 微调，支持主流开源模型系列。', tags: ['QLoRA', '多模型', '端到端'] },
  { icon: Setting, title: '全链路 DevOps 集成', desc: 'Docker Compose 部署，Nginx 反向代理，Spring Boot 后端与 FastAPI 微服务协作。', tags: ['Docker', 'Nginx', 'CI/CD'] }
]
const baseline = [{ value: 'RAG 2.0', label: '检索引擎' }, { value: 'ChromaDB', label: '向量数据库' }, { value: 'QLoRA', label: '微调方案' }, { value: 'LLM Judge', label: '评测体系' }, { value: 'Multi-Model', label: '模型支持' }]
</script>

<style scoped>
.dashboard { width: min(100%, 1440px); margin: 0 auto; padding-bottom: 12px; }
.hero-board { min-height: 390px; display: grid; grid-template-columns: minmax(0, 1.15fr) minmax(390px, .85fr); background: #17221f; color: #f6f5ef; border: 1px solid #273632; overflow: hidden; position: relative; }
.hero-board::before { content: ''; position: absolute; inset: 0; opacity: .16; background-image: linear-gradient(rgba(173,213,201,.16) 1px,transparent 1px),linear-gradient(90deg,rgba(173,213,201,.16) 1px,transparent 1px); background-size: 28px 28px; mask-image: linear-gradient(90deg,transparent 20%,black); }
.hero-copy { padding: clamp(34px,5vw,68px); position: relative; z-index: 1; }
.eyebrow,.section-kicker,.panel-head,.baseline-title span { font: 600 10px var(--font-mono); letter-spacing: .13em; }
.eyebrow { color: #74cfb2; }
.hero-copy h2 { margin: 20px 0 18px; max-width: 760px; font-size: clamp(31px,3.4vw,52px); line-height: 1.13; letter-spacing: -.035em; font-weight: 650; }
.hero-copy h2 em { color: #77d0b3; font-style: normal; }
.hero-copy p { max-width: 650px; color: #aebdb8; line-height: 1.85; font-size: 14px; }
.hero-actions { display: flex; gap: 10px; margin-top: 30px; }
.action { min-height: 42px; display: inline-flex; align-items: center; gap: 9px; padding: 0 17px; color: inherit; text-decoration: none; border: 1px solid rgba(255,255,255,.17); font-weight: 650; font-size: 13px; transition: background-color var(--transition-fast),border-color var(--transition-fast),transform var(--transition-fast); }
.action.primary { color: #10201b; background: #77d0b3; border-color: #77d0b3; }
.action:hover { transform: translateY(-1px); border-color: #77d0b3; }
.action-arrow { margin-left: 7px; }
.pipeline-panel { margin: 34px 34px 34px 0; padding: 24px; align-self: stretch; background: rgba(7,15,13,.45); border: 1px solid rgba(153,202,187,.23); position: relative; z-index: 1; display: flex; flex-direction: column; }
.panel-head { display: flex; justify-content: space-between; color: #7c918a; padding-bottom: 16px; border-bottom: 1px solid rgba(153,202,187,.16); }
.panel-head span:last-child { color: #77d0b3; }
.pipeline { flex: 1; padding: 22px 0; display: grid; align-content: center; }
.pipeline-step { min-height: 43px; display: grid; grid-template-columns: 35px 28px 1fr; align-items: center; color: #d7e0dd; border-bottom: 1px solid rgba(153,202,187,.13); font-size: 13px; }
.step-index { color: #668078; font: 500 10px var(--font-mono); }
.signal-line { height: 40px; display: flex; align-items: end; gap: 5px; border-bottom: 1px solid rgba(119,208,179,.32); }
.signal-line i { flex: 1; max-width: 9px; background: #4ca789; opacity: .66; }
.quick-section,.capabilities { margin-top: 34px; }
.section-header { display: flex; align-items: end; justify-content: space-between; gap: 20px; margin-bottom: 15px; }
.section-kicker { display: block; color: var(--color-primary-dark); margin-bottom: 5px; }
.section-header h3 { font-size: 20px; letter-spacing: -.02em; }
.section-header p { color: var(--color-text-tertiary); font-size: 12px; }
.quick-grid { display: grid; grid-template-columns: repeat(4,1fr); border: 1px solid var(--color-border); background: var(--color-bg-surface); }
.quick-card { min-height: 145px; padding: 20px; display: grid; grid-template-columns: 38px 1fr auto; grid-template-rows: auto 1fr; gap: 16px 12px; color: inherit; text-decoration: none; border-right: 1px solid var(--color-border); position: relative; transition: background-color var(--transition-fast); }
.quick-card:last-child { border-right: 0; }
.quick-card:hover,.quick-card:focus-visible { background: var(--color-primary-bg); }
.quick-index { grid-column: 1/-1; color: var(--color-text-tertiary); font: 600 10px var(--font-mono); }
.quick-icon { width: 38px; height: 38px; display: grid; place-items: center; color: var(--color-primary-dark); border: 1px solid var(--color-primary-lighter); }
.quick-copy { display: flex; flex-direction: column; gap: 4px; }
.quick-copy strong { font-size: 14px; }
.quick-copy span { color: var(--color-text-tertiary); font-size: 11px; line-height: 1.5; }
.quick-arrow { align-self: center; color: var(--color-text-tertiary); transition: transform var(--transition-fast); }
.quick-card:hover .quick-arrow { transform: translateX(3px); color: var(--color-primary-dark); }
.capability-grid { display: grid; grid-template-columns: repeat(3,1fr); background: var(--color-border); gap: 1px; border: 1px solid var(--color-border); }
.capability-card { min-height: 270px; padding: 24px; background: var(--color-bg-surface); }
.capability-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 26px; }
.capability-index { font: 600 11px var(--font-mono); color: var(--color-text-tertiary); }
.capability-icon { width: 40px; height: 40px; display: grid; place-items: center; border: 1px solid var(--color-border); color: var(--color-primary-dark); }
.capability-card h4 { font-size: 15px; margin-bottom: 10px; }
.capability-card p { color: var(--color-text-secondary); font-size: 12px; line-height: 1.75; min-height: 65px; }
.capability-card ul { display: flex; flex-wrap: wrap; gap: 6px; list-style: none; margin-top: 18px; }
.capability-card li { padding: 3px 7px; color: var(--color-text-secondary); background: var(--color-bg-muted); border-radius: 2px; font: 500 10px var(--font-mono); }
.baseline { margin-top: 34px; display: grid; grid-template-columns: 190px 1fr; border: 1px solid var(--color-border); background: var(--color-bg-surface); }
.baseline-title { padding: 20px 22px; border-right: 1px solid var(--color-border); display: flex; flex-direction: column; gap: 4px; }
.baseline-title span { color: var(--color-primary-dark); }
.baseline-items { display: grid; grid-template-columns: repeat(5,1fr); }
.baseline-items div { min-height: 76px; display: flex; flex-direction: column; justify-content: center; padding: 14px 20px; border-right: 1px solid var(--color-border-light); }
.baseline-items div:last-child { border-right: 0; }
.baseline-items strong { color: var(--color-primary-dark); font: 600 13px var(--font-mono); }
.baseline-items span { color: var(--color-text-tertiary); font-size: 10px; margin-top: 3px; }
@media (max-width: 1180px) { .hero-board { grid-template-columns: 1fr 390px; } .quick-grid { grid-template-columns: repeat(2,1fr); } .quick-card:nth-child(2) { border-right: 0; } .quick-card:nth-child(-n+2) { border-bottom: 1px solid var(--color-border); } .capability-grid { grid-template-columns: repeat(2,1fr); } .baseline { grid-template-columns: 1fr; } .baseline-title { border-right: 0; border-bottom: 1px solid var(--color-border); } }
@media (max-width: 900px) { .hero-board { grid-template-columns: 1fr; } .pipeline-panel { margin: 0 24px 24px; min-height: 300px; } .baseline-items { grid-template-columns: repeat(3,1fr); } }
@media (max-width: 640px) { .hero-copy { padding: 30px 22px; } .hero-copy h2 { font-size: 31px; } .hero-actions { flex-direction: column; } .action { justify-content: center; } .pipeline-panel { margin: 0 14px 14px; padding: 18px; } .section-header { align-items: start; flex-direction: column; gap: 4px; } .quick-grid,.capability-grid { grid-template-columns: 1fr; } .quick-card { border-right: 0; border-bottom: 1px solid var(--color-border); } .quick-card:last-child { border-bottom: 0; } .capability-card { min-height: auto; } .baseline-items { grid-template-columns: repeat(2,1fr); } .baseline-items div { border-bottom: 1px solid var(--color-border-light); } }
</style>
