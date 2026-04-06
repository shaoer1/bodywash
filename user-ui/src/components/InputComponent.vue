<template>
  <div class="chat-input">
    <div class="input-wrapper">
      <input 
        v-model="inputText" 
        @keyup.enter="handleSend"
        placeholder="输入描述..."
        class="input-field"
      />
      <button 
        @click="handleSend" 
        :disabled="!inputText.trim()"
        class="send-button"
        :class="{ active: inputText.trim() }"
      >
        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M22 2L11 13" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          <path d="M22 2L15 22L11 13L2 9L22 2Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>
    </div>
  </div>
</template>

<script>
export default {
  name: 'InputComponent',
  data() {
    return {
      inputText: ''
    }
  },
  methods: {
    handleSend() {
      if (!this.inputText.trim()) return
      
      this.$emit('send', this.inputText.trim())
      this.inputText = ''
    },
    setInputText(text) {
      this.inputText = text
    }
  }
}
</script>

<style scoped>
.chat-input {
  background: white;
  padding: 16px 20px;
  border-top: 1px solid #e5e5e5;
}

.input-wrapper {
  display: flex;
  align-items: center;
  background: #f5f5f5;
  border-radius: 8px;
  padding: 4px;
}

.input-field {
  flex: 1;
  padding: 10px 14px;
  border: none;
  background: transparent;
  font-size: 14px;
  color: #262626;
  outline: none;
}

.input-field::placeholder {
  color: #bfbfbf;
}

.input-field:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.send-button {
  width: 36px;
  height: 36px;
  border-radius: 6px;
  background: #e5e5e5;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #bfbfbf;
  transition: all 0.2s;
}

.send-button.active {
  background: #1890ff;
  color: white;
}

.send-button.active:hover {
  background: #40a9ff;
}

.send-button:disabled {
  cursor: not-allowed;
}

.send-button svg {
  width: 18px;
  height: 18px;
}

.button-loading {
  width: 18px;
  height: 18px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 768px) {
  .chat-input {
    padding: 12px 16px;
  }
  
  .input-field {
    padding: 10px 12px;
  }
}
</style>
