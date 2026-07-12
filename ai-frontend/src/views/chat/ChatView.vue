<template>
  <div class="chat-root">
    <button class="mobile-config-toggle" type="button" :aria-expanded="isConfigOpen" @click="isConfigOpen = !isConfigOpen">
      <el-icon><Setting /></el-icon>
      {{ isConfigOpen ? '收起会话面板' : '会话配置与历史' }}
    </button>
    <button v-if="isConfigOpen" class="config-backdrop" type="button" aria-label="关闭会话面板" @click="isConfigOpen = false" />
    <!-- 左侧配置面板 -->
    <aside class="chat-sidebar" :class="{ open: isConfigOpen }">
      <!-- 配置区 -->
      <div class="sidebar-block">
        <div class="block-title">
          <el-icon :size="14"><Setting /></el-icon>
          会话配置
        </div>
        <div class="block-body">
          <label class="field-label">提示词模板</label>
          <el-select v-model="selectedPromptId" placeholder="选择提示词模板"
            style="width: 100%" @change="onConfigChange" size="small">
            <el-option
              v-for="tpl in promptTemplates"
              :key="tpl.id"
              :label="tpl.scene"
              :value="tpl.id"
            >
              <div class="tpl-option">
                <span>{{ tpl.scene }}</span>
                <el-tag size="small" :type="tpl.status === 'ACTIVE' ? 'success' : 'info'" effect="light">
                  v{{ tpl.version }}
                </el-tag>
              </div>
            </el-option>
          </el-select>

          <label class="field-label" style="margin-top: 14px">限定文档（可选）</label>
          <el-select v-model="selectedDocId" placeholder="全局搜索"
            style="width: 100%" clearable @change="onConfigChange" size="small">
            <el-option
              v-for="doc in documents"
              :key="doc.id"
              :label="doc.fileName"
              :value="doc.id"
            >
              <div class="tpl-option">
                <span>{{ doc.fileName }}</span>
                <el-tag size="small" :type="doc.status === 'COMPLETED' ? 'success' : 'warning'" effect="light">
                  {{ doc.status === 'COMPLETED' ? '就绪' : '处理中' }}
                </el-tag>
              </div>
            </el-option>
          </el-select>
        </div>
      </div>

      <!-- 会话列表 -->
      <div class="sidebar-block sessions-block">
        <div class="block-title">
          <el-icon :size="14"><ChatLineSquare /></el-icon>
          会话历史
        </div>
        <div class="block-body">
          <el-button style="width: 100%" size="small" @click="newSession">
            <el-icon><Plus /></el-icon> 新建会话
          </el-button>
        </div>
        <div class="session-list" v-if="sessions.length > 0">
          <div
            v-for="s in sessions"
            :key="s.id"
            class="session-item"
            :class="{ active: s.id === sessionId }"
            @click="switchSession(s.id)"
          >
            <div class="session-left">
              <div class="session-name">{{ s.name }}</div>
              <div class="session-time">{{ s.time }}</div>
            </div>
            <button class="session-del" @click.stop="handleDeleteSession(s.id)" title="删除会话">
              <el-icon :size="13"><Close /></el-icon>
            </button>
          </div>
        </div>
      </div>
    </aside>

    <!-- 右侧对话区 -->
    <div class="chat-main">
      <!-- 消息区 -->
      <div class="chat-messages" ref="messagesContainer">
        <!-- 欢迎引导 -->
        <div v-if="messages.length === 0" class="chat-welcome">
          <div class="welcome-icon">
            <svg viewBox="0 0 48 48" fill="none" width="48" height="48">
              <rect width="48" height="48" rx="12" fill="url(#wg)" />
              <path d="M16 28v-10l8 5-8 5z" fill="#fff" />
              <path d="M24 28v-10l8 5-8 5z" fill="rgba(255,255,255,0.6)" />
              <defs><linearGradient id="wg" x1="0" y1="0" x2="48" y2="48"><stop stop-color="#6366f1"/><stop offset="1" stop-color="#8b5cf6"/></linearGradient></defs>
            </svg>
          </div>
          <h3 class="welcome-title">AI 知识库问答</h3>
          <p class="welcome-desc">
            {{ selectedPromptId ? '输入问题开始对话，AI 将基于知识库文档为你解答' : '请先在左侧选择提示词模板，然后开始对话' }}
          </p>
          <div class="welcome-hints" v-if="selectedPromptId">
            <span class="hint-chip" v-for="h in exampleQuestions" :key="h" @click="inputText = h; handleSend()">{{ h }}</span>
          </div>
        </div>

        <!-- 消息列表 -->
        <div v-for="(msg, idx) in messages" :key="idx" class="msg-row" :class="msg.role">
          <div class="msg-avatar">
            <div v-if="msg.role === 'user'" class="avatar-user">
              <el-icon :size="16"><UserFilled /></el-icon>
            </div>
            <div v-else class="avatar-ai">
              <svg viewBox="0 0 24 24" fill="none" width="16" height="16">
                <path d="M12 2l2.4 7.2h7.6l-6 4.8 2.4 7.2-6.4-4.8-6.4 4.8 2.4-7.2-6-4.8h7.6z" fill="currentColor"/>
              </svg>
            </div>
          </div>
          <div class="msg-body">
            <div class="msg-meta">
              <span class="msg-sender">{{ msg.role === 'user' ? '你' : 'AI 助手' }}</span>
              <span class="msg-time" v-if="msg.time">{{ msg.time }}</span>
              <span class="msg-latency" v-if="msg.latencyMs">· {{ msg.latencyMs }}ms</span>
            </div>
            <div class="msg-bubble">
              <div class="msg-text">{{ msg.content }}</div>
            </div>
            <!-- 引用来源 -->
            <div v-if="msg.role === 'assistant' && msg.sources && msg.sources.length > 0" class="msg-sources">
              <el-collapse>
                <el-collapse-item>
                  <template #title>
                    <span class="sources-title">
                      <el-icon :size="14"><Link /></el-icon>
                      引用来源 ({{ msg.sources.length }})
                    </span>
                  </template>
                  <div v-for="(src, si) in msg.sources" :key="si" class="source-item">
                    <span class="source-index">#{{ si + 1 }}</span>
                    <p>{{ src }}</p>
                  </div>
                </el-collapse-item>
              </el-collapse>
            </div>
          </div>
        </div>

        <!-- AI 思考中 -->
        <div v-if="thinking && (!messages.length || messages[messages.length-1]?.role === 'user' || !messages[messages.length-1]?.content)" class="msg-row assistant">
          <div class="msg-avatar">
            <div class="avatar-ai thinking-avatar">
              <svg viewBox="0 0 24 24" fill="none" width="16" height="16">
                <path d="M12 2l2.4 7.2h7.6l-6 4.8 2.4 7.2-6.4-4.8-6.4 4.8 2.4-7.2-6-4.8h7.6z" fill="currentColor"/>
              </svg>
            </div>
          </div>
          <div class="msg-body">
            <div class="msg-meta">
              <span class="msg-sender">AI 助手</span>
              <span class="thinking-label">思考中...</span>
            </div>
            <div class="thinking-indicator">
              <span></span><span></span><span></span>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="chat-input">
        <div class="input-wrapper">
          <el-input
            v-model="inputText"
            type="textarea"
            :rows="1"
            :autosize="{ minRows: 1, maxRows: 4 }"
            placeholder="输入问题，Enter 发送，Shift+Enter 换行"
            :disabled="!canSend"
            @keydown.enter.exact="handleSend"
            resize="none"
            class="chat-textarea"
          />
          <el-button
            type="primary"
            :disabled="!canSend || !inputText.trim()"
            :loading="thinking"
            @click="handleSend"
            class="send-btn"
            circle
          >
            <el-icon :size="18"><Promotion /></el-icon>
          </el-button>
        </div>
        <div class="input-footer">
          <span class="footer-hint" v-if="!selectedPromptId">
            <el-icon :size="12"><WarningFilled /></el-icon>
            请先选择提示词模板
          </span>
          <span class="footer-hint" v-else-if="thinking">AI 正在生成回复...</span>
          <span class="footer-hint" v-else>Enter 发送 · Shift+Enter 换行</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import {
  Setting, ChatLineSquare, Plus, Promotion, UserFilled,
  Close, Link, WarningFilled
} from '@element-plus/icons-vue'
import { askQuestionStream, deleteChatHistory, getChatHistory } from '../../api/qa'
import { pagePromptTemplates } from '../../api/promptTemplate'
import { pageDocuments } from '../../api/document'

