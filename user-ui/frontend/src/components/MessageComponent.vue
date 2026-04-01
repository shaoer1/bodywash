<template>
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
          <p>正在生成图片...</p>
        </div>
        <div v-else-if="message.imageUrl" class="image-container">
          <img :src="message.imageUrl" :alt="message.content" class="generated-image" />
        </div>
        <div v-else class="error-message">
          生成失败，请重试
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
  }
}
</script>

<style scoped>
.message {
  max-width: 80%;
  padding: 12px 16px;
  border-radius: 18px;
  animation: fadeIn 0.3s ease;
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
  align-self: flex-end;
  background: #007bff;
  color: white;
  border-bottom-right-radius: 4px;
}

.message.bot {
  align-self: flex-start;
  background: rgba(255, 255, 255, 0.95);
  color: #333;
  border-bottom-left-radius: 4px;
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.1);
}

.generating {
  display: flex;
  align-items: center;
  gap: 10px;
}

.loading-spinner {
  width: 20px;
  height: 20px;
  border: 2px solid #f3f3f3;
  border-top: 2px solid #007bff;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.image-container {
  margin-top: 10px;
}

.generated-image {
  max-width: 100%;
  max-height: 300px;
  border-radius: 8px;
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.15);
  transition: transform 0.3s ease;
}

.generated-image:hover {
  transform: scale(1.02);
}

.error-message {
  color: #dc3545;
  font-style: italic;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .message {
    max-width: 90%;
  }
}
</style>