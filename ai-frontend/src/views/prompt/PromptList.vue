<template>
  <div class="prompt-list">
    <!-- 页面标题栏 -->
    <div class="page-toolbar stagger-1">
      <div class="toolbar-info">
        <h3 class="toolbar-title">提示词模板管理</h3>
        <span class="toolbar-count" v-if="total > 0">共 {{ total }} 个模板</span>
      </div>
      <el-button type="primary" size="large" @click="$router.push('/prompts/create')">
        <el-icon><Plus /></el-icon>
        新增模板
      </el-button>
    </div>

    <!-- 搜索区域 -->
    <div class="search-card stagger-2">
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item label="场景">
          <el-input v-model="query.scene" placeholder="输入场景名称搜索" clearable
            :prefix-icon="Search" @keyup.enter="fetchData" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="归档" value="ARCHIVED" />
            <el-option label="草稿" value="DRAFT" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">
            <el-icon><Search /></el-icon> 查询
          </el-button>
          <el-button @click="resetQuery">
            <el-icon><Refresh /></el-icon> 重置
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <div class="table-card stagger-3">
      <el-table :data="tableData" v-loading="loading" stripe
        :header-cell-style="{ background: '#f8fafc', color: '#64748b', fontWeight: 600, fontSize: '12px', textTransform: 'uppercase', letterSpacing: '0.05em' }">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="scene" label="场景" width="160">
          <template #default="{ row }">
            <span class="scene-name">{{ row.scene }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="systemPrompt" label="系统提示词" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="text-mono text-secondary" style="font-size: 12px">{{ row.systemPrompt }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="70" align="center">
          <template #default="{ row }">
            <span class="version-badge">v{{ row.version }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small" effect="light">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="temperature" label="Temp" width="70" align="center">
          <template #default="{ row }">
            <span class="text-mono" style="font-size: 12px; color: #6366f1">{{ row.temperature }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="maxTokens" label="Tokens" width="80" align="center">
          <template #default="{ row }">
            <span class="text-mono" style="font-size: 12px">{{ row.maxTokens }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="250" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="$router.push(`/prompts/${row.id}/edit`)">
              <el-icon><Edit /></el-icon> 编辑
            </el-button>
            <el-button
              v-if="row.status !== 'ACTIVE'"
              size="small" type="success" link
              @click="handleActivate(row)"
            >
              <el-icon><CircleCheck /></el-icon> 激活
            </el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              size="small" type="warning" link
              @click="handleArchive(row)"
            >
              <el-icon><Box /></el-icon> 归档
            </el-button>
            <el-popconfirm title="确认删除该模板？此操作不可恢复"
              confirm-button-text="确认删除" cancel-button-text="取消"
              @confirm="handleDelete(row)">
              <template #reference>
                <el-button size="small" type="danger" link>
                  <el-icon><Delete /></el-icon> 删除
                </el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && tableData.length === 0"
        description="暂无提示词模板，点击上方按钮创建第一个模板" />

      <div class="table-footer" v-if="total > 0">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, prev, pager, next, sizes"
          @current-change="fetchData"
          @size-change="fetchData"
          background
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete, CircleCheck, Box } from '@element-plus/icons-vue'
import {
  pagePromptTemplates,
  deletePromptTemplate,
  archivePromptTemplate,
  activatePromptTemplate
} from '../../api/promptTemplate'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, scene: '', status: '' })

const fetchData = async () => {
  loading.value = true
  try {
    const data = await pagePromptTemplates(query)
    tableData.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

const handleDelete = async (row) => {
  await deletePromptTemplate(row.id)
  ElMessage.success('删除成功')
  fetchData()
}

const handleArchive = async (row) => {
  await archivePromptTemplate(row.id)
  ElMessage.success('归档成功')
  fetchData()
}

const handleActivate = async (row) => {
  await activatePromptTemplate(row.id)
  ElMessage.success('激活成功')
  fetchData()
}

const statusType = (s) => ({ ACTIVE: 'success', ARCHIVED: 'info', DRAFT: 'warning' }[s] || 'info')
const statusLabel = (s) => ({ ACTIVE: '启用', ARCHIVED: '已归档', DRAFT: '草稿' }[s] || s)

const resetQuery = () => {
  query.page = 1; query.size = 10; query.scene = ''; query.status = ''
  fetchData()
}

onMounted(fetchData)
</script>

<style scoped>
.prompt-list {
  animation: fadeIn 0.4s ease;
  max-width: 1300px;
}

.page-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.toolbar-title {
  font-size: 17px;
  font-weight: 700;
  color: var(--color-text-primary);
}

.toolbar-count {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-left: 10px;
}

.search-card {
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 16px 20px 0;
  margin-bottom: 16px;
}

.search-form {
  margin: 0;
}

.search-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.search-form :deep(.el-form-item__label) {
  font-weight: 500;
  font-size: 13px;
}

.table-card {
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 4px;
}

.table-card :deep(.el-table) {
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.table-card :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.scene-name {
  font-weight: 600;
  color: var(--color-text-primary);
}

.version-badge {
  display: inline-block;
  padding: 1px 8px;
  background: var(--color-primary-bg);
  color: var(--color-primary);
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
  border-radius: 4px;
}

.table-footer {
  padding: 12px 16px 4px;
  display: flex;
  justify-content: flex-end;
}
</style>