const promptTemplates = ref([])
const documents = ref([])
const selectedPromptId = ref(null)
const selectedDocId = ref(null)

const sessionId = ref(initSessionId())
const messages = ref([])
const inputText = ref('')
const thinking = ref(false)
const cancelStream = ref(null)
const messagesContainer = ref(null)
const isConfigOpen = ref(false)

const sessions = ref(loadSessions())
const canSend = computed(() => selectedPromptId.value && !thinking.value)

const exampleQuestions = [
  '平台有哪些核心功能？',
  '什么是 RAG 检索增强生成？',
  '如何进行消融实验？'
]

function initSessionId() {
  const stored = localStorage.getItem('qa_current_session')
  return stored || generateUUID()
}

function generateUUID() {
  return 'sess_' + Date.now() + '_' + Math.random().toString(36).substring(2, 10)
}

function loadSessions() {
  try { return JSON.parse(localStorage.getItem('qa_sessions') || '[]') }
  catch { return [] }
}

function saveSessions() {
  localStorage.setItem('qa_sessions', JSON.stringify(sessions.value))
}

function persistSessionId() {
  localStorage.setItem('qa_current_session', sessionId.value)
}

onMounted(async () => {
  await loadConfigData()
  await loadChatHistory()
})

async function loadConfigData() {
  try {
    const [tplRes, docRes] = await Promise.all([
      pagePromptTemplates({ page: 1, size: 100, status: 'ACTIVE' }),
      pageDocuments({ page: 1, size: 100 })
    ])
    promptTemplates.value = tplRes.records || []
    documents.value = (docRes.records || []).filter(d => d.status === 'COMPLETED')
  } catch { /* 静默 */ }
}

