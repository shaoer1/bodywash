<template>
  <footer class="footer">
    <div class="container">
      <div class="footer-content">
        <div class="footer-info">
          <div class="footer-logo">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M12 2L2 7L12 12L22 7L12 2Z" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M2 17L12 22L22 17" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M2 12L12 17L22 12" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>
          <h3>AI模型训练系统</h3>
          <p>基于SDXL 1.0的LoRA微调训练平台</p>
        </div>
      </div>
      <div class="footer-bottom">
        <div class="footer-copyright">
          <p>© {{ new Date().getFullYear() }} AI模型训练系统</p>
        </div>
      </div>
    </div>
  </footer>
</template>

<script>
export default {
  name: 'FooterComponent'
}
</script>

<style scoped>
.footer {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 3rem 0 2rem;
  margin-top: 3rem;
  position: relative;
  overflow: hidden;
}

.footer::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: linear-gradient(90deg, #ffffff, #f0f0f0, #ffffff);
  opacity: 0.3;
}

.footer-content {
  display: flex;
  justify-content: center;
  text-align: center;
  margin-bottom: 2rem;
  position: relative;
  z-index: 1;
}

.footer-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
}

.footer-logo {
  background: rgba(255,255,255,0.2);
  padding: 1rem;
  border-radius: 16px;
  backdrop-filter: blur(10px);
  transition: transform 0.3s ease;
}

.footer-logo:hover {
  transform: scale(1.05);
}

.footer-info h3 {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 700;
  color: white;
  background: linear-gradient(90deg, #ffffff, #f0f0f0);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.footer-info p {
  margin: 0;
  opacity: 0.85;
  font-size: 1rem;
  max-width: 500px;
  line-height: 1.6;
}

.footer-bottom {
  border-top: 1px solid rgba(255,255,255,0.1);
  padding-top: 2rem;
  text-align: center;
  position: relative;
  z-index: 1;
}

.footer-copyright p {
  margin: 0;
  opacity: 0.7;
  font-size: 0.9rem;
  font-weight: 500;
  transition: opacity 0.3s ease;
}

.footer-copyright p:hover {
  opacity: 1;
}

/* 装饰元素 */
.footer::after {
  content: '';
  position: absolute;
  top: 20%;
  right: -5%;
  width: 30%;
  height: 60%;
  background: radial-gradient(circle, rgba(255,255,255,0.1) 0%, transparent 70%);
  border-radius: 50%;
  z-index: 0;
}

.footer::before {
  content: '';
  position: absolute;
  bottom: 20%;
  left: -5%;
  width: 30%;
  height: 60%;
  background: radial-gradient(circle, rgba(255,255,255,0.1) 0%, transparent 70%);
  border-radius: 50%;
  z-index: 0;
}

@media (max-width: 768px) {
  .footer {
    padding: 2rem 0 1.5rem;
  }
  
  .footer-info h3 {
    font-size: 1.25rem;
  }
  
  .footer-info p {
    font-size: 0.9rem;
    padding: 0 1rem;
  }
  
  .footer-bottom {
    padding-top: 1.5rem;
  }
  
  .footer-copyright p {
    font-size: 0.8rem;
  }
  
  .footer-logo {
    padding: 0.75rem;
  }
  
  .footer-logo svg {
    width: 24px;
    height: 24px;
  }
}
</style>