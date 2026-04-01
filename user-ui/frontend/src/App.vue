<template>
  <div class="chat-container">
    <div class="chat-header">
      <h1>图片生成助手</h1>
    </div>
    <div class="chat-messages" ref="messagesContainer">
      <MessageComponent :messages="messages" />
    </div>
    <InputComponent 
      :is-generating="isGenerating"
      @send="handleSend"
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
      isGenerating: false,
      loraModels: [] // 存储所有LoRA模型信息
    }
  },
  methods: {
    async handleSend(prompt) {
      if (this.isGenerating) return

      // 添加用户消息
      this.messages.push({
        id: Date.now(),
        type: 'user',
        content: prompt
      })

      // 添加生成中的消息
      const botMessageId = Date.now() + 1
      this.messages.push({
        id: botMessageId,
        type: 'bot',
        content: prompt,
        status: 'generating',
        imageId: null,
        imageUrl: null
      })

      this.isGenerating = true
      this.scrollToBottom()

      try {
        // 根据中文提示词自动选择LoRA模型
        const loraModel = this.selectLoraModel(prompt)
        
        // 调用后端API生成图片
        const response = await ApiService.generateImage(prompt, loraModel)
        const imageId = response.imageId

        // 轮询获取图片URL
        this.pollForImage(botMessageId, imageId)
      } catch (error) {
        console.error('Error generating image:', error)
        this.updateMessageStatus(botMessageId, 'error')
        this.isGenerating = false
      }
    },

    selectLoraModel(prompt) {
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

      const normalized = (prompt || '').replace(/，/g, ',')
      const translatedTokens = normalized
        .split(',')
        .map(s => s.trim())
        .filter(Boolean)
        .map(token => tokenMap[token])
        .filter(Boolean)

      const translated = [...new Set(translatedTokens)].join(', ')
      console.log('Translated prompt:', translated)

      for (const lora of this.loraModels) {
        if (lora.triggerWord && translated.includes(lora.triggerWord)) {
          console.log('Matched LoRA model:', lora)
          return lora.fileName
        }
      }

      if (this.loraModels.length > 0) {
        return this.loraModels[0].fileName
      }
      return ''
    },

    async pollForImage(messageId, imageId) {
      let attempts = 0
      const maxAttempts = 30

      const poll = async () => {
        try {
          const response = await ApiService.getImageUrl(imageId)

          if (response.imageUrl) {
            this.updateMessageStatus(messageId, 'completed', response.imageUrl)
            this.isGenerating = false
            return
          }

          attempts++
          if (attempts < maxAttempts) {
            setTimeout(poll, 1000)
          } else {
            this.updateMessageStatus(messageId, 'error')
            this.isGenerating = false
          }
        } catch (error) {
          console.error('Error polling for image:', error)
          this.updateMessageStatus(messageId, 'error')
          this.isGenerating = false
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
        // 调用后端API获取LoRA模型列表
        const response = await ApiService.getLoraModels()
        this.loraModels = response.models || []
        console.log('Scanned LoRA models:', this.loraModels)
      } catch (error) {
        console.error('Error scanning LoRA models:', error)
        // 失败时使用默认数据
        this.loraModels = [
          { fileName: 'bodywash-black.safetensors', triggerWord: 'black' },
          { fileName: 'bodywash-blue.safetensors', triggerWord: 'blue' }
        ]
      }
    }
  },
  mounted() {
    // 检查后端服务是否可用
    ApiService.healthCheck()
      .then(data => {
        console.log('Backend service is', data.status)
        // 扫描LoRA模型
        this.scanLoraModels()
      })
      .catch(error => {
        console.error('Backend service is not available:', error)
      })
  }
}
</script>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  font-family: 'Arial', sans-serif;
}

.chat-header {
  background: rgba(255, 255, 255, 0.95);
  padding: 20px;
  text-align: center;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
}

.chat-header h1 {
  margin: 0;
  color: #333;
  font-size: 24px;
  font-weight: 600;
}

.chat-messages {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 15px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .chat-container {
    height: 100vh;
  }
  
  .chat-header {
    padding: 15px;
  }
  
  .chat-header h1 {
    font-size: 20px;
  }
  
  .chat-messages {
    padding: 15px;
  }
}
</style>