async function loadChatHistory() {
  if (!sessionId.value) return
  try {
    const logs = await getChatHistory(sessionId.value)
    messages.value = (logs || []).flatMap(log => [
      { role: 'user', content: log.userQuestion, time: log.createTime },
      { role: 'assistant', content: log.modelResponse, time: log.createTime, latencyMs: log.latencyMs, sources: parseSources(log.retrievedChunks) }
    ])
    await scrollToBottom()
  } catch { messages.value = [] }
}

function parseSources(chunks) {
  if (!chunks) return []
  try { const arr = JSON.parse(chunks); return Array.isArray(arr) ? arr : [chunks] }
  catch { return [chunks] }
}

async function handleSend() {
  const text = inputText.value.trim()
  if (!text || !canSend.value) return

  messages.value.push({ role: 'user', content: text, time: formatTime(new Date()) })
  inputText.value = ''
  await scrollToBottom()

  const assistantMsg = { role: 'assistant', content: '', time: formatTime(new Date()), latencyMs: 0, sources: [] }
  messages.value.push(assistantMsg)

  thinking.value = true
  cancelStream.value = askQuestionStream({
    question: text,
    promptTemplateId: selectedPromptId.value,
    documentId: selectedDocId.value || null,
    sessionId: sessionId.value,
    onMeta(meta) {
      assistantMsg.sessionId = meta.sessionId
      assistantMsg.sources = meta.sources || []
      assistantMsg.retrievalMs = meta.retrievalMs
    },
    onToken(token) {
      assistantMsg.content += token
      scrollToBottom()
    },
    onDone(done) {
      assistantMsg.latencyMs = done.latencyMs
      thinking.value = false
      cancelStream.value = null
      updateSessionMeta(text)
      scrollToBottom()
    },
    onError(err) {
      assistantMsg.content = assistantMsg.content || '抱歉，请求失败：' + (err.message || '网络错误')
      assistantMsg.isError = true
      thinking.value = false
      cancelStream.value = null
      scrollToBottom()
    }
  })
}

function newSession() {
  sessionId.value = generateUUID()
  messages.value = []
  persistSessionId()
  saveSessions()
}

function switchSession(id) {
  sessionId.value = id
  persistSessionId()
  loadChatHistory()
}

async function handleDeleteSession(id) {
  try { await deleteChatHistory(id) } catch { /* 忽略 */ }
  sessions.value = sessions.value.filter(s => s.id !== id)
  saveSessions()
  if (sessionId.value === id) newSession()
}

