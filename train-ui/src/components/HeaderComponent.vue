<template>
  <header class="hdr">
    <div class="hdr-inner">
      <div class="logo">
        <svg width="26" height="26" viewBox="0 0 24 24" fill="none">
          <path d="M12 2L2 7l10 5 10-5-10-5z" fill="#f97316"/>
          <path d="M2 17l10 5 10-5" stroke="#f97316" stroke-width="1.5" stroke-linecap="round"/>
          <path d="M2 12l10 5 10-5" stroke="rgba(249,115,22,0.45)" stroke-width="1.5" stroke-linecap="round"/>
        </svg>
        <span class="logo-text">LoRA <em>Studio</em></span>
        <span class="logo-chip">Kohya</span>
      </div>
      <nav class="nav">
        <router-link
          v-for="r in routes" :key="r.path" :to="r.path"
          class="nav-link" :class="{active: $route.path===r.path}">
          <span class="nav-icon" v-html="r.icon"></span>
          {{ r.name }}
        </router-link>
      </nav>
      <div class="hdr-right">
        <div class="sys-pill" @click="checkStatus" :title="statusMsg">
          <span class="sys-dot" :class="dotCls"></span>
          <span class="sys-txt">{{ statusTxt }}</span>
        </div>
      </div>
    </div>
  </header>
</template>

<script>
export default {
  name: 'HeaderComponent',
  data() {
    return {
      status: 'checking',
      statusMsg: '',
      routes: [
        {
          path: '/training', name: 'LoRA 训练',
          icon: '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>'
        },
        {
          path: '/inference', name: '模型推理',
          icon: '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="5 3 19 12 5 21 5 3"/></svg>'
        },
        {
          path: '/label', name: '数据打标',
          icon: '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20.59 13.41l-7.17 7.17a2 2 0 01-2.83 0L2 12V2h10l8.59 8.59a2 2 0 010 2.82z"/><line x1="7" y1="7" x2="7.01" y2="7"/></svg>'
        },
        {
          path: '/settings', name: '系统设置',
          icon: '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.07 4.93a10 10 0 010 14.14M4.93 4.93a10 10 0 000 14.14"/></svg>'
        },
      ]
    }
  },
  computed: {
    dotCls() {
      return {
        'dot-ok':   this.status === 'ready',
        'dot-warn': this.status === 'checking',
        'dot-err':  this.status === 'error',
      }
    },
    statusTxt() {
      return { ready: '系统就绪', checking: '检测中', error: '后端离线' }[this.status] || ''
    }
  },
  mounted() { this.checkStatus(); this.timer = setInterval(this.checkStatus, 30000) },
  beforeUnmount() { clearInterval(this.timer) },
  methods: {
    async checkStatus() {
      this.status = 'checking'
      try {
        const r = await fetch('/api/check_environment')
        const d = await r.json()
        this.statusMsg = d.kohya_installed ? 'Kohya_ss 就绪' : '后端在线，Kohya_ss 未找到'
        this.status = d.system_ready ? 'ready' : 'error'
      } catch {
        this.status = 'error'
        this.statusMsg = '无法连接后端'
      }
    }
  }
}
</script>

<style scoped>
.hdr {
  background: #0e0e12;
  border-bottom: 1px solid #1e1e24;
  height: 56px;
  flex-shrink: 0;
  position: sticky;
  top: 0;
  z-index: 100;
}
.hdr-inner {
  display: flex;
  align-items: center;
  height: 100%;
  padding: 0 1.25rem;
  gap: 1.5rem;
}
.logo { display: flex; align-items: center; gap: .55rem; flex-shrink: 0; }
.logo-text { font-size: .95rem; font-weight: 700; color: #ddd; letter-spacing: -.3px; }
.logo-text em { color: #f97316; font-style: normal; }
.logo-chip { font-size: .58rem; font-weight: 700; background: rgba(249,115,22,.12); color: #f97316; border: 1px solid rgba(249,115,22,.22); padding: .1rem .4rem; border-radius: 4px; letter-spacing: .5px; }
.nav { display: flex; align-items: center; gap: .2rem; flex: 1; }
.nav-link {
  display: flex; align-items: center; gap: .4rem;
  color: #555; text-decoration: none;
  padding: .38rem .8rem; border-radius: 6px;
  font-size: .78rem; font-weight: 600;
  transition: all .15s; letter-spacing: .2px;
}
.nav-icon { display: flex; align-items: center; opacity: .65; }
.nav-link:hover { color: #ccc; background: #1a1a1e; }
.nav-link.active { color: #f97316; background: rgba(249,115,22,.1); }
.nav-link.active .nav-icon { opacity: 1; }
.hdr-right { margin-left: auto; }
.sys-pill {
  display: flex; align-items: center; gap: .4rem;
  padding: .28rem .7rem; border: 1px solid #1e1e24;
  border-radius: 20px; cursor: pointer;
  background: #0a0a0c; transition: border-color .2s;
}
.sys-pill:hover { border-color: #2a2a30; }
.sys-dot { width: 7px; height: 7px; border-radius: 50%; flex-shrink: 0; animation: pulse 2s infinite; }
.dot-ok   { background: #22c55e; }
.dot-warn { background: #eab308; }
.dot-err  { background: #ef4444; }
@keyframes pulse { 0%,100%{opacity:1} 50%{opacity:.35} }
.sys-txt { font-size: .7rem; font-weight: 600; color: #555; white-space: nowrap; }
@media(max-width: 680px) {
  .logo-chip, .hdr-right { display: none; }
  .nav-link .nav-icon { display: none; }
}
</style>
