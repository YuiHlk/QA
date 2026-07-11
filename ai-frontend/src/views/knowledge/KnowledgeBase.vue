<template>
  <div class="knowledge-page">
    <!-- 页面标题栏 -->
    <div class="page-toolbar stagger-1">
      <div class="toolbar-info">
        <h3 class="toolbar-title">知识库管理</h3>
        <span class="toolbar-count" v-if="total > 0">共 {{ total }} 个文档</span>
      </div>
      <div class="toolbar-actions">
        <el-button @click="fetchData" :loading="loading">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
        <el-button type="primary" size="large" @click="showUploadDialog = true">
          <el-icon><Upload /></el-icon> 上传文档
        </el-button>
      </div>
    </div>

    <!-- 搜索区域 -->
    <div class="search-card stagger-2">
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item label="文件名">
          <el-input v-model="query.fileName" placeholder="搜索文件名" clearable
            :prefix-icon="Search" @clear="fetchData" @keyup.enter="fetchData" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部状态" clearable
            style="width: 130px" @change="fetchData">
            <el-option label="处理中" value="PROCESSING" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="失败" value="FAILED" />
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

    <!-- 文档表格 -->
    <div class="table-card stagger-3">
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column label="文档" min-width="220">
          <template #default="{ row }">
            <div class="doc-cell">
              <span class="doc-icon">
                <el-icon :size="16"><Document /></el-icon>
              </span>
              <div class="doc-info">
                <span class="doc-name">{{ row.fileName }}</span>
                <span class="doc-meta">{{ row.fileType }} · {{ formatFileSize(row.fileSize) }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="分块参数" width="150">
          <template #default="{ row }">
            <div class="chunk-info">
              <span class="chunk-val">Size: <strong class="text-mono">{{ row.chunkSize }}</strong></span>
              <span class="chunk-val">Overlap: <strong class="text-mono">{{ row.chunkOverlap }}</strong></span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="chunkCount" label="分块数" width="90" align="center">
          <template #default="{ row }">
            <span class="text-mono font-semibold" :style="{ color: row.chunkCount > 0 ? '#6366f1' : '#94a3b8' }">
              {{ row.chunkCount }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small" effect="light">
              <span class="status-with-dot">
                <span class="dot" :class="`dot-${row.status}`" />
                {{ statusLabel(row.status) }}
              </span>
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="上传时间" width="170" />
        <el-table-column label="操作" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="viewDetail(row)">
              <el-icon><View /></el-icon> 详情
            </el-button>
            <el-popconfirm title="确认删除此文档？将同时删除关联的所有分块数据"
              confirm-button-text="确认删除" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button size="small" type="danger" link>
                  <el-icon><Delete /></el-icon> 删除
                </el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && tableData.length === 0 && !query.status && !query.fileName"
        description="暂无文档，请上传 PDF、MD 或 TXT 文件构建知识库">
        <el-button type="primary" @click="showUploadDialog = true">
          <el-icon><Upload /></el-icon> 上传第一个文档
        </el-button>
      </el-empty>

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

    <!-- 上传对话框 -->
    <el-dialog v-model="showUploadDialog" title="上传文档" width="520px" :close-on-click-modal="false">
      <el-form :model="uploadForm" label-width="100px">
        <el-form-item label="选择文件" required>
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            :on-change="handleFileChange"
            :on-remove="() => uploadForm.file = null"
            accept=".pdf,.md,.txt"
            drag
            class="upload-zone"
          >
            <div class="upload-placeholder">
              <el-icon :size="40" color="#6366f1"><UploadFilled /></el-icon>
              <p class="upload-text">拖拽文件到此处或 <em>点击上传</em></p>
              <p class="upload-tip">支持 PDF / Markdown / TXT 格式</p>
            </div>
          </el-upload>
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="分块大小">
              <el-input-number v-model="uploadForm.chunkSize" :min="128" :max="4096" :step="128" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="重叠窗口">
              <el-input-number v-model="uploadForm.chunkOverlap" :min="0" :max="1024" :step="16" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-alert type="info" :closable="false" show-icon style="margin-top: 4px">
          <template #title>
            上传后系统将自动进行解析、分块与向量化处理
          </template>
        </el-alert>
      </el-form>
      <template #footer>
        <el-button @click="showUploadDialog = false">取消</el-button>
        <el-button type="primary" @click="handleUpload" :loading="uploading" :disabled="!uploadForm.file">
          <el-icon><Upload /></el-icon> 上传并处理
        </el-button>
      </template>
    </el-dialog>

    <!-- 文档详情对话框 -->
    <el-dialog v-model="showDetailDialog" title="文档详情" width="500px">
      <el-descriptions v-if="currentDoc" :column="2" border size="small">
        <el-descriptions-item label="ID">{{ currentDoc.id }}</el-descriptions-item>
        <el-descriptions-item label="文件名">
          <strong>{{ currentDoc.fileName }}</strong>
        </el-descriptions-item>
        <el-descriptions-item label="文件类型">
          <el-tag size="small" type="info">{{ currentDoc.fileType }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="文件大小">{{ formatFileSize(currentDoc.fileSize) }}</el-descriptions-item>
        <el-descriptions-item label="分块大小">{{ currentDoc.chunkSize }}</el-descriptions-item>
        <el-descriptions-item label="重叠窗口">{{ currentDoc.chunkOverlap }}</el-descriptions-item>
        <el-descriptions-item label="分块数量">
          <strong style="color: #6366f1">{{ currentDoc.chunkCount }}</strong>
        </el-descriptions-item>
        <el-descriptions-item label="处理状态">
          <el-tag :type="statusTagType(currentDoc.status)" size="small" effect="light">
            {{ statusLabel(currentDoc.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="上传时间" :span="2">{{ currentDoc.createTime }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, Upload, UploadFilled, Document, View, Delete } from '@element-plus/icons-vue'
import { uploadDocument, pageDocuments, deleteDocument, getDocument } from '../../api/document'

const loading = ref(false)
const uploading = ref(false)
const tableData = ref([])
const total = ref(0)
const showUploadDialog = ref(false)
const showDetailDialog = ref(false)
const currentDoc = ref(null)

const query = reactive({ page: 1, size: 10, fileName: '', status: '' })

const uploadForm = reactive({ file: null, chunkSize: 512, chunkOverlap: 64 })

const fetchData = async () => {
  loading.value = true
  try {
    const params = { page: query.page, size: query.size }
    if (query.status) params.status = query.status
    const data = await pageDocuments(params)
    let records = data.records || []
    if (query.fileName) {
      records = records.filter(r => r.fileName && r.fileName.includes(query.fileName))
    }
    tableData.value = records
    total.value = data.total || 0
  } catch {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const handleFileChange = (file) => { uploadForm.file = file.raw }

const handleUpload = async () => {
  if (!uploadForm.file) return
  uploading.value = true
  try {
    await uploadDocument(uploadForm.file, uploadForm.chunkSize, uploadForm.chunkOverlap)
    ElMessage.success('文档上传成功，后台正在处理中')
    showUploadDialog.value = false
    uploadForm.file = null
    fetchData()
  } catch {
    ElMessage.error('上传失败')
  } finally {
    uploading.value = false
  }
}

const handleDelete = async (id) => {
  try {
    await deleteDocument(id)
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    ElMessage.error('删除失败')
  }
}

const viewDetail = async (row) => {
  try {
    currentDoc.value = await getDocument(row.id)
    showDetailDialog.value = true
  } catch {
    ElMessage.error('获取详情失败')
  }
}

const resetQuery = () => {
  query.page = 1; query.size = 10; query.fileName = ''; query.status = ''
  fetchData()
}

const formatFileSize = (bytes) => {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0, size = bytes
  while (size >= 1024 && i < units.length - 1) { size /= 1024; i++ }
  return size.toFixed(i > 0 ? 1 : 0) + ' ' + units[i]
}

const statusTagType = (s) => ({ PROCESSING: 'warning', COMPLETED: 'success', FAILED: 'danger' }[s] || 'info')
const statusLabel = (s) => ({ PROCESSING: '处理中', COMPLETED: '已完成', FAILED: '失败' }[s] || s)

onMounted(fetchData)
</script>

<style scoped>
.knowledge-page {
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
}

.toolbar-count {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-left: 10px;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
}

.search-card {
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 16px 20px 0;
  margin-bottom: 16px;
}

.search-form { margin: 0; }
.search-form :deep(.el-form-item) { margin-bottom: 16px; }
.search-form :deep(.el-form-item__label) { font-weight: 500; font-size: 13px; }

.table-card {
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 4px;
}

.table-card :deep(.el-table) { border-radius: var(--radius-lg); overflow: hidden; }
.table-card :deep(.el-table__inner-wrapper::before) { display: none; }

/* 文档单元格 */
.doc-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.doc-icon {
  width: 34px;
  height: 34px;
  border-radius: var(--radius-sm);
  background: var(--color-primary-bg);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-primary);
  flex-shrink: 0;
}

.doc-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.doc-name {
  font-weight: 600;
  font-size: 13px;
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.doc-meta {
  font-size: 11px;
  color: var(--color-text-tertiary);
}

/* 分块参数 */
.chunk-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.chunk-val {
  font-size: 12px;
  color: var(--color-text-secondary);
}

.chunk-val strong {
  color: var(--color-text-primary);
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

.dot-PROCESSING { background: #f59e0b; animation: pulse-glow 2s infinite; }
.dot-COMPLETED { background: #10b981; }
.dot-FAILED { background: #ef4444; }

.table-footer {
  padding: 12px 16px 4px;
  display: flex;
  justify-content: flex-end;
}

/* 上传区域 */
.upload-zone :deep(.el-upload-dragger) {
  padding: 28px;
}

.upload-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.upload-text {
  font-size: 14px;
  color: var(--color-text-primary);
  margin: 0;
}

.upload-text em {
  color: var(--color-primary);
  font-style: normal;
  font-weight: 600;
}

.upload-tip {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin: 0;
}
</style>
