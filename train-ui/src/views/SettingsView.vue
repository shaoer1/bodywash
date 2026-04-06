<template>
  <div class="sv">
    <div class="sv-inner">
      <div class="sv-col">
        <div class="card">
          <div class="card-hdr">路径配置</div>
          <div class="fgroup"><label>Kohya_ss 路径</label><input v-model="s.kohya_path" placeholder="D:\kohya_ss" /></div>
          <div class="fgroup"><label>底座模型目录</label><input v-model="s.models_dir" placeholder="D:\models" /></div>
          <div class="fgroup"><label>输出目录</label><input v-model="s.output_dir" /></div>
          <div class="fgroup"><label>数据集根目录</label><input v-model="s.dataset_base" /></div>
          <div class="fgroup"><label>推理默认模型</label><input v-model="s.inference_model" placeholder="推理页默认底座模型" /></div>
          <button class="btn-save" @click="saveSettings">{{ saving ? '保存中...' : '保存设置' }}</button>
          <div class="save-ok" v-if="saveMsg">{{ saveMsg }}</div>
        </div>
        <div class="card">
          <div class="card-hdr">Kohya_ss 状态</div>
          <div class="srow"><span>安装状态</span><span :class="kohya.installed?'ok':'err'">{{ kohya.installed?'✓ 已安装':'✗ 未找到' }}</span></div>
          <div class="srow"><span>路径</span><span class="mono">{{ kohya.path||'--' }}</span></div>
          <div class="srow"><span>Python</span><span class="mono">{{ kohya.python||'--' }}</span></div>
          <div class="srow"><span>Accelerate</span><span class="mono">{{ kohya.accelerate||'--' }}</span></div>
          <div v-if="!kohya.installed" class="warn-box">Kohya_ss 未找到，请检查路径后保存设置并重新检测。</div>
        </div>
      </div>
      <div class="sv-col">
        <div class="card">
          <div class="card-hdr">系统环境 <button class="ghost-xs" @click="checkEnv" :disabled="checking">{{ checking?'检测中...':'重新检测' }}</button></div>
          <div class="env-grid">
            <div class="env-item"><div class="ei-lbl">Python</div><div class="ei-val">{{ env.python||'--' }}</div></div>
            <div class="env-item"><div class="ei-lbl">PyTorch</div><div class="ei-val" :class="env.pytorch?'ok':'err'">{{ env.pytorch||'未安装' }}</div></div>
            <div class="env-item"><div class="ei-lbl">CUDA</div><div class="ei-val" :class="env.cuda_available?'ok':'err'">{{ env.cuda_available?('✓ '+env.cuda_version):'✗ 不可用' }}</div></div>
            <div class="env-item"><div class="ei-lbl">GPU</div><div class="ei-val" :class="env.gpu_count?'ok':'err'">{{ env.gpu_count??'--' }} 张</div></div>
          </div>
          <div v-if="env.gpus&&env.gpus.length" class="gpu-list">
            <div v-for="g in env.gpus" :key="g.index" class="gpu-item">
              <span class="gpu-idx">GPU{{ g.index }}</span>
              <span class="gpu-name">{{ g.name }}</span>
              <span class="gpu-mem">{{ g.memory_total }}GB</span>
            </div>
          </div>
          <div class="dep-title">依赖库</div>
          <div class="dep-grid">
            <div v-for="(ver,dep) in (env.dependencies||{})" :key="dep" class="dep-item" :class="ver?'dep-ok':'dep-err'">
              <span>{{ dep }}</span><span>{{ ver||'未安装' }}</span>
            </div>
          </div>
          <div class="env-overall" :class="env.system_ready?'overall-ok':'overall-warn'">
            {{ env.system_ready?'✓ 环境就绪，可以训练':'⚠ 环境不完整，请检查依赖' }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'SettingsView',
  data() {
    return {
      s: {kohya_path:'',models_dir:'',output_dir:'',dataset_base:'',inference_model:''},
      kohya: {installed:false,path:'',python:'',accelerate:''},
      env: {},
      checking: false, saving: false, saveMsg: '',
    }
  },
  async mounted() { await this.loadSettings(); await this.checkEnv() },
  methods: {
    async loadSettings() {
      try { const r=await fetch('/api/settings'); Object.assign(this.s, await r.json()) } catch {}
    },
    async saveSettings() {
      this.saving=true; this.saveMsg=''
      try {
        const r=await fetch('/api/settings',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(this.s)})
        const d=await r.json()
        this.saveMsg=d.success?'✓ 设置已保存':(d.message||'保存失败')
        if(d.success){setTimeout(()=>{this.saveMsg=''},3000);await this.checkEnv()}
      } catch(e){this.saveMsg='保存失败: '+e.message}
      finally{this.saving=false}
    },
    async checkEnv() {
      this.checking=true
      try {
        const [er,kr]=await Promise.all([fetch('/api/check_environment'),fetch('/api/kohya_status')])
        this.env=await er.json(); this.kohya=await kr.json()
      } catch(e){console.error(e)}
      finally{this.checking=false}
    },
  }
}
</script>

