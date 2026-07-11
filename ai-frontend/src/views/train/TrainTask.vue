<template>
  <div class="train-page">
    <!-- 页面标题栏 -->
    <div class="page-toolbar stagger-1">
      <div class="toolbar-info">
        <h3 class="toolbar-title">模型微调</h3>
        <span class="toolbar-count" v-if="total > 0">共 {{ total }} 个任务</span>
      </div>
      <div class="toolbar-actions">
        <el-button @click="loadTasks" :loading="loading">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
        <el-button type="primary" size="large" @click="openCreateDialog">
          <el-icon><Plus /></el-icon> 新建微调任务
        </el-button>
      </div>
    </div>

    <!-- 任务列表 -->
    <div class="table-card stagger-2">
      <el-table :data="tasks" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column label="任务名称" min-width="200">
          <template #default="{ row }">
            <div class="task-name-cell">
              <el-icon :size="16" color="#6366f1"><Cpu /></el-icon>
              <span class="task-name">{{ row.taskName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="基座模型" width="190">
          <template #default="{ row }">
            <span class="text-mono" style="font-size: 12px">{{ row.modelBase }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="datasetName" label="数据集" width="140" />
        <el-table-column label="LoRA 参数" width="140">
          <template #default="{ row }">
            <div class="lora-tags">
              <span class="lora-tag">r={{ row.loraRank }}</span>
              <span class="lora-tag">α={{ row.loraAlpha }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="训练进度" width="170">
          <template #default="{ row }">
            <div class="progress-cell">
              <el-progress
                :percentage="row.progress || 0"
                :status="row.status === 'COMPLETED' ? 'success' : row.status === 'FAILED' ? 'exception' : ''"
                :stroke-width="10"
                :show-text="false"
              />
              <span class="progress-text text-mono" :class="{ 'text-success': row.status === 'COMPLETED' }">
                {{ row.progress || 0 }}%
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small" effect="light">
              <span class="status-with-dot">
                <span class="dot" :class="`dot-${row.status}`" />
                {{ statusLabel(row.status) }}
              </span>
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="viewDetail(row.id)">
              <el-icon><View /></el-icon> 详情
            </el-button>
            <el-button
              v-if="row.status === 'TRAINING'"
              size="small" type="warning" link
              :loading="pollingIds.has(row.id)"
              @click="handlePoll(row.id)"
            >
              <el-icon><Refresh /></el-icon> 刷新
            </el-button>
            <el-popconfirm title="确认删除该微调任务？" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button size="small" type="danger" link>
                  <el-icon><Delete /></el-icon> 删除
                </el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && tasks.length === 0"
        description="暂无微调任务，请创建 QLoRA 微调任务" />

      <div class="table-footer" v-if="total > 0">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="loadTasks"
          background
        />
      </div>
    </div>

    <!-- ======== 创建任务对话框 ======== -->
    <el-dialog v-model="showCreate" title="新建 QLoRA 微调任务" width="580px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="任务名称" required>
          <el-input v-model="form.taskName" placeholder="如：客服FAQ问答微调" />
        </el-form-item>
        <el-form-item label="基座模型" required>
          <el-select v-model="form.modelBase" placeholder="选择基座模型" style="width:100%">
            <el-option label="Qwen2-7B-Instruct（推荐）" value="Qwen2-7B-Instruct" />
            <el-option label="Qwen2-1.5B-Instruct（轻量）" value="Qwen2-1.5B-Instruct" />
            <el-option label="Llama-3-8B-Instruct" value="Llama-3-8B-Instruct" />
            <el-option label="Mistral-7B-Instruct" value="Mistral-7B-Instruct" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据集" required>
          <el-input v-model="form.datasetName" placeholder="数据集名称" />
        </el-form-item>
        <el-form-item label="数据路径">
          <el-input v-model="form.datasetPath" placeholder="本地 JSON 文件路径（可选）" />
        </el-form-item>

        <el-divider content-position="left">
          <span class="divider-label">LoRA 超参数</span>
        </el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="LoRA Rank">
              <el-input-number v-model="form.loraRank" :min="8" :max="256" :step="8" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="LoRA Alpha">
              <el-input-number v-model="form.loraAlpha" :min="4" :max="128" :step="4" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="学习率">
              <el-input-number v-model="form.learningRate" :min="1e-6" :max="1e-3" :step="1e-5"
                :precision="6" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="训练轮数">
              <el-input-number v-model="form.numEpochs" :min="1" :max="20" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="Batch Size">
              <el-input-number v-model="form.batchSize" :min="1" :max="32" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-alert type="info" :closable="false" show-icon style="margin-top: 8px">
          <template #title>任务创建后将自动调用 Python 微调服务，如服务不可用则保持 PENDING 状态</template>
        </el-alert>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="handleCreate" :loading="creating"
          :disabled="!form.taskName || !form.modelBase || !form.datasetName">
          创建并启动
        </el-button>
      </template>
    </el-dialog>

    <!-- ======== 详情对话框 ======== -->
    <el-dialog v-model="showDetail" title="微调任务详情" width="620px">
      <template v-if="currentTask">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="任务名称">
            <strong>{{ currentTask.taskName }}</strong>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTag(currentTask.status)" size="small" effect="light">
              {{ statusLabel(currentTask.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="基座模型">
            <span class="text-mono" style="font-size: 12px">{{ currentTask.modelBase }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="数据集">{{ currentTask.datasetName }}</el-descriptions-item>
          <el-descriptions-item label="LoRA Rank">
            <span class="text-mono" style="color: #6366f1">{{ currentTask.loraRank }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="LoRA Alpha">
            <span class="text-mono" style="color: #6366f1">{{ currentTask.loraAlpha }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="学习率">
            <span class="text-mono" style="font-size: 12px">{{ currentTask.learningRate }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="训练轮数">{{ currentTask.numEpochs }}</el-descriptions-item>
          <el-descriptions-item label="Batch Size">{{ currentTask.batchSize }}</el-descriptions-item>
          <el-descriptions-item label="训练进度">
            <span class="text-mono font-semibold" :class="{ 'text-success': currentTask.status === 'COMPLETED' }">
              {{ currentTask.progress || 0 }}%
            </span>
          </el-descriptions-item>
          <el-descriptions-item v-if="currentTask.loraWeightPath" label="权重路径" :span="2">
            <span class="text-mono" style="font-size: 11px; word-break: break-all">{{ currentTask.loraWeightPath }}</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="currentTask.metrics" label="训练指标" :span="2">
            <pre class="metrics-pre">{{ formatMetrics(currentTask.metrics) }}</pre>
          </el-descriptions-item>
          <el-descriptions-item v-if="currentTask.errorMsg" label="错误信息" :span="2">
            <span style="color: #ef4444; font-size: 13px">{{ currentTask.errorMsg }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间" :span="2">{{ currentTask.createTime }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh, View, Delete, Cpu } from '@element-plus/icons-vue'
import { createTask, listTasks, getTask, pollStatus, deleteTask } from '../../api/train'

const loading = ref(false)
const tasks = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10 })

const showCreate = ref(false)
const creating = ref(false)
const form = reactive({
  taskName: '', modelBase: 'Qwen2-7B-Instruct', datasetName: '', datasetPath: '',
  loraRank: 64, loraAlpha: 16, learningRate: 0.0002, numEpochs: 3, batchSize: 4
})

const showDetail = ref(false)
const currentTask = ref(null)

const pollingIds = ref(new Set())
let pollingTimer = null

onMounted(() => { loadTasks(); startAutoPolling() })
onUnmounted(() => { if (pollingTimer) clearInterval(pollingTimer) })

async function loadTasks() {
  loading.value = true
  try {
    const data = await listTasks(query)
    tasks.value = data.records || []
    total.value = data.total || 0
  } catch { tasks.value = [] } finally { loading.value = false }
}

function openCreateDialog() {
  Object.assign(form, {
    taskName: '', modelBase: 'Qwen2-7B-Instruct', datasetName: '', datasetPath: '',
    loraRank: 64, loraAlpha: 16, learningRate: 0.0002, numEpochs: 3, batchSize: 4
  })
  showCreate.value = true
}

async function handleCreate() {
  if (!form.taskName || !form.modelBase || !form.datasetName) return
  creating.value = true
  try {
    await createTask(form)
    ElMessage.success('微调任务已创建并启动')
    showCreate.value = false
    await loadTasks()
  } catch { ElMessage.error('创建失败') } finally { creating.value = false }
}

async function viewDetail(id) {
  try { currentTask.value = await getTask(id); showDetail.value = true }
  catch { ElMessage.error('获取详情失败') }
}

async function handlePoll(id) {
  pollingIds.value.add(id)
  try { await pollStatus(id); await loadTasks() }
  catch { ElMessage.error('刷新失败') } finally { pollingIds.value.delete(id) }
}

async function handleDelete(id) {
  try { await deleteTask(id); ElMessage.success('删除成功'); await loadTasks() }
  catch { ElMessage.error('删除失败') }
}

function startAutoPolling() {
  pollingTimer = setInterval(async () => {
    const training = tasks.value.filter(t => t.status === 'TRAINING')
    if (training.length === 0) return
    for (const t of training) {
      try { await pollStatus(t.id) } catch { /* ignore */ }
    }
    await loadTasks()
  }, 10000)
}

function formatMetrics(metricsStr) {
  try { return JSON.stringify(JSON.parse(metricsStr), null, 2) } catch { return metricsStr }
}

function statusTag(s) {
  return { PENDING: 'info', TRAINING: 'warning', COMPLETED: 'success', FAILED: 'danger' }[s] || 'info'
}

function statusLabel(s) {
  return { PENDING: '待启动', TRAINING: '训练中', COMPLETED: '已完成', FAILED: '失败' }[s] || s
}
</script>

<style scoped>
.train-page {
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

.toolbar-actions { display: flex; gap: 8px; }

.table-card {
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 4px;
}

.table-card :deep(.el-table) { border-radius: var(--radius-lg); overflow: hidden; }
.table-card :deep(.el-table__inner-wrapper::before) { display: none; }

.task-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.task-name {
  font-weight: 600;
  color: var(--color-text-primary);
}

.lora-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.lora-tag {
  font-family: var(--font-mono);
  font-size: 11px;
  padding: 2px 7px;
  background: var(--color-primary-bg);
  color: var(--color-primary-dark);
  border-radius: 4px;
  font-weight: 500;
}

.progress-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.progress-text {
  font-size: 12px;
  font-weight: 500;
}

.text-success {
  color: var(--color-success);
}

/* 状态指示点 */
.status-with-dot {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.dot-PENDING { background: #3b82f6; }
.dot-TRAINING { background: #f59e0b; animation: pulse-glow 2s infinite; }
.dot-COMPLETED { background: #10b981; }
.dot-FAILED { background: #ef4444; }

.table-footer {
  padding: 12px 16px 4px;
  display: flex;
  justify-content: flex-end;
}

.divider-label {
  font-weight: 600;
  font-size: 13px;
}

.metrics-pre {
  margin: 0;
  font-family: var(--font-mono);
  font-size: 11px;
  line-height: 1.5;
  max-height: 160px;
  overflow: auto;
  background: var(--color-bg-muted);
  padding: 10px;
  border-radius: var(--radius-sm);
  color: var(--color-text-primary);
}
</style>