function updateSessionMeta(lastQuestion) {
  const existing = sessions.value.find(s => s.id === sessionId.value)
  const name = lastQuestion.length > 20 ? lastQuestion.substring(0, 20) + '...' : lastQuestion
  if (existing) {
    existing.name = name
    existing.time = formatTime(new Date())
  } else {
    sessions.value.unshift({ id: sessionId.value, name, time: formatTime(new Date()) })
  }
  saveSessions()
}

function onConfigChange() {}

function scrollToBottom() {
  return nextTick(() => {
    const el = messagesContainer.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

function formatTime(date) {
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}
</script>

<style scoped>
.chat-root {
  display: flex;
  height: 100%;
  min-height: 0;
  position: relative;
  background: var(--color-bg-surface);
  border-radius: var(--radius-xl);
  border: 1px solid var(--color-border);
  overflow: hidden;
  animation: fadeIn 0.4s ease;
}

/* ======== 左侧栏 ======== */
.chat-sidebar {
  width: 260px;
  background: var(--color-bg-elevated);
  border-right: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.sidebar-block {
  padding: 16px;
  border-bottom: 1px solid var(--color-border-light);
}

.sessions-block {
  flex: 1;
  display: flex;
  flex-direction: column;
  border-bottom: none;
  overflow: hidden;
}

.block-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  font-weight: 700;
  color: var(--color-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 12px;
}

.field-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-tertiary);
  display: block;
  margin-bottom: 6px;
}

.tpl-option {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px 8px;
}

.session-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 9px 12px;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background-color var(--transition-fast), border-color var(--transition-fast);
  margin-bottom: 2px;
}

.session-item:hover {
  background: var(--color-bg-muted);
}

.session-item.active {
  background: var(--color-primary-bg);
  border-left: 3px solid var(--color-primary);
}

.session-left {
  flex: 1;
  min-width: 0;
}

.session-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-item.active .session-name {
  color: var(--color-primary-dark);
  font-weight: 600;
}

.session-time {
  font-size: 11px;
  color: var(--color-text-tertiary);
  margin-top: 2px;
}

.session-del {
  width: 22px;
  height: 22px;
  border: none;
  background: transparent;
  color: var(--color-text-tertiary);
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity var(--transition-fast), color var(--transition-fast), background-color var(--transition-fast);
  flex-shrink: 0;
}

.session-item:hover .session-del {
  opacity: 1;
}

.session-del:hover {
  background: var(--color-danger-bg);
  color: var(--color-danger);
}

/* ======== 对话区 ======== */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

.chat-messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 24px;
  background: var(--color-bg-elevated);
}

/* 欢迎引导 */
.chat-welcome {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  text-align: center;
  gap: 12px;
}

.welcome-icon {
  margin-bottom: 8px;
}

.welcome-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-text-primary);
}

.welcome-desc {
  font-size: 14px;
  color: var(--color-text-secondary);
  max-width: 360px;
  line-height: 1.6;
}

.welcome-hints {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
  justify-content: center;
}

.hint-chip {
  padding: 6px 14px;
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: 20px;
  font-size: 13px;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.hint-chip:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: var(--color-primary-bg);
}

/* 消息行 */
.msg-row {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
  animation: fadeInUp 0.35s var(--ease-spring) both;
}

.msg-row.user {
  flex-direction: row-reverse;
}

/* 头像 */
.msg-avatar {
  flex-shrink: 0;
}

.avatar-user,
.avatar-ai {
  width: 34px;
  height: 34px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-user {
  background: var(--color-primary);
  color: #fff;
}

.avatar-ai {
  background: #17221f;
  color: #77d0b3;
}

.thinking-avatar {
  animation: pulse-glow 2s infinite;
}

/* 消息体 */
.msg-body {
  max-width: 72%;
  min-width: 0;
}

.msg-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.msg-row.user .msg-meta {
  flex-direction: row-reverse;
}

.msg-sender {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.msg-time,
.msg-latency {
  font-size: 11px;
  color: var(--color-text-tertiary);
  font-family: var(--font-mono);
}

.thinking-label {
  font-size: 11px;
  color: var(--color-primary);
  font-weight: 500;
}

/* 气泡 */
.msg-bubble {
  padding: 12px 16px;
  border-radius: 14px;
  line-height: 1.65;
}

.msg-row.user .msg-bubble {
  background: var(--color-primary);
  color: #fff;
  border-bottom-right-radius: 4px;
  box-shadow: 0 2px 10px rgba(12, 107, 85, 0.18);
}

.msg-row.assistant .msg-bubble {
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
  border: 1px solid var(--color-border-light);
  border-bottom-left-radius: 4px;
  box-shadow: var(--shadow-xs);
}

.msg-text {
  font-size: 14px;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 引用来源 */
.msg-sources {
  margin-top: 8px;
  max-width: 100%;
}

:deep(.msg-sources .el-collapse) {
  border: none;
  background: transparent;
}

:deep(.msg-sources .el-collapse-item__header) {
  height: 32px;
  padding: 0 12px;
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-sm);
  font-size: 12px;
}

.sources-title {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-secondary);
}

.source-item {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  padding: 8px;
  background: var(--color-bg-muted);
  border-radius: var(--radius-sm);
}

.source-index {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-primary);
  font-weight: 600;
  flex-shrink: 0;
}