<style scoped>
.sv{min-height:calc(100vh - 56px);background:#0a0a0c;font-family:'Consolas','SF Mono','Fira Code',monospace;color:#c8c8c8;padding:1.25rem;}
.sv-inner{display:grid;grid-template-columns:1fr 1fr;gap:1.25rem;max-width:1200px;margin:0 auto;}
@media(max-width:900px){.sv-inner{grid-template-columns:1fr;}}
.sv-col{display:flex;flex-direction:column;gap:1.25rem;}
.card{background:#111114;border:1px solid #1a1a1e;border-radius:10px;padding:1.25rem;}
.card-hdr{font-size:.65rem;font-weight:700;text-transform:uppercase;letter-spacing:2px;color:#444;margin-bottom:1rem;display:flex;align-items:center;justify-content:space-between;}
.fgroup{margin-bottom:.75rem;}
.fgroup label{display:block;font-size:.68rem;font-weight:600;color:#555;margin-bottom:.28rem;text-transform:uppercase;letter-spacing:.3px;}
.fgroup input{width:100%;background:#161619;border:1px solid #222;border-radius:5px;color:#bbb;font-size:.78rem;padding:.4rem .6rem;outline:none;font-family:inherit;transition:border-color .15s;box-sizing:border-box;}
.fgroup input:focus{border-color:#f97316;}
.btn-save{width:100%;padding:.6rem;background:#f97316;color:#fff;border:none;border-radius:7px;font-size:.78rem;font-weight:700;cursor:pointer;font-family:inherit;transition:background .15s;margin-top:.5rem;}
.btn-save:hover{background:#ea6c10;}
.save-ok{margin-top:.5rem;font-size:.75rem;color:#22c55e;text-align:center;}
.srow{display:flex;justify-content:space-between;align-items:flex-start;padding:.4rem 0;border-bottom:1px solid #161619;gap:.5rem;font-size:.72rem;}
.srow span:first-child{color:#444;flex-shrink:0;}
.mono{color:#666;word-break:break-all;text-align:right;font-size:.7rem;}
.ok{color:#22c55e;font-weight:600;}
.err{color:#ef4444;font-weight:600;}
.warn-box{margin-top:.75rem;padding:.65rem;background:rgba(234,179,8,.08);border:1px solid rgba(234,179,8,.2);border-radius:6px;font-size:.73rem;color:#ca8a04;line-height:1.5;}
.ghost-xs{background:none;border:1px solid #252528;color:#444;padding:.18rem .5rem;border-radius:4px;font-size:.63rem;cursor:pointer;font-family:inherit;}
.ghost-xs:hover:not(:disabled){color:#ccc;border-color:#444;}
.ghost-xs:disabled{opacity:.4;cursor:not-allowed;}
.env-grid{display:grid;grid-template-columns:1fr 1fr;gap:.5rem;margin-bottom:.85rem;}
.env-item{background:#161619;border-radius:6px;padding:.5rem .65rem;}
.ei-lbl{font-size:.63rem;color:#444;text-transform:uppercase;letter-spacing:.5px;margin-bottom:.2rem;}
.ei-val{font-size:.78rem;color:#bbb;font-weight:600;}
.gpu-list{margin-bottom:.85rem;display:flex;flex-direction:column;gap:.35rem;}
.gpu-item{display:flex;align-items:center;gap:.6rem;background:#161619;border-radius:6px;padding:.4rem .65rem;}
.gpu-idx{font-size:.65rem;font-weight:700;color:#f97316;flex-shrink:0;}
.gpu-name{font-size:.75rem;color:#bbb;flex:1;}
.gpu-mem{font-size:.72rem;color:#666;flex-shrink:0;}
.dep-title{font-size:.63rem;font-weight:700;text-transform:uppercase;letter-spacing:1px;color:#333;margin-bottom:.5rem;}
.dep-grid{display:grid;grid-template-columns:1fr 1fr;gap:.35rem;margin-bottom:.85rem;}
.dep-item{display:flex;justify-content:space-between;align-items:center;padding:.35rem .55rem;border-radius:5px;font-size:.72rem;}
.dep-ok{background:rgba(34,197,94,.07);color:#22c55e;border:1px solid rgba(34,197,94,.15);}
.dep-err{background:rgba(239,68,68,.07);color:#ef4444;border:1px solid rgba(239,68,68,.15);}
.env-overall{padding:.65rem .85rem;border-radius:7px;font-size:.78rem;font-weight:600;text-align:center;}
.overall-ok{background:rgba(34,197,94,.1);color:#22c55e;border:1px solid rgba(34,197,94,.2);}
.overall-warn{background:rgba(234,179,8,.1);color:#ca8a04;border:1px solid rgba(234,179,8,.2);}
.tips{display:flex;flex-direction:column;gap:.55rem;}
.tip{font-size:.75rem;color:#555;line-height:1.6;padding:.5rem .65rem;background:#161619;border-radius:5px;border-left:2px solid #f97316;}
.tip b{color:#aaa;}
</style>
