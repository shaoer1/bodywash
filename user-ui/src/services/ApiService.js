class ApiService {
  constructor() {
    this.baseUrl = '/api'
  }

  async generateImage(prompt, loraModel) {
    try {
      const response = await fetch(`${this.baseUrl}/image/generate`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          prompt: prompt,
          loraModel: loraModel
        })
      })

      if (!response.ok) {
        throw new Error('API request failed')
      }

      return await response.json()
    } catch (error) {
      console.error('Error generating image:', error)
      throw error
    }
  }

  async getImageUrl(imageId) {
    try {
      const response = await fetch(`${this.baseUrl}/image/get/${imageId}`)

      if (!response.ok) {
        throw new Error('API request failed')
      }

      return await response.json()
    } catch (error) {
      console.error('Error getting image URL:', error)
      throw error
    }
  }

  async healthCheck() {
    try {
      const response = await fetch(`${this.baseUrl}/image/health`)

      if (!response.ok) {
        throw new Error('API request failed')
      }

      return await response.json()
    } catch (error) {
      console.error('Error checking health:', error)
      throw error
    }
  }

  async getLoraModels() {
    try {
      const response = await fetch(`${this.baseUrl}/lora/models`)

      if (!response.ok) {
        throw new Error('API request failed')
      }

      return await response.json()
    } catch (error) {
      console.error('Error getting LoRA models:', error)
      throw error
    }
  }

  async getMessages() {
    try {
      const response = await fetch(`${this.baseUrl}/message`)

      if (!response.ok) {
        throw new Error('API request failed')
      }

      return await response.json()
    } catch (error) {
      console.error('Error getting messages:', error)
      return []
    }
  }

  async saveMessages(messages) {
    try {
      const response = await fetch(`${this.baseUrl}/message`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(messages)
      })

      if (!response.ok) {
        throw new Error('API request failed')
      }
    } catch (error) {
      console.error('Error saving messages:', error)
    }
  }

  async clearMessages() {
    try {
      const response = await fetch(`${this.baseUrl}/message`, {
        method: 'DELETE'
      })

      if (!response.ok) {
        throw new Error('API request failed')
      }
    } catch (error) {
      console.error('Error clearing messages:', error)
    }
  }
}

export default new ApiService()