<template>
  <div class="chat-input">
    <input 
      v-model="inputText" 
      @keyup.enter="handleSend"
      :disabled="isGenerating"
      placeholder="输入图片描述，按Enter生成..."
      class="input-field"
    />
    <button 
      @click="handleSend" 
      :disabled="isGenerating"
      class="send-button"
    >
      {{ isGenerating ? '生成中...' : '生成' }}
    </button>
  </div>
</template>

<script>
export default {
  name: 'InputComponent',
  props: {
    isGenerating: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      inputText: ''
    }
  },
  methods: {
    handleSend() {
      if (!this.inputText.trim() || this.isGenerating) return
      
      this.$emit('send', this.inputText.trim())
      this.inputText = ''
    }
  }
}
</script>

<style scoped>
.chat-input {
  background: rgba(255, 255, 255, 0.95);
  padding: 20px;
  display: flex;
  gap: 10px;
  box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.1);
}

.input-field {
  flex: 1;
  padding: 12px 16px;
  border: 1px solid #ddd;
  border-radius: 25px;
  font-size: 16px;
  outline: none;
  transition: border-color 0.3s ease;
}

.input-field:focus {
  border-color: #007bff;
  box-shadow: 0 0 0 2px rgba(0, 123, 255, 0.25);
}

.input-field:disabled {
  background: #f8f9fa;
  cursor: not-allowed;
}

.send-button {
  background: #007bff;
  color: white;
  border: none;
  border-radius: 25px;
  padding: 0 24px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: background-color 0.3s ease;
}

.send-button:hover:not(:disabled) {
  background: #0069d9;
}

.send-button:active:not(:disabled) {
  transform: translateY(1px);
}

.send-button:disabled {
  background: #6c757d;
  cursor: not-allowed;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .chat-input {
    padding: 15px;
  }
}
</style>