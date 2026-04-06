<template>
  <div class="chat-container">
    <div class="chat-header">
      <div class="header-content">
        <div class="logo">
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect x="3" y="3" width="18" height="18" rx="2" stroke="currentColor" stroke-width="2"/>
            <circle cx="8.5" cy="8.5" r="1.5" fill="currentColor"/>
            <path d="M21 15L16 10L5 21" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <h1>图片生成</h1>
      </div>
      <div class="status-indicator" :class="{ active: isBackendAvailable }">
        <span class="status-dot"></span>
      </div>
    </div>
    <div class="chat-messages" ref="messagesContainer">
      <div v-if="messages.length === 0" class="welcome-message">
        <div class="welcome-icon">
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect x="3" y="3" width="18" height="18" rx="2" stroke="currentColor" stroke-width="2"/>
            <circle cx="8.5" cy="8.5" r="1.5" fill="currentColor"/>
            <path d="M21 15L16 10L5 21" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <h2>输入描述生成图片</h2>
      </div>
      <MessageComponent :messages="messages" />
    </div>
    <InputComponent 
      @send="handleSend"
      ref="inputComponent"
    />
  </div>
</template>

<script>
import MessageComponent from './components/MessageComponent.vue'
import InputComponent from './components/InputComponent.vue'
import ApiService from './services/ApiService.js'