.source-item p {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-secondary);
  line-height: 1.5;
  word-break: break-all;
}

/* 思考动画 */
.thinking-indicator {
  display: flex;
  gap: 5px;
  padding: 10px 14px;
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border-light);
  border-radius: 14px;
  border-bottom-left-radius: 4px;
  width: fit-content;
}

.thinking-indicator span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--color-primary);
  animation: dot-bounce 1.4s infinite ease-in-out both;
}

.thinking-indicator span:nth-child(1) { animation-delay: -0.32s; }
.thinking-indicator span:nth-child(2) { animation-delay: -0.16s; }
.thinking-indicator span:nth-child(3) { animation-delay: 0s; }

@keyframes dot-bounce {
  0%, 80%, 100% { opacity: 0.15; transform: scale(0.7); }
  40% { opacity: 1; transform: scale(1); }
}

/* ======== 输入区 ======== */
.chat-input {
  padding: 16px 20px;
  background: var(--color-bg-surface);
  border-top: 1px solid var(--color-border);
}

.input-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 10px;
}

.chat-textarea {
  flex: 1;
}

.chat-textarea :deep(.el-textarea__inner) {
  background: var(--color-bg-muted);
  border-radius: 12px;
  padding: 10px 16px;
  font-size: 14px;
  line-height: 1.5;
  resize: none;
}

.chat-textarea :deep(.el-textarea__inner:focus) {
  background: var(--color-bg-surface);
}

.send-btn {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
}

.input-footer {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
}

.footer-hint {
  font-size: 11px;
  color: var(--color-text-tertiary);
  display: flex;
  align-items: center;
  gap: 4px;
}

.mobile-config-toggle,
.config-backdrop {
  display: none;
}

@media (max-width: 900px) {
  .mobile-config-toggle {
    display: flex;
    position: absolute;
    top: 12px;
    left: 12px;
    z-index: 8;
    align-items: center;
    gap: 7px;
    min-height: 34px;
    padding: 0 11px;
    border: 1px solid var(--color-border);
    border-radius: var(--radius-md);
    background: rgba(252, 251, 247, .94);
    color: var(--color-text-secondary);
    font-size: 12px;
    font-weight: 600;
    box-shadow: var(--shadow-sm);
  }

  .config-backdrop {
    display: block;
    position: absolute;
    inset: 0;
    z-index: 9;
    border: 0;
    background: rgba(20, 31, 28, .38);
  }

  .chat-sidebar {
    position: absolute;
    inset: 0 auto 0 0;
    z-index: 10;
    width: min(84vw, 310px);
    transform: translateX(-101%);
    transition: transform var(--transition-base);
    box-shadow: var(--shadow-xl);
  }

  .chat-sidebar.open { transform: translateX(0); }
  .chat-messages { padding: 62px 18px 20px; }
  .msg-body { max-width: 88%; }
  .session-del { opacity: 1; }
}

@media (max-width: 520px) {
  .chat-root { border-radius: var(--radius-lg); }
  .chat-messages { padding-inline: 12px; }
  .msg-row { gap: 8px; margin-bottom: 18px; }
  .msg-body { max-width: calc(100% - 42px); }
  .msg-bubble { padding: 10px 12px; }
  .chat-input { padding: 12px 12px calc(10px + env(safe-area-inset-bottom)); }
  .input-footer { display: none; }
  .welcome-hints { align-items: stretch; flex-direction: column; }
  .hint-chip { border-radius: var(--radius-md); }
}
</style>
