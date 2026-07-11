<template>
  <div class="eval-page">
    <el-tabs v-model="activeTab" class="eval-tabs">
      <!-- ======== Tab 1: 评测问题集管理 ======== -->
      <el-tab-pane name="testSets">
        <template #label>
          <span class="tab-label">
            <el-icon><Collection /></el-icon> 评测问题集
          </span>
        </template>

        <div class="tab-content">
          <div class="page-toolbar">
            <div class="toolbar-info">
              <span class="toolbar-count" v-if="testSets.length > 0">共 {{ testSets.length }} 个评测集</span>
            </div>
            <div class="toolbar-actions">
              <el-button @click="loadTestSets">
                <el-icon><Refresh /></el-icon> 刷新
              </el-button>
              <el-button type="primary" @click="showCreateDialog = true">
                <el-icon><Plus /></el-icon> 创建问题集
              </el-button>
            </div>
          </div>

          <div class="table-card">
            <el-table :data="testSets" v-loading="setsLoading" stripe>
              <el-table-column prop="setName" label="评测集名称" min-width="220">
                <template #default="{ row }">
                  <div class="set-name-cell">
                    <el-icon :size="16" color="#6366f1"><Tickets /></el-icon>
                    <span class="set-name">{{ row.setName }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="问题数量" width="110" align="center">
                <template #default="{ row }">
                  <span class="text-mono font-semibold" style="color: #6366f1">{{ row.questionCount }}</span>
                  <span style="font-size: 12px;color:#94a3b8;margin-left:4px">题</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="200" align="center">
                <template #default="{ row }">
                  <el-button size="small" type="primary" link @click="viewSetDetail(row.setName)">
                    <el-icon><View /></el-icon> 查看问题
                  </el-button>
                  <el-button size="small" type="success" link @click="useForEval(row.setName)">
                    <el-icon><CaretRight /></el-icon> 评测
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-if="!setsLoading && testSets.length === 0"
              description="暂无评测集，请创建标准评测问题" />
          </div>
        </div>
      </el-tab-pane>

      <!-- ======== Tab 2: 评测任务 ======== -->
      <el-tab-pane name="tasks">
        <template #label>
          <span class="tab-label">
            <el-icon><TrendCharts /></el-icon> 评测任务
          </span>
        </template>

        <div class="tab-content">
          <!-- 启动评测卡片 -->
          <div class="run-card">
            <div class="run-card-header">
              <el-icon :size="18" color="#6366f1"><VideoPlay /></el-icon>
              <span>启动新评测</span>
            </div>
            <el-form :inline="true" :model="runForm" class="run-form">
              <el-form-item label="评测集">
                <el-select v-model="runForm.setName" placeholder="选择评测集" style="width: 200px">
                  <el-option v-for="s in testSetNames" :key="s" :label="s" :value="s" />
                </el-select>
              </el-form-item>
              <el-form-item label="提示词模板">
                <el-select v-model="runForm.promptTemplateId" placeholder="选择模板" style="width: 220px">
                  <el-option v-for="tpl in promptTemplates" :key="tpl.id"
                    :label="`${tpl.scene} (v${tpl.version})`" :value="tpl.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="TopK">
                <el-input-number v-model="runForm.topK" :min="1" :max="20" style="width: 80px" size="small" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="handleRunEval" :loading="running"
                  :disabled="!runForm.setName || !runForm.promptTemplateId">
                  <el-icon><VideoPlay /></el-icon> 启动评测
                </el-button>
              </el-form-item>
            </el-form>
          </div>

          <!-- 任务列表 -->
          <div class="table-card" style="margin-top: 16px">
            <el-table :data="tasks" v-loading="tasksLoading" stripe>
              <el-table-column prop="taskId" label="任务 ID" width="140">
                <template #default="{ row }">
                  <span class="text-mono" style="font-size: 12px; color: #6366f1">{{ row.taskId }}</span>
                </template>
              </el-table-column>
              <el-table-column label="进度" width="160">
                <template #default="{ row }">
                  <div class="progress-cell">
                    <el-progress
                      :percentage="row.total > 0 ? Math.round((row.completed + row.failed) / row.total * 100) : 0"
                      :status="row.pending === 0 ? 'success' : ''"
                      :stroke-width="10"
                      :show-text="false"
                    />
                    <span class="progress-text text-mono">
                      <span style="color:#10b981">{{ row.completed }}</span>
                      <span style="color:#94a3b8">/</span>
                      <span style="color:#ef4444">{{ row.failed }}</span>
                      <span style="color:#94a3b8">/</span>
                      {{ row.total }}
                    </span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="latestTime" label="最新时间" width="170" />
              <el-table-column label="操作" width="120" align="center">
                <template #default="{ row }">
                  <el-button size="small" type="primary" link @click="viewTaskDetail(row.taskId)">
                    <el-icon><View /></el-icon> 查看详情
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-if="!tasksLoading && tasks.length === 0"
              description="暂无评测任务，请选择评测集启动评测" />
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- ======== 评测集详情弹窗 ======== -->
    <el-dialog v-model="showDetailDialog" :title="`评测集: ${currentSetName}`" width="780px">
      <el-table :data="currentQuestions" border stripe max-height="420">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="question" label="问题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="110">
          <template #default="{ row }">
            <el-tag size="small" type="info" effect="light" v-if="row.category">{{ row.category }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="难度" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="difficultyTag(row.difficulty)" size="small" effect="light">
              {{ difficultyLabel(row.difficulty) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center">
          <template #default="{ row }">
            <el-popconfirm title="确认删除？" @confirm="handleDeleteQuestion(row.id)">
              <template #reference>
                <el-button size="small" type="danger" link>
                  <el-icon><Delete /></el-icon>
                </el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- ======== 创建问题集弹窗 ======== -->
    <el-dialog v-model="showCreateDialog" title="创建评测问题集" width="700px">
      <el-form :model="createForm" label-width="100px">
        <el-form-item label="评测集名称" required>
          <el-input v-model="createForm.setName" placeholder="如：客服FAQ评测集" size="large" />
        </el-form-item>
        <el-form-item label="问题列表">
          <div class="questions-editor">
            <div v-for="(q, idx) in createForm.questions" :key="idx" class="question-row">
              <span class="q-index">{{ idx + 1 }}</span>
              <el-input v-model="q.question" placeholder="问题" style="flex: 2" size="small" />
              <el-input v-model="q.referenceAnswer" placeholder="参考答案（可选）" style="flex: 2" size="small" />
              <el-input v-model="q.category" placeholder="分类" style="width: 90px" size="small" />
              <el-select v-model="q.difficulty" style="width: 90px" size="small">
                <el-option label="简单" value="EASY" />
                <el-option label="中等" value="MEDIUM" />
                <el-option label="困难" value="HARD" />
              </el-select>
              <el-button :icon="Delete" circle size="small" @click="removeQuestion(idx)"
                :disabled="createForm.questions.length <= 1" />
            </div>
            <el-button type="primary" text @click="addQuestionRow">
              <el-icon><Plus /></el-icon> 添加问题
            </el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreateSet" :loading="creating"
          :disabled="!createForm.setName || createForm.questions.length === 0">
          创建 ({{ createForm.questions.length }} 题)
        </el-button>
      </template>
    </el-dialog>

    <!-- ======== 任务详情弹窗 ======== -->
    <el-dialog v-model="showTaskDetail" title="评测任务详情" width="1000px" top="3vh">
      <template v-if="taskDetail">
        <!-- 指标卡片 -->
        <div class="metrics-row">
          <div class="metric-card" v-for="m in taskMetrics" :key="m.label">
            <span class="metric-value text-mono" :style="{ color: m.color }">{{ m.value }}</span>
            <span class="metric-label">{{ m.label }}</span>
          </div>
        </div>

        <!-- 评测记录表格 -->
        <el-table :data="taskDetail.records" border stripe max-height="420">
          <el-table-column type="index" label="#" width="50" align="center" />
          <el-table-column label="精准度" width="85" align="center">
            <template #default="{ row }">
              <span :style="{ color: scoreColor(row.retrievalPrecision), fontWeight: 600 }" class="text-mono">
                {{ row.retrievalPrecision != null ? row.retrievalPrecision.toFixed(2) : '-' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="召回率" width="85" align="center">
            <template #default="{ row }">
              <span :style="{ color: scoreColor(row.contextRecall), fontWeight: 600 }" class="text-mono">
                {{ row.contextRecall != null ? row.contextRecall.toFixed(2) : '-' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="相关性" width="85" align="center">
            <template #default="{ row }">
              <span :style="{ color: scoreColor(row.answerRelevance, 5), fontWeight: 600 }" class="text-mono">
                {{ row.answerRelevance != null ? row.answerRelevance.toFixed(1) : '-' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="忠实度" width="85" align="center">
            <template #default="{ row }">
              <span :style="{ color: scoreColor(row.contextFaithfulness, 5), fontWeight: 600 }" class="text-mono">
                {{ row.contextFaithfulness != null ? row.contextFaithfulness.toFixed(1) : '-' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="幻觉分" width="85" align="center">
            <template #default="{ row }">
              <span :style="{ color: hallucinationColor(row.hallucinationScore), fontWeight: 600 }" class="text-mono">
                {{ row.hallucinationScore != null ? row.hallucinationScore.toFixed(1) : '-' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="latencyMs" label="耗时" width="75" align="center">
            <template #default="{ row }">
              <span class="text-mono" style="font-size: 12px">{{ row.latencyMs }}ms</span>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="70" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 'COMPLETED' ? 'success' : 'danger'" size="small" effect="light">
                {{ row.status === 'COMPLETED' ? '完成' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="回答" min-width="240" show-overflow-tooltip>
            <template #default="{ row }">{{ row.modelResponse }}</template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="{ row }">
              <el-popconfirm title="确认删除？" @confirm="handleDeleteRecord(row.id)">
                <template #reference>
                  <el-button size="small" type="danger" link>
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Delete, Plus, Refresh, View, CaretRight, Collection, TrendCharts, VideoPlay, Tickets
} from '@element-plus/icons-vue'
import {
  listTestSets, getQuestions, createQuestions, deleteQuestion,
  runEvaluation, listTasks, getTaskDetail, deleteRecord
} from '../../api/evaluation'
import { pagePromptTemplates } from '../../api/promptTemplate'

const activeTab = ref('testSets')

// ---- 评测集 ----
const setsLoading = ref(false)
const testSets = ref([])
const testSetNames = ref([])
const showCreateDialog = ref(false)
const showDetailDialog = ref(false)
const currentSetName = ref('')
const currentQuestions = ref([])
const creating = ref(false)

const createForm = reactive({
  setName: '',
  questions: [{ question: '', referenceAnswer: '', category: '', difficulty: 'MEDIUM' }]
})

function addQuestionRow() { createForm.questions.push({ question: '', referenceAnswer: '', category: '', difficulty: 'MEDIUM' }) }
function removeQuestion(idx) { if (createForm.questions.length > 1) createForm.questions.splice(idx, 1) }

// ---- 评测任务 ----
const tasksLoading = ref(false)
const tasks = ref([])
const running = ref(false)
const promptTemplates = ref([])
const showTaskDetail = ref(false)
const taskDetail = ref(null)
const currentTaskId = ref('')

const runForm = reactive({ setName: '', promptTemplateId: null, topK: 5 })

onMounted(async () => {
  await loadTestSets()
  await loadTasks()
  await loadPromptTemplates()
})

async function loadTestSets() {
  setsLoading.value = true
  try {
    const names = await listTestSets()
    testSetNames.value = names || []
    const sets = []
    for (const name of (names || [])) {
      const questions = await getQuestions(name)
      sets.push({ setName: name, questionCount: (questions || []).length })
    }
    testSets.value = sets
  } catch { testSets.value = [] } finally { setsLoading.value = false }
}

async function loadTasks() {
  tasksLoading.value = true
  try { tasks.value = await listTasks() || [] } catch { tasks.value = [] } finally { tasksLoading.value = false }
}

async function loadPromptTemplates() {
  try {
    const res = await pagePromptTemplates({ page: 1, size: 100, status: 'ACTIVE' })
    promptTemplates.value = res.records || []
  } catch { promptTemplates.value = [] }
}

async function viewSetDetail(setName) {
  currentSetName.value = setName
  try { currentQuestions.value = await getQuestions(setName) || []; showDetailDialog.value = true }
  catch { ElMessage.error('获取问题列表失败') }
}

async function handleCreateSet() {
  if (!createForm.setName || createForm.questions.length === 0) return
  creating.value = true
  try {
    const payload = createForm.questions.map(q => ({
      setName: createForm.setName, question: q.question,
      referenceAnswer: q.referenceAnswer, category: q.category, difficulty: q.difficulty
    }))
    await createQuestions(payload)
    ElMessage.success(`评测集「${createForm.setName}」创建成功`)
    showCreateDialog.value = false
    createForm.setName = ''
    createForm.questions = [{ question: '', referenceAnswer: '', category: '', difficulty: 'MEDIUM' }]
    await loadTestSets()
  } catch { ElMessage.error('创建失败') } finally { creating.value = false }
}

async function handleDeleteQuestion(id) {
  try { await deleteQuestion(id); ElMessage.success('删除成功'); viewSetDetail(currentSetName.value) }
  catch { ElMessage.error('删除失败') }
}

function useForEval(setName) {
  runForm.setName = setName
  activeTab.value = 'tasks'
}

async function handleRunEval() {
  if (!runForm.setName || !runForm.promptTemplateId) return
  running.value = true
  try {
    const res = await runEvaluation(runForm.setName, runForm.promptTemplateId, runForm.topK)
    ElMessage.success(`评测任务已启动 [${res.taskId}]，后台运行中`)
    await loadTasks()
  } catch { ElMessage.error('启动评测失败') } finally { running.value = false }
}

async function viewTaskDetail(taskId) {
  try {
    currentTaskId.value = taskId
    taskDetail.value = await getTaskDetail(taskId)
    showTaskDetail.value = true
  } catch { ElMessage.error('获取失败') }
}

async function handleDeleteRecord(id) {
  try { await deleteRecord(id); ElMessage.success('删除成功'); taskDetail.value = await getTaskDetail(currentTaskId.value) }
  catch { ElMessage.error('删除失败') }
}

const taskMetrics = computed(() => {
  if (!taskDetail.value) return []
  return [
    { label: '总计', value: taskDetail.value.total || 0, color: '#6366f1' },
    { label: '相关性均分', value: taskDetail.value.avgAnswerRelevance || '-', color: '#10b981' },
    { label: '忠实度均分', value: taskDetail.value.avgContextFaithfulness || '-', color: '#f59e0b' },
    { label: '幻觉均分↓', value: taskDetail.value.avgHallucinationScore || '-', color: '#ef4444' },
    { label: '召回率均分', value: taskDetail.value.avgContextRecall || '-', color: '#3b82f6' },
    { label: '精准度均分', value: taskDetail.value.avgRetrievalPrecision || '-', color: '#8b5cf6' }
  ]
})

function scoreColor(val, max = 1) {
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

function difficultyTag(d) {
  return { EASY: 'success', MEDIUM: 'warning', HARD: 'danger' }[d] || 'info'
}

function difficultyLabel(d) {
  return { EASY: '简单', MEDIUM: '中等', HARD: '困难' }[d] || d
}
</script>

<style scoped>
.eval-page {
  animation: fadeIn 0.4s ease;
  max-width: 1300px;
}

.eval-tabs {
  border-radius: var(--radius-xl);
  overflow: hidden;
  box-shadow: var(--shadow-sm);
}

.tab-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
}

.tab-content {
  padding: 20px;
  min-height: 360px;
}

.page-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.toolbar-count { font-size: 13px; color: var(--color-text-tertiary); }
.toolbar-actions { display: flex; gap: 8px; }

.table-card {
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 4px;
}

.table-card :deep(.el-table) { border-radius: var(--radius-lg); overflow: hidden; }
.table-card :deep(.el-table__inner-wrapper::before) { display: none; }

.set-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.set-name {
  font-weight: 600;
  color: var(--color-text-primary);
}

/* 启动评测卡片 */
.run-card {
  background: var(--color-primary-bg);
  border: 1px solid var(--color-primary-lighter);
  border-radius: var(--radius-lg);
  padding: 16px 20px;
}

.run-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 14px;
  color: var(--color-primary-dark);
  margin-bottom: 12px;
}

.run-form { margin: 0; }
.run-form :deep(.el-form-item) { margin-bottom: 0; margin-right: 16px; }

/* 进度单元格 */
.progress-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.progress-text {
  font-size: 11px;
  font-weight: 500;
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
  font-size: 22px;
  font-weight: 700;
  display: block;
}

.metric-label {
  font-size: 11px;
  color: var(--color-text-tertiary);
  margin-top: 2px;
  display: block;
}

/* 问题编辑器 */
.questions-editor {
  width: 100%;
}

.question-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}

.q-index {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-text-tertiary);
  width: 20px;
  text-align: center;
  flex-shrink: 0;
}
</style>