export default {
  name: 'App',
  components: {
    MessageComponent,
    InputComponent
  },
  data() {
    return {
      messages: [],
      loraModels: [],
      isBackendAvailable: false
    }
  },
  methods: {
    async handleSend(prompt) {
      this.messages.push({
        id: Date.now(),
        type: 'user',
        content: prompt
      })

      const botMessageId = Date.now() + 1
      this.messages.push({
        id: botMessageId,
        type: 'bot',
        content: prompt,
        status: 'generating',
        imageId: null,
        imageUrl: null
      })

      this.scrollToBottom()

      try {
        const { loraModel, finalPrompt } = this.selectLoraModel(prompt)
        const response = await ApiService.generateImage(finalPrompt, loraModel)
        
        const message = this.messages.find(m => m.id === botMessageId)
        if (message) {
          if (response.imageId) {
            message.imageId = response.imageId
            message.status = 'generating'
            // 轮询获取图片URL
            this.pollForImage(botMessageId, response.imageId)
          } else {
            message.status = 'error'
          }
        }
      } catch (error) {
        console.error('Error generating image:', error)
        this.updateMessageStatus(botMessageId, 'error')
      }
    },
    selectLoraModel(prompt) {
      try {
        const tokenMap = {
          '沐浴露': 'body wash',
          '旋盖式': 'screw cap',
          '旋盖': 'screw cap',
          '压泵式': 'pump dispenser',
          '压泵': 'pump dispenser',
          '泵头式': 'pump dispenser',
          '泵头': 'pump dispenser',
          '翻盖式': 'flip cap',
          '翻盖': 'flip cap',
          '喷雾式': 'spray nozzle',
          '喷雾': 'spray nozzle',
          '长方形': 'rectangular',
          '方形': 'square',
          '圆形': 'round',
          '修长': 'tall and slender',
          '矮胖': 'short and wide',
          '均匀': 'balanced proportion',
          '白色': 'white',
          '黑色': 'black',
          '黄色': 'yellow',
          '红色': 'red',
          '蓝色': 'blue'
        }

        let bodyWashAdded = false
        let otherAttributes = ''
        let processedPrompt = prompt || ''
        let selectedColor = ''
        
        // 优先处理完整短语
        const phrases = [
          '翻盖式', '旋盖式', '压泵式', '泵头式', '喷雾式',
          '沐浴露', '修长', '矮胖', '均匀',
          '白色', '黑色', '黄色', '红色', '蓝色',
          '长方形', '方形', '圆形'
        ]
        
        phrases.forEach(phrase => {
          if (processedPrompt.includes(phrase)) {
            const translatedPhrase = tokenMap[phrase]
            
            // 检查是否是沐浴露
            if (phrase === '沐浴露') {
              bodyWashAdded = true
            } 
            // 检查是否是颜色词
            else if (['白色', '黑色', '黄色', '红色', '蓝色'].includes(phrase)) {
              selectedColor = translatedPhrase
            } 
            // 其他属性
            else {
              otherAttributes += (otherAttributes ? ', ' : '') + translatedPhrase
            }
            
            processedPrompt = processedPrompt.replace(phrase, '')
          }
        })
        
        // 构建最终提示词
        let finalPrompt = 'body wash'
        let loraModel = ''
        
        // 优先根据颜色选择 lora 模型
        if (selectedColor) {
          for (const lora of this.loraModels) {
            if (lora.fileName.includes(selectedColor)) {
              console.log('Matched LoRA model by color:', lora)
              loraModel = lora.fileName
              // 构建提示词：触发词, body wash, 其他属性, 颜色
              let promptParts = []
              if (lora.triggerWord) {
                promptParts.push(lora.triggerWord)
              }
              promptParts.push('body wash')
              if (otherAttributes) {
                promptParts.push(otherAttributes)
              }
              if (selectedColor) {
                promptParts.push(selectedColor)
              }
              finalPrompt = promptParts.join(', ')
              console.log('Final prompt:', finalPrompt)
              break
            }
          }
        }

        // 如果没有根据颜色匹配到 lora 模型，再根据触发词匹配
        if (!loraModel) {
          for (const lora of this.loraModels) {
            if (lora.triggerWord && (otherAttributes.includes(lora.triggerWord) || selectedColor === lora.triggerWord)) {
              console.log('Matched LoRA model by trigger word:', lora)
              loraModel = lora.fileName
              // 构建提示词：触发词, body wash, 其他属性, 颜色
              let promptParts = []
              if (lora.triggerWord) {
                promptParts.push(lora.triggerWord)
              }
              promptParts.push('body wash')
              if (otherAttributes) {
                promptParts.push(otherAttributes)
              }
              if (selectedColor) {
                promptParts.push(selectedColor)
              }
              finalPrompt = promptParts.join(', ')
              console.log('Final prompt:', finalPrompt)
              break
            }
          }
        }

        if (!loraModel && this.loraModels.length > 0) {
          loraModel = this.loraModels[0].fileName
          const selectedLora = this.loraModels[0]
          // 构建提示词：触发词, body wash, 其他属性, 颜色
          let promptParts = []
          if (selectedLora.triggerWord) {
            promptParts.push(selectedLora.triggerWord)
          }
          promptParts.push('body wash')
          if (otherAttributes) {
            promptParts.push(otherAttributes)
          }
          if (selectedColor) {
            promptParts.push(selectedColor)
          }
          finalPrompt = promptParts.join(', ')
          console.log('Final prompt:', finalPrompt)
        }
        
        return { loraModel, finalPrompt }
      } catch (error) {
        console.error('Error in selectLoraModel:', error)
        return { loraModel: '', finalPrompt: '' }
      }
    },
    async pollForImage(messageId, imageId) {
      let attempts = 0
      const maxAttempts = 60

      const poll = async () => {
        try {
          const response = await ApiService.getImageUrl(imageId)
          if (response.imageUrl) {
            this.updateMessageStatus(messageId, 'completed', response.imageUrl)
            return
          }

          attempts++
          if (attempts < maxAttempts) {
            setTimeout(poll, 2000)
          } else {
            this.updateMessageStatus(messageId, 'error')
          }
        } catch (error) {
          console.error('Error polling for image:', error)
          this.updateMessageStatus(messageId, 'error')
        }
      }
      poll()
    },
    updateMessageStatus(messageId, status, imageUrl = null) {
      const message = this.messages.find(m => m.id === messageId)
      if (message) {
        message.status = status
        message.imageUrl = imageUrl
        this.scrollToBottom()
      }
    },
    scrollToBottom() {
      setTimeout(() => {
        const container = this.$refs.messagesContainer
        if (container) {
          container.scrollTop = container.scrollHeight
        }
      }, 100)
    },
    async scanLoraModels() {
      try {
        const response = await ApiService.getLoraModels()
        this.loraModels = response.models || []
        console.log('Scanned LoRA models:', this.loraModels)
      } catch (error) {
        console.error('Error scanning LoRA models:', error)
        this.loraModels = [
          { fileName: 'bodywash-black.safetensors', triggerWord: 'black' },
          { fileName: 'bodywash-blue.safetensors', triggerWord: 'blue' },
          { fileName: 'bodywash-white.safetensors', triggerWord: 'white' }
        ]
      }
    },
    async refreshImageUrls() {
      for (const message of this.messages) {
        if (message.type === 'bot' && message.imageId && !message.imageUrl) {
          try {
            const response = await ApiService.getImageUrl(message.imageId)
            if (response.imageUrl) {
              message.imageUrl = response.imageUrl
              message.status = 'completed'
            }
          } catch (error) {
            console.error('Error refreshing image URL:', error)
          }
        }
      }
    }
  },
  watch: {
    messages: {
      handler(newMessages) {
        ApiService.saveMessages(newMessages)
      },
      deep: true
    }
  },
  mounted() {
    ApiService.healthCheck()
      .then(data => {
        console.log('Backend service is', data.status)
        this.isBackendAvailable = true
        this.scanLoraModels()
        this.refreshImageUrls()
        // 获取消息
        ApiService.getMessages()
          .then(messages => {
            this.messages = messages
          })
          .catch(error => {
            console.error('Error getting messages:', error)
          })
      })
      .catch(error => {
        console.error('Backend service is not available:', error)
        this.isBackendAvailable = false
      })
  }
}
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}
body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
  background: #f5f5f5;
  min-height: 100vh;
}
#app {
  height: 100vh;
}
</style>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f5f5f5;
}
.chat-header {
  background: white;
  padding: 16px 24px;
  border-bottom: 1px solid #e5e5e5;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.header-content {
  display: flex;
  align-items: center;
  gap: 12px;
}
.logo {
  width: 24px;
  height: 24px;
  color: #6366f1;
}
.chat-header h1 {
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
  margin: 0;
}
.status-indicator {
  position: relative;
}
.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #e5e7eb;
  display: block;
}
.status-indicator.active .status-dot {
  background: #10b981;
  box-shadow: 0 0 0 2px rgba(16, 185, 129, 0.3);
}
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.welcome-message {
  text-align: center;
  padding: 60px 24px;
  color: #6b7280;
}
.welcome-icon {
  width: 48px;
  height: 48px;
  margin: 0 auto 16px;
  color: #d1d5db;
}
.welcome-message h2 {
  font-size: 18px;
  font-weight: 500;
  margin: 0;
}
.chat-input {
  background: white;
  padding: 16px 24px;
  border-top: 1px solid #e5e5e5;
  position: relative;
}
.input-wrapper {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}
.input-field {
  flex: 1;
  min-height: 44px;
  padding: 10px 16px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  font-size: 14px;
  resize: none;
  outline: none;
  transition: border-color 0.2s;
}
.input-field:focus {
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
}
.send-button {
  width: 44px;
  height: 44px;
  border: none;
  border-radius: 8px;
  background: #6366f1;
  color: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background-color 0.2s;
  flex-shrink: 0;
}
.send-button:hover {
  background: #4f46e5;
}
.send-button:disabled {
  background: #e5e7eb;
  cursor: not-allowed;
}
.send-button svg {
  width: 20px;
  height: 20px;
}
.send-button.active {
  background: #6366f1;
}
.message {
  display: flex;
  gap: 12px;
  max-width: 80%;
  animation: fadeIn 0.3s ease;
}
.message.user {
  align-self: flex-end;
  flex-direction: row-reverse;
}
.message.bot {
  align-self: flex-start;
}
.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.message.user .message-avatar {
  background: #6366f1;
  color: white;
}
.message.bot .message-avatar {
  background: #f3f4f6;
  color: #6b7280;
}
.message-content {
  flex: 1;
}
.message-text {
  padding: 12px 16px;
  border-radius: 16px;
  font-size: 14px;
  line-height: 1.4;
}
.message.user .message-text {
  background: #6366f1;
  color: white;
  border-bottom-right-radius: 4px;
}
.message.bot .message-text {
  background: #f3f4f6;
  color: #1f2937;
  border-bottom-left-radius: 4px;
}
.message-image {
  margin-top: 8px;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}
.message-image img {
  width: 100%;
  height: auto;
  display: block;
}
.message-status {
  font-size: 12px;
  color: #9ca3af;
  margin-top: 4px;
  text-align: left;
}
.message.bot .message-status {
  text-align: left;
}
.message.user .message-status {
  text-align: right;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}
@media (max-width: 768px) {
  .chat-messages { padding: 16px; }
  .chat-header { padding: 12px 16px; }
  .chat-input { padding: 12px 16px; }
  .message { max-width: 90%; }
}
</style>