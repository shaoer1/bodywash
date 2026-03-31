<template>
  <div class="iv">
    <div class="workspace">
      <aside class="cpanel">
        <div class="panel-title">推理配置</div>
        <div class="section">
          <div class="sec-hdr">基础模型</div>
          <div class="fgroup">
            <label>模型类型</label>
            <div class="seg"><button v-for="m in modelTypes" :key="m.l" :class="{active:inf.isSDXL===m.v}" @click="inf.isSDXL=m.v">{{ m.l }}</button></div>
          </div>
          <div class="fgroup">
            <label>底座模型路径</label>
            <div class="input-row"><input v-model="inf.modelPath" placeholder="D:\models\sd_xl_base_1.0.safetensors" /><button class="btn-scan" @click="scanBaseModels">⟳</button></div>
            <select v-if="baseModels.length" v-model="inf.modelPath" class="mt-s">
              <option value="">-- 从扫描结果选择 --</option>
              <option v-for="m in baseModels" :key="m.path" :value="m.path">{{ m.name }} ({{ m.size_mb }}MB)</option>
            </select>
          </div>
        </div>
        <div class="section">
          <div class="sec-hdr">LoRA 模型 <button class="ghost-xs" @click="addLora">+ 添加</button></div>
          <div v-for="(lora,i) in inf.loras" :key="i" class="lora-item">
            <div class="lora-head"><span>LoRA {{ i+1 }}</span><button class="del-btn" @click="removeLora(i)">✕</button></div>
            <div class="input-row"><input v-model="lora.path" placeholder="LoRA .safetensors 路径" /><button class="btn-scan" @click="scanLoraModels">⟳</button></div>
            <select v-if="loraModels.length" v-model="lora.path" class="mt-s">
              <option value="">-- 选择 LoRA --</option>
              <option v-for="m in loraModels" :key="m.path" :value="m.path">{{ m.name }} ({{ m.size_mb }}MB)</option>
            </select>
            <div class="weight-row"><label>Weight: <b>{{ lora.weight.toFixed(2) }}</b></label><input type="range" v-model.number="lora.weight" min="0" max="2" step="0.05" /></div>
          </div>
          <div v-if="!inf.loras.length" class="empty-note">未添加 LoRA（可直接生成）</div>
        </div>
        <div class="section">
          <div class="sec-hdr">提示词</div>
          <div class="fgroup"><label>正向提示词</label><textarea v-model="inf.prompt" rows="4" placeholder="masterpiece, best quality, ..."></textarea></div>
          <div class="fgroup"><label>反向提示词</label><textarea v-model="inf.negPrompt" rows="3" placeholder="deformed, blurry, ..."></textarea></div>
        </div>
        <div class="section">
          <div class="sec-hdr">生成参数</div>
          <div class="fgroup row2">
            <div><label>Width</label><div class="num-ctrl"><button @click="inf.width=Math.max(64,inf.width-64)">−</button><input type="number" v-model.number="inf.width" step="64" /><button @click="inf.width+=64">+</button></div></div>
            <div><label>Height</label><div class="num-ctrl"><button @click="inf.height=Math.max(64,inf.height-64)">−</button><input type="number" v-model.number="inf.height" step="64" /><button @click="inf.height+=64">+</button></div></div>
          </div>
          <div class="fgroup row2">
            <div><label>Steps</label><div class="num-ctrl"><button @click="inf.steps=Math.max(1,inf.steps-1)">−</button><input type="number" v-model.number="inf.steps" min="1" /><button @click="inf.steps++">+</button></div></div>
            <div><label>CFG Scale</label><div class="num-ctrl"><button @click="inf.cfgScale=Math.max(1,+(inf.cfgScale-0.5).toFixed(1))">−</button><input type="number" v-model.number="inf.cfgScale" step="0.5" /><button @click="inf.cfgScale=+(inf.cfgScale+0.5).toFixed(1)">+</button></div></div>
          </div>
          <div class="fgroup row2">
            <div><label>Seed <span class="hint">-1=随机</span></label><input type="number" v-model.number="inf.seed" /></div>
            <div><label>生成数量</label><div class="num-ctrl"><button @click="inf.numImages=Math.max(1,inf.numImages-1)">−</button><input type="number" v-model.number="inf.numImages" min="1" max="8" /><button @click="inf.numImages=Math.min(8,inf.numImages+1)">+</button></div></div>
          </div>
          <div class="fgroup">
            <label>Scheduler</label>
            <div class="seg wrap"><button v-for="s in schedulers" :key="s.v" :class="{active:inf.scheduler===s.v}" @click="inf.scheduler=s.v">{{ s.l }}</button></div>
          </div>
        </div>
        <div class="action-bar">
          <button class="btn-gen" :disabled="isGenerating" @click="generate">
            <span v-if="isGenerating" class="spinner"></span>
            <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/></svg>
            {{ isGenerating ? '生成中...' : '生成图片' }}
          </button>
        </div>
      </aside>

      <main class="gallery-panel">
        <div class="gallery-header">
          <span class="g-title">生成结果</span>
          <div class="g-meta" v-if="lastSeed !== null">Seed: <b>{{ lastSeed }}</b> &nbsp;·&nbsp; 耗时: <b>{{ lastElapsed }}s</b></div>
          <button class="ghost-xs" v-if="gallery.length" @click="gallery=[];selected=null">清空</button>
        </div>
        <div v-if="isGenerating" class="gen-progress">
          <div class="gen-bar"><div class="gen-fill" :style="{width:genPct+'%'}"></div></div>
          <span>正在生成...</span>
        </div>
        <div v-if="errorMsg" class="err-banner">{{ errorMsg }}</div>
        <div class="gallery" v-if="gallery.length">
          <div v-for="(img,i) in gallery" :key="i" class="gcard" :class="{selected:selected===i}" @click="selected=i">
            <img :src="img.src" />
            <div class="gcard-actions">
              <button @click.stop="downloadImg(img,i)">↓</button>
              <button @click.stop="reusePrompt(img)">↺</button>
            </div>
            <div class="gcard-seed">#{{ img.seed }}</div>
          </div>
        </div>
        <div v-else-if="!isGenerating" class="empty-gallery">
          <svg width="52" height="52" viewBox="0 0 24 24" fill="none" stroke="#222" stroke-width="1.2"><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/><path d="M21 15l-5-5L5 21"/></svg>
          <p>配置参数后点击生成图片</p>
          <p class="empty-note">需要后端 PyTorch + diffusers 支持</p>
        </div>
        <div class="lightbox" v-if="selected!==null && gallery[selected]" @click.self="selected=null">
          <img :src="gallery[selected].src" />
          <div class="lb-bar">
            <button @click="downloadImg(gallery[selected],selected)">下载</button>
            <button @click="selected=null">关闭</button>
          </div>
        </div>
      </main>
    </div>
  </div>
