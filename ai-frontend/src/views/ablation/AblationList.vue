<template>
  <div class="ablation-page">
    <!-- 页面标题栏 -->
    <div class="page-toolbar stagger-1">
      <div class="toolbar-info">
        <h3 class="toolbar-title">消融实验</h3>
        <span class="toolbar-count" v-if="experiments.length > 0">共 {{ experiments.length }} 个实验</span>
      </div>
      <el-button type="primary" size="large" @click="openCreateDialog">
        <el-icon><Plus /></el-icon> 新建实验
      </el-button>
    </div>

    <!-- 实验列表 -->
    <div class="table-card stagger-2">
      <el-table :data="experiments" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column label="实验名称" min-width="220">
          <template #default="{ row }">
            <div class="exp-name-cell">
              <el-icon :size="16" color="#6366f1"><Connection /></el-icon>
              <span class="exp-name">{{ row.experimentName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="testSetName" label="评测集" width="160" />
        <el-table-column label="进度" width="160">
          <template #default="{ row }">
            <div class="progress-cell">
              <el-progress
                :percentage="row.totalTasks > 0 ? Math.round(row.completedTasks / row.totalTasks * 100) : 0"
                :status="row.status === 'COMPLETED' ? 'success' : row.status === 'FAILED' ? 'exception' : ''"
                :stroke-width="10"
                :show-text="false"
              />
              <span class="progress-text text-mono">
                {{ row.completedTasks }}<span style="color:#94a3b8">/</span>{{ row.totalTasks }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small" effect="light">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="viewDetail(row.id)">
              <el-icon><TrendCharts /></el-icon> 对比报告
            </el-button>
            <el-button
              v-if="row.status === 'PENDING'"
              size="small" type="success" link
              @click="handleRun(row.id)"
            >
              <el-icon><VideoPlay /></el-icon> 运行
            </el-button>
            <el-popconfirm title="确认删除实验？" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button size="small" type="danger" link>
                  <el-icon><Delete /></el-icon> 删除
                </el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && experiments.length === 0"
        description="暂无消融实验，请创建实验自动对比不同 RAG 参数组合的效果" />
    </div>

    <!-- ======== 创建实验对话框 ======== -->
    <el-dialog v-model="showCreate" title="新建消融实验" width="680px" top="5vh">
      <el-form :model="createForm" label-width="100px">
        <el-form-item label="实验名称" required>
          <el-input v-model="createForm.name" placeholder="如：分块大小与 TopK 消融实验" size="large" />
        </el-form-item>
        <el-form-item label="评测集" required>
          <el-select v-model="createForm.testSetName" placeholder="选择评测集" style="width: 100%">
            <el-option v-for="s in testSetNames" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>

        <el-divider content-position="left">
          <span class="divider-label">基准配置</span>
        </el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="提示词模板">
              <el-select v-model="createForm.baseConfig.promptTemplateId" placeholder="选择模板" style="width: 100%">
                <el-option v-for="tpl in promptTemplates" :key="tpl.id"
                  :label="`${tpl.scene} (v${tpl.version})`" :value="tpl.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="分块大小">
              <el-input-number v-model="createForm.baseConfig.chunkSize" :min="128" :max="4096" :step="128" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="TopK">
              <el-input-number v-model="createForm.baseConfig.topK" :min="1" :max="20" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">
          <span class="divider-label">
            变量配置
            <el-tooltip content="各变量取值做笛卡尔积组合，组合数不能超过 50。">
              <el-icon style="margin-left:4px;cursor:help;color:#94a3b8"><QuestionFilled /></el-icon>
            </el-tooltip>
          </span>
        </el-divider>

        <div class="variables-section">
          <div v-for="(v, idx) in createForm.variables" :key="idx" class="variable-row">
            <span class="var-index text-mono">V{{ idx + 1 }}</span>
            <el-input v-model="v.name" placeholder="变量名（如 chunkSize）" style="width: 160px" size="small" />
            <el-input v-model="v.valuesStr" placeholder="取值，逗号分隔（如 256,512,1024）" style="flex: 1" size="small" />
            <el-button :icon="Delete" circle size="small" @click="removeVariable(idx)"
              :disabled="createForm.variables.length <= 1" />
          </div>
          <el-button type="primary" text @click="addVariable">
            <el-icon><Plus /></el-icon> 添加变量
          </el-button>
        </div>

        <el-alert type="info" :closable="false" show-icon style="margin-top: 12px">
          <template #title>实验将对每个参数组合运行完整评测流程，完成后生成对比报告</template>
        </el-alert>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="handleCreate" :loading="creating" :disabled="!canCreate">
          创建实验
        </el-button>
      </template>
    </el-dialog>

    <!-- ======== 对比报告对话框 ======== -->
    <el-dialog v-model="showReport" title="消融实验对比报告" width="980px" top="3vh">
      <template v-if="reportData && reportData.groups">
        <div class="metrics-row">
          <div class="metric-card" v-for="b in bestCards" :key="b.label">
            <span class="metric-value text-mono" :style="{ color: b.color }">{{ b.value }}</span>
            <span class="metric-label">{{ b.label }}</span>
          </div>
        </div>

        <el-table :data="reportData.groups" border stripe max-height="460">
          <el-table-column prop="label" label="实验组" min-width="200">
            <template #default="{ row }">
              <span class="text-mono" style="font-size:12px;font-weight:500">{{ row.label }}</span>
            </template>
          </el-table-column>
          <el-table-column label="完成/总数" width="100" align="center">
            <template #default="{ row }">
              <span class="text-mono" style="font-size:12px">{{ row.completed }}<span style="color:#94a3b8">/</span>{{ row.total }}</span>
            </template>
          </el-table-column>
          <el-table-column label="相关性 ↑" width="90" align="center">
            <template #default="{ row }">
              <span class="text-mono"
                :style="{ color: scoreColor(row.avgAnswerRelevance, 5), fontWeight: bestFor('bestRelevance', row.label) ? '700' : '400' }">
                {{ row.avgAnswerRelevance }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="忠实度 ↑" width="90" align="center">
            <template #default="{ row }">
              <span class="text-mono"
                :style="{ color: scoreColor(row.avgContextFaithfulness, 5), fontWeight: bestFor('bestFaithfulness', row.label) ? '700' : '400' }">
                {{ row.avgContextFaithfulness }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="幻觉分 ↓" width="90" align="center">
            <template #default="{ row }">
              <span class="text-mono"
                :style="{ color: hallucinationColor(row.avgHallucinationScore), fontWeight: bestFor('bestHallucination', row.label) ? '700' : '400' }">
                {{ row.avgHallucinationScore }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="召回率 ↑" width="90" align="center">
            <template #default="{ row }">
              <span class="text-mono"
                :style="{ color: scoreColor(row.avgContextRecall, 1), fontWeight: bestFor('bestRecall', row.label) ? '700' : '400' }">
                {{ row.avgContextRecall }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="精准度 ↑" width="90" align="center">
            <template #default="{ row }">
              <span class="text-mono"
                :style="{ color: scoreColor(row.avgRetrievalPrecision, 1), fontWeight: bestFor('bestPrecision', row.label) ? '700' : '400' }">
                {{ row.avgRetrievalPrecision }}
              </span>
            </template>
          </el-table-column>
        </el-table>

        <div style="margin-top: 8px; font-size: 12px; color: #94a3b8">
          粗体 = 该指标最优组 · ↑ 越高越好 · ↓ 越低越好
        </div>
      </template>
      <el-empty v-else-if="reportData" description="实验尚未产生对比数据" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Delete, QuestionFilled, Connection, TrendCharts, VideoPlay } from '@element-plus/icons-vue'
import { createExperiment, listExperiments, getExperiment, runExperiment, deleteExperiment } from '../../api/ablation'
import { listTestSets } from '../../api/evaluation'
import { pagePromptTemplates } from '../../api/promptTemplate'

const loading = ref(false)
const experiments = ref([])

const showCreate = ref(false)
const creating = ref(false)
const testSetNames = ref([])
const promptTemplates = ref([])

const createForm = reactive({
  name: '',
  testSetName: '',
  baseConfig: { promptTemplateId: null, chunkSize: 512, topK: 5 },
  variables: [{ name: '', valuesStr: '' }]
})

const canCreate = computed(() =>
  createForm.name && createForm.testSetName && createForm.baseConfig.promptTemplateId
)

const showReport = ref(false)
const reportData = ref(null)

onMounted(async () => {
  await loadExperiments()
  await loadTestSetNames()
  await loadPromptTemplates()
})

async function loadExperiments() {
  loading.value = true
  try { experiments.value = await listExperiments() || [] } catch { experiments.value = [] } finally { loading.value = false }
}

async function loadTestSetNames() {
  try { testSetNames.value = await listTestSets() || [] } catch { testSetNames.value = [] }
}

async function loadPromptTemplates() {
  try {
    const res = await pagePromptTemplates({ page: 1, size: 100, status: 'ACTIVE' })
    promptTemplates.value = res.records || []
  } catch { promptTemplates.value = [] }
}

function addVariable() {
  createForm.variables.push({ name: '', valuesStr: '' })
}

function removeVariable(idx) {
  if (createForm.variables.length > 1) createForm.variables.splice(idx, 1)
}

function openCreateDialog() {
  createForm.name = ''
  createForm.testSetName = ''
  createForm.baseConfig = { promptTemplateId: null, chunkSize: 512, topK: 5 }
  createForm.variables = [{ name: '', valuesStr: '' }]
  showCreate.value = true
}

async function handleCreate() {
  if (!canCreate.value) return
  creating.value = true
  try {
    const variableConfigs = createForm.variables
      .filter(v => v.name.trim() && v.valuesStr.trim())
      .map(v => ({
        variable: v.name.trim(),
        values: v.valuesStr.split(',').map(s => {
          const trimmed = s.trim()
          const num = Number(trimmed)
          return isNaN(num) ? trimmed : num
        })
      }))
    await createExperiment(createForm.name, createForm.testSetName, createForm.baseConfig, variableConfigs)
    ElMessage.success('实验创建成功')
    showCreate.value = false
    await loadExperiments()
  } catch (e) {
    ElMessage.error('创建失败: ' + (e.message || '未知错误'))
  } finally {
    creating.value = false
  }
}

async function handleRun(id) {
  try { await runExperiment(id); ElMessage.success('实验已启动'); await loadExperiments() }
  catch { ElMessage.error('启动失败') }
}

async function handleDelete(id) {
  try { await deleteExperiment(id); ElMessage.success('删除成功'); await loadExperiments() }
  catch { ElMessage.error('删除失败') }
}

async function viewDetail(id) {
  try { reportData.value = await getExperiment(id); showReport.value = true }
  catch { ElMessage.error('获取报告失败') }
}

const bestCards = computed(() => {
  if (!reportData.value) return []
  return [
    { label: '实验组数', value: reportData.value.totalGroups || 0, color: '#6366f1' },
    { label: '最佳相关性', value: reportData.value.bestRelevance || '-', color: '#10b981' },
    { label: '最佳忠实度', value: reportData.value.bestFaithfulness || '-', color: '#f59e0b' },
    { label: '最低幻觉', value: reportData.value.bestHallucination || '-', color: '#ef4444' },
    { label: '最佳召回', value: reportData.value.bestRecall || '-', color: '#3b82f6' },
    { label: '最佳精准', value: reportData.value.bestPrecision || '-', color: '#8b5cf6' }
  ]
})

function bestFor(key, label) {
  return reportData.value && reportData.value[key] === label
}

function scoreColor(val, max) {
  if (val == null) return '#94a3b8'
  const ratio = val / max
  if (ratio >= 0.7) return '#10b981'
  if (ratio >= 0.4) return '#f59e0b'
  return '#ef4444'
}

function hallucinationColor(val) {
  if (val == null) return '#94a3b8'
  if (val <= 2) return '#10b981'
  if (val <= 3) return '#f59e0b'
  return '#ef4444'
}

function statusTag(s) {
  return { PENDING: 'info', RUNNING: 'warning', COMPLETED: 'success', FAILED: 'danger' }[s] || 'info'
}

function statusLabel(s) {
  return { PENDING: '待执行', RUNNING: '运行中', COMPLETED: '已完成', FAILED: '失败' }[s] || s
}
</script>

<style scoped>
.ablation-page {
  animation: fadeIn 0.4s ease;
  max-width: 1300px;
}

.page-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.toolbar-title { font-size: 17px; font-weight: 700; }

.toolbar-count {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-left: 10px;
}

.table-card {
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 4px;
}

.table-card :deep(.el-table) { border-radius: var(--radius-lg); overflow: hidden; }
.table-card :deep(.el-table__inner-wrapper::before) { display: none; }

.exp-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.exp-name {
  font-weight: 600;
  color: var(--color-text-primary);
}

.progress-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.progress-text {
  font-size: 11px;
  font-weight: 500;
}

.divider-label {
  font-weight: 600;
  font-size: 13px;
  display: flex;
  align-items: center;
}

.variables-section {
  margin-bottom: 8px;
}

.variable-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.var-index {
  font-size: 11px;
  color: var(--color-primary);
  font-weight: 600;
  width: 24px;
  flex-shrink: 0;
}

/* 指标行 */
.metrics-row {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 10px;
  margin-bottom: 16px;
}

.metric-card {
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 12px 8px;
  text-align: center;
}

.metric-value {
  font-size: 20px;
  font-weight: 700;
  display: block;
}

.metric-label {
  font-size: 11px;
  color: var(--color-text-tertiary);
  margin-top: 2px;
  display: block;
}
</style>
