<template>
  <div class="prompt-form-page">
    <!-- 返回 + 标题 -->
    <div class="form-header stagger-1">
      <el-button text @click="$router.back()" class="back-btn">
        <el-icon><ArrowLeft /></el-icon> 返回列表
      </el-button>
      <h3 class="form-title">{{ isEdit ? '编辑提示词模板' : '创建提示词模板' }}</h3>
      <span class="form-subtitle">{{ isEdit ? '修改模板参数并保存新版本' : '配置场景化的提示词模板参数' }}</span>
    </div>

    <!-- 表单主体 -->
    <div class="form-body stagger-2">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="120px" size="large">
        <!-- 基本信息 -->
        <div class="form-section">
          <div class="section-title">基本信息</div>
          <el-form-item label="场景名称" prop="scene">
            <el-input v-model="form.scene" placeholder="如：客服问答、知识检索、代码生成" />
          </el-form-item>
          <el-form-item label="版本备注">
            <el-input v-model="form.remark" placeholder="本次版本的变更说明" />
          </el-form-item>
          <el-form-item label="状态">
            <el-radio-group v-model="form.status">
              <el-radio-button value="DRAFT">
                <el-icon><EditPen /></el-icon> 草稿
              </el-radio-button>
              <el-radio-button value="ACTIVE">
                <el-icon><CircleCheck /></el-icon> 启用
              </el-radio-button>
            </el-radio-group>
          </el-form-item>
        </div>

        <!-- 提示词内容 -->
        <div class="form-section">
          <div class="section-title">提示词配置</div>
          <el-form-item label="系统提示词" prop="systemPrompt">
            <el-input
              v-model="form.systemPrompt"
              type="textarea"
              :rows="5"
              placeholder="你是一个专业的...助手，请根据以下参考资料回答用户问题..."
            />
          </el-form-item>
          <el-form-item label="用户模板" prop="userTemplate">
            <el-input
              v-model="form.userTemplate"
              type="textarea"
              :rows="5"
              placeholder="用户问题：{{question}}&#10;参考资料：{{context}}&#10;请基于以上资料回答问题。"
            />
          </el-form-item>
          <el-form-item label="Few-Shot">
            <el-input
              v-model="form.fewShotExamples"
              type="textarea"
              :rows="3"
              placeholder='[{"input":"Q1...","output":"A1..."},{"input":"Q2...","output":"A2..."}]'
            />
            <div class="form-hint">可选：提供少量示例帮助模型理解期望的输出格式</div>
          </el-form-item>
        </div>

        <!-- 模型参数 -->
        <div class="form-section">
          <div class="section-title">模型参数</div>
          <div class="param-grid">
            <div class="param-item">
              <label class="param-label">Temperature</label>
              <el-slider
                v-model="form.temperature"
                :min="0" :max="2" :step="0.1"
                :marks="{ 0: '0', 0.7: '0.7', 1: '1', 2: '2' }"
                show-input
              />
              <span class="param-hint">越高越随机，越低越确定</span>
            </div>
            <div class="param-item">
              <label class="param-label">Top P</label>
              <el-slider
                v-model="form.topP"
                :min="0" :max="1" :step="0.1"
                :marks="{ 0: '0', 0.5: '0.5', 1: '1' }"
                show-input
              />
              <span class="param-hint">核采样阈值</span>
            </div>
            <div class="param-item">
              <label class="param-label">Max Tokens</label>
              <el-input-number
                v-model="form.maxTokens"
                :min="1" :max="32768" :step="256"
                style="width: 160px"
              />
              <span class="param-hint">最大生成 token 数</span>
            </div>
          </div>
        </div>

        <!-- 操作按钮 -->
        <div class="form-actions">
          <el-button size="large" @click="$router.back()">取消</el-button>
          <el-button type="primary" size="large" @click="handleSubmit" :loading="submitting">
            <el-icon><Check /></el-icon>
            {{ isEdit ? '保存更新' : '创建模板' }}
          </el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, EditPen, CircleCheck, Check } from '@element-plus/icons-vue'
import { getPromptTemplate, createPromptTemplate, updatePromptTemplate } from '../../api/promptTemplate'

const route = useRoute()
const router = useRouter()
const formRef = ref(null)
const submitting = ref(false)

const isEdit = computed(() => !!route.params.id)

const form = reactive({
  scene: '',
  systemPrompt: '',
  userTemplate: '',
  fewShotExamples: '',
  temperature: 0.7,
  topP: 1.0,
  maxTokens: 2048,
  status: 'DRAFT',
  remark: ''
})

const rules = {
  scene: [{ required: true, message: '请输入场景名称', trigger: 'blur' }],
  systemPrompt: [{ required: true, message: '请输入系统提示词', trigger: 'blur' }],
  userTemplate: [{ required: true, message: '请输入用户提示词模板', trigger: 'blur' }]
}

onMounted(async () => {
  if (isEdit.value) {
    const data = await getPromptTemplate(route.params.id)
    Object.assign(form, data)
  }
})

const handleSubmit = async () => {
  await formRef.value.validate()
  submitting.value = true
  try {
    if (isEdit.value) {
      await updatePromptTemplate(route.params.id, form)
      ElMessage.success('更新成功')
    } else {
      await createPromptTemplate(form)
      ElMessage.success('创建成功')
    }
    router.push('/prompts')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.prompt-form-page {
  animation: fadeIn 0.4s ease;
  max-width: 800px;
  margin: 0 auto;
}

.form-header {
  margin-bottom: 20px;
}

.back-btn {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-bottom: 8px;
  padding: 0;
}

.back-btn:hover {
  color: var(--color-primary);
}

.form-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin-bottom: 4px;
}

.form-subtitle {
  font-size: 13px;
  color: var(--color-text-tertiary);
}

.form-body {
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  padding: 28px 32px;
  box-shadow: var(--shadow-sm);
}

.form-section {
  margin-bottom: 28px;
}

.form-section:last-of-type {
  margin-bottom: 20px;
}

.section-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin-bottom: 16px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--color-border-light);
  letter-spacing: 0.02em;
}

.form-hint {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-top: 6px;
  line-height: 1.5;
}

.param-grid {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.param-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.param-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-secondary);
}

.param-hint {
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 20px;
  border-top: 1px solid var(--color-border-light);
}

/* Slider refinement */
:deep(.el-slider__marks-text) {
  font-size: 10px;
  font-family: var(--font-mono);
  color: var(--color-text-tertiary);
}

:deep(.el-slider__runway) {
  background: var(--color-bg-muted);
  border-radius: 4px;
}

:deep(.el-slider__bar) {
  background: linear-gradient(90deg, var(--color-primary), var(--color-primary-light));
  border-radius: 4px;
}

:deep(.el-slider__button) {
  border-color: var(--color-primary);
}
</style>