</template>
<script>
export default {
  name: 'InferenceView',
  data() {
    return {
      modelTypes: [{v:true,l:'SDXL'},{v:false,l:'SD1.5'}],
      schedulers: [{v:'euler_a',l:'Euler A'},{v:'euler',l:'Euler'},{v:'dpm++2m',l:'DPM++2M'},{v:'ddim',l:'DDIM'}],
      inf: {
        isSDXL: false, modelPath: '', loras: [],
        prompt: 'masterpiece, best quality, high detail, commercial product photography, pure white background, soft studio lighting',
        negPrompt: 'text, logo, watermark, deformed, blurry, distorted, low quality, messy background',
        width:512, height:512, steps:20, cfgScale:7.0, seed:-1, numImages:1, scheduler:'euler_a',
      },
      baseModels: [], loraModels: [],
      gallery: [], selected: null,
      isGenerating: false, genStep: 0, genTimer: null,
      errorMsg: '', lastSeed: null, lastElapsed: null,
    }
  },
  computed: {
    genPct() { return this.inf.steps ? Math.min(100, Math.round(this.genStep/this.inf.steps*100)) : 0 }
  },
  watch: {
    'inf.modelPath'(val) {
      if (!val) return
      const low = val.toLowerCase()
      if (low.includes('xl') || low.includes('sdxl')) {
        this.inf.isSDXL = true
      } else if (low.includes('v1') || low.includes('v2') || low.includes('sd1') || low.includes('1.5') || low.includes('pruned')) {
        this.inf.isSDXL = false
      }
    }
  },
  mounted() { this.scanLoraModels() },
  beforeUnmount() { if(this.genTimer) clearInterval(this.genTimer) },
  methods: {
    addLora() { this.inf.loras.push({path:'',weight:0.8}) },
    removeLora(i) { this.inf.loras.splice(i,1) },
    async scanBaseModels() {
      try {
        const r = await fetch('/api/get_models?type=base')
        const d = await r.json()
        this.baseModels = d.models || []
      } catch(e) { console.error(e) }
    },
    async scanLoraModels() {
      try {
        const r = await fetch('/api/get_models?type=lora')
        const d = await r.json()
        this.loraModels = d.models || []
      } catch(e) { console.error(e) }
    },
    async generate() {
      this.errorMsg = ''
      if (!this.inf.modelPath) { this.errorMsg = '请填写底座模型路径'; return }
      this.isGenerating = true
      this.genStep = 0
      this.genTimer = setInterval(()=>{
        if (this.genStep < this.inf.steps - 1) this.genStep++
      }, (this.inf.steps > 0 ? 60000/this.inf.steps : 1000))
      try {
        const payload = {
          model_path: this.inf.modelPath,
          is_sdxl: this.inf.isSDXL,
          loras: this.inf.loras.filter(l=>l.path),
          prompt: this.inf.prompt,
          negative_prompt: this.inf.negPrompt,
          width: this.inf.width,
          height: this.inf.height,
          steps: this.inf.steps,
          cfg_scale: this.inf.cfgScale,
          seed: this.inf.seed,
          num_images: this.inf.numImages,
          scheduler: this.inf.scheduler,
        }
        const r = await fetch('/api/generate_image', {
          method: 'POST',
          headers: {'Content-Type':'application/json'},
          body: JSON.stringify(payload)
        })
        const d = await r.json()
        if (!d.success) { this.errorMsg = d.message || '生成失败'; return }
        this.lastSeed = d.seed
        this.lastElapsed = d.elapsed
        this.genStep = this.inf.steps
        for (const src of (d.images || [])) {
          this.gallery.unshift({src, seed: d.seed, prompt: this.inf.prompt})
        }
        if (this.gallery.length > 50) this.gallery.splice(50)
      } catch(e) {
        this.errorMsg = '请求失败: ' + e.message
      } finally {
        clearInterval(this.genTimer); this.genTimer = null
        this.isGenerating = false
      }
    },
    downloadImg(img, i) {
      const a = document.createElement('a')
      a.href = img.src
      a.download = `gen_${img.seed||i}_${Date.now()}.png`
      a.click()
    },
    reusePrompt(img) {
      if (img.prompt) this.inf.prompt = img.prompt
    },
  }
}
</script>
<style scoped>
.iv{display:flex;flex-direction:column;height:calc(100vh - 56px);background:#0a0a0c;font-family:'Consolas','SF Mono','Fira Code',monospace;color:#c8c8c8;}
.workspace{display:flex;flex:1;overflow:hidden;}
.cpanel{width:320px;flex-shrink:0;background:#111114;border-right:1px solid #1a1a1e;display:flex;flex-direction:column;overflow-y:auto;}
.cpanel::-webkit-scrollbar{width:3px;}.cpanel::-webkit-scrollbar-thumb{background:#222;}
.panel-title{padding:.85rem 1rem .5rem;font-size:.65rem;font-weight:700;text-transform:uppercase;letter-spacing:2px;color:#444;border-bottom:1px solid #1a1a1e;}
.section{padding:.85rem 1rem;border-bottom:1px solid #1a1a1e;}
.sec-hdr{font-size:.63rem;font-weight:700;text-transform:uppercase;letter-spacing:1.5px;color:#555;margin-bottom:.7rem;display:flex;align-items:center;justify-content:space-between;}
.fgroup{margin-bottom:.75rem;}.fgroup:last-child{margin-bottom:0;}
.fgroup label{display:block;font-size:.68rem;font-weight:600;color:#555;margin-bottom:.28rem;text-transform:uppercase;letter-spacing:.3px;}
.hint{color:#333;font-weight:400;text-transform:none;}
.fgroup input,.fgroup textarea,.fgroup select{width:100%;background:#161619;border:1px solid #222;border-radius:5px;color:#bbb;font-size:.78rem;padding:.38rem .55rem;outline:none;font-family:inherit;transition:border-color .15s;box-sizing:border-box;}
.fgroup input:focus,.fgroup textarea:focus{border-color:#f97316;}
.fgroup textarea{resize:vertical;min-height:60px;}
.mt-s{margin-top:.3rem;}
.input-row{display:flex;gap:.3rem;}
.input-row input{flex:1;}
.btn-scan{padding:.38rem .55rem;background:#1a1a1e;border:1px solid #252528;border-radius:5px;color:#666;cursor:pointer;font-size:.85rem;transition:all .15s;flex-shrink:0;}
.btn-scan:hover{color:#f97316;border-color:#f97316;}
.seg{display:flex;background:#161619;border:1px solid #222;border-radius:5px;overflow:hidden;}
.seg.wrap{flex-wrap:wrap;}
.seg button{flex:1;padding:.33rem .2rem;font-size:.7rem;font-weight:600;color:#444;background:none;border:none;cursor:pointer;transition:all .12s;font-family:inherit;min-width:44px;}
.seg button:hover:not(.active){background:#1e1e22;color:#888;}
.seg button.active{background:#f97316;color:#fff;}
.num-ctrl{display:flex;align-items:center;background:#161619;border:1px solid #222;border-radius:5px;overflow:hidden;}
.num-ctrl button{width:26px;height:28px;background:none;border:none;color:#555;cursor:pointer;font-size:.95rem;transition:all .12s;}
.num-ctrl button:hover{background:#1e1e22;color:#f97316;}
.num-ctrl input{flex:1;background:none;border:none;color:#bbb;font-size:.78rem;text-align:center;outline:none;min-width:0;font-family:inherit;}
.row2{display:grid;grid-template-columns:1fr 1fr;gap:.5rem;}
.lora-item{background:#161619;border:1px solid #1e1e22;border-radius:7px;padding:.65rem;margin-bottom:.55rem;display:flex;flex-direction:column;gap:.4rem;}
.lora-head{display:flex;justify-content:space-between;align-items:center;font-size:.7rem;font-weight:600;color:#555;}
.del-btn{background:none;border:none;color:#444;cursor:pointer;font-size:.75rem;padding:.1rem .3rem;transition:color .15s;}
.del-btn:hover{color:#ef4444;}
.weight-row{display:flex;align-items:center;gap:.5rem;font-size:.72rem;color:#555;}
.weight-row label{flex-shrink:0;} .weight-row b{color:#f97316;}
.weight-row input[type=range]{flex:1;accent-color:#f97316;}
.empty-note{font-size:.72rem;color:#333;text-align:center;padding:.5rem 0;}
.action-bar{padding:1rem;margin-top:auto;border-top:1px solid #1a1a1e;background:#0d0d10;}
.btn-gen{width:100%;display:flex;align-items:center;justify-content:center;gap:.5rem;padding:.65rem;background:#f97316;color:#fff;border:none;border-radius:7px;font-weight:700;font-size:.82rem;cursor:pointer;font-family:inherit;transition:all .15s;}
.btn-gen:hover:not(:disabled){background:#ea6c10;}
.btn-gen:disabled{opacity:.35;cursor:not-allowed;}
.spinner{width:14px;height:14px;border:2px solid rgba(255,255,255,.3);border-top-color:#fff;border-radius:50%;animation:spin .8s linear infinite;flex-shrink:0;}
@keyframes spin{to{transform:rotate(360deg)}}
.gallery-panel{flex:1;display:flex;flex-direction:column;overflow:hidden;background:#0a0a0c;}
.gallery-header{display:flex;align-items:center;gap:1rem;padding:.65rem 1rem;background:#111114;border-bottom:1px solid #1a1a1e;flex-shrink:0;}
.g-title{font-size:.7rem;font-weight:700;text-transform:uppercase;letter-spacing:1.5px;color:#444;}
.g-meta{font-size:.72rem;color:#555;} .g-meta b{color:#aaa;}
.ghost-xs{background:none;border:1px solid #252528;color:#444;padding:.18rem .5rem;border-radius:4px;font-size:.65rem;cursor:pointer;font-family:inherit;transition:all .12s;margin-left:auto;}
.ghost-xs:hover{color:#ccc;border-color:#444;}
.gen-progress{padding:.5rem 1rem;background:#0d0d10;border-bottom:1px solid #1a1a1e;display:flex;align-items:center;gap:.75rem;font-size:.72rem;color:#555;flex-shrink:0;}
.gen-bar{flex:1;height:4px;background:#1a1a1e;border-radius:2px;overflow:hidden;}
.gen-fill{height:100%;background:#f97316;border-radius:2px;transition:width .3s;}
.err-banner{margin:.5rem 1rem;padding:.5rem .75rem;background:rgba(239,68,68,.1);border:1px solid rgba(239,68,68,.2);border-radius:6px;color:#ef4444;font-size:.75rem;}
.gallery{flex:1;overflow-y:auto;padding:1rem;display:grid;grid-template-columns:repeat(auto-fill,minmax(220px,1fr));gap:.85rem;align-content:start;}
.gallery::-webkit-scrollbar{width:3px;}.gallery::-webkit-scrollbar-thumb{background:#1e1e22;}
.gcard{position:relative;border-radius:8px;overflow:hidden;border:2px solid transparent;cursor:pointer;transition:all .15s;background:#161619;}
.gcard:hover{border-color:#f97316;transform:translateY(-2px);}
.gcard.selected{border-color:#f97316;}
.gcard img{width:100%;height:auto;display:block;}
.gcard-actions{position:absolute;top:.4rem;right:.4rem;display:flex;gap:.3rem;opacity:0;transition:opacity .15s;}
.gcard:hover .gcard-actions{opacity:1;}
.gcard-actions button{background:rgba(0,0,0,.75);border:none;color:#fff;width:26px;height:26px;border-radius:5px;cursor:pointer;font-size:.85rem;display:flex;align-items:center;justify-content:center;}
.gcard-actions button:hover{background:#f97316;}
.gcard-seed{position:absolute;bottom:0;left:0;right:0;padding:.25rem .5rem;background:rgba(0,0,0,.6);font-size:.63rem;color:#888;text-align:right;}
.empty-gallery{flex:1;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:.75rem;color:#2a2a2e;}
.empty-gallery p{font-size:.8rem;margin:0;}
.empty-gallery .empty-note{font-size:.7rem;color:#1e1e22;}
.lightbox{position:fixed;inset:0;background:rgba(0,0,0,.88);z-index:200;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:1rem;}
.lightbox img{max-width:90vw;max-height:80vh;border-radius:8px;border:1px solid #2a2a2e;}
.lb-bar{display:flex;gap:.75rem;}
.lb-bar button{padding:.5rem 1.25rem;background:#1a1a1e;border:1px solid #2a2a2e;color:#aaa;border-radius:7px;cursor:pointer;font-family:inherit;font-size:.8rem;}
.lb-bar button:hover{background:#f97316;color:#fff;border-color:#f97316;}
</style>

