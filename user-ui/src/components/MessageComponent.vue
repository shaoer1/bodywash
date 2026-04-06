<template>
  <div class="messages-wrapper">
    <div 
      v-for="message in messages" 
      :key="message.id"
      :class="['message', message.type]"
    >
      <div class="message-content">
        <div v-if="message.type === 'user'" class="user-message">
          {{ message.content }}
        </div>
        <div v-else class="bot-message">
          <div v-if="message.status === 'generating'" class="generating">
            <div class="loading-spinner"></div>
            <span>生成中...</span>
          </div>
          <div v-else-if="message.imageUrl" class="image-container">
            <img :src="message.imageUrl" :alt="message.content" class="generated-image" />
            <div class="image-actions">
              <button class="action-btn" @click="downloadImage(message.imageUrl)" title="下载">
                <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <path d="M21 15V19C21 19.5304 20.7893 20.0391 20.4142 20.4142C20.0391 20.7893 19.5304 21 19 21H5C4.46957 21 3.96086 20.7893 3.58579 20.4142C3.21071 20.0391 3 19.5304 3 19V15" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                  <path d="M7 10L12 15L17 10" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                  <path d="M12 15V3" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
              </button>
            </div>
          </div>
          <div v-else class="error-message">
            生成失败
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'MessageComponent',
  props: {
    messages: {
      type: Array,
      required: true
    }
  },
  methods: {
    downloadImage(url) {
      const link = document.createElement('a')
      link.href = url
      link.download = `image-${Date.now()}.png`
      link.click()
    }
  }
}
</script>

<style scoped>
.messages-wrapper {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.message {
  display: flex;
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.message.user {
  justify-content: flex-end;
}

.message.bot {
  justify-content: flex-start;
}

.message-content {
  max-width: 70%;
}

.user-message {
  background: #1890ff;
  color: white;
  padding: 10px 16px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.5;
}

.bot-message {
  background: white;
  padding: 12px;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
}

.generating {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 4px;
  color: #8c8c8c;
  font-size: 14px;
}

.loading-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid #e5e5e5;
  border-top-color: #1890ff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.image-container {
  position: relative;
  display: inline-block;
}

.generated-image {
  max-width: 100%;
  max-height: 400px;
  border-radius: 8px;
  display: block;
}

.image-actions {
  position: absolute;
  bottom: 8px;
  right: 8px;
  opacity: 0;
  transition: opacity 0.2s;
}

.image-container:hover .image-actions {
  opacity: 1;
}

.action-btn {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.9);
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #595959;
  transition: all 0.2s;
}

.action-btn:hover {
  background: white;
  color: #1890ff;
}

.action-btn svg {
  width: 16px;
  height: 16px;
}

.error-message {
  color: #ff4d4f;
  font-size: 14px;
  padding: 4px;
}

@media (max-width: 768px) {
  .message-content {
    max-width: 85%;
  }
  
  .generated-image {
    max-height: 300px;
  }
}
</style>
