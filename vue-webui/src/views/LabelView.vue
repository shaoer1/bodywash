<template>
  <div class="lv">
    <!-- 目录浏览弹窗 -->
    <div class="modal-overlay" v-if="browseOpen" @click.self="browseOpen=false">
      <div class="modal">
        <div class="modal-hdr">
          <span>选择文件夹</span>
          <button @click="browseOpen=false">✕</button>
        </div>
        <div class="browse-path">{{ browseCurrent }}</div>
        <div class="browse-drives">
          <button v-for="d in browseDrives" :key="d.path" class="drive-btn" @click="browseNav(d.path)">{{ d.name }}</button>
        </div>
        <div class="browse-list">
          <div class="browse-item parent" @click="browseNav(browseParent)">.. 上级目录</div>
          <div v-for="e in browseEntries" :key="e.path" class="browse-item" @click="browseNav(e.path)">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="#f97316"><path d="M22 19a2 2 0 01-2 2H4a2 2 0 01-2-2V5a2 2 0 012-2h5l2 3h9a2 2 0 012 2z"/></svg>
            {{ e.name }}
          </div>
        </div>
        <div class="browse-footer">
          <span class="browse-sel">已选: {{ browseCurrent }}</span>
          <button class="btn-confirm" @click="confirmBrowse">确认选择</button>
        </div>
      </div>
    </div>

    <div class="lv-top">
      <div class="lv-title">数据打标工具</div>
      <div class="lv-desc">根据图片文件名自动生成 LoRA 训练 caption，重命名为 001/002... 并生成对应 txt</div>
    </div>

    <div class="lv-body">
      <!-- 左侧配置 -->
      <div class="lv-left">
        <div class="card">
          <div class="card-hdr">配置</div>
          <div class="fgroup">
            <label>图片文件夹路径</label>
            <div class="input-row">
              <input v-model="folder" placeholder="D:\dataset\my_product" />
              <button class="btn-browse" @click="openBrowse" title="浏览">📁</button>
            </div>
          </div>
          <div class="fgroup">
            <label>触发词（Trigger Word，放在第一位）</label>
            <input v-model="trigger" placeholder="white" />
          </div>
          <div class="btn-row">
            <button class="btn-scan" @click="scanFolder" :disabled="!folder||scanning">{{ scanning ? '扫描中...' : '扫描预览' }}</button>
            <button class="btn-run" @click="runLabel" :disabled="!items.length||running">{{ running ? '处理中...' : '执行打标' }}</button>
          </div>
          <div class="result-msg" v-if="resultMsg" :class="resultOk?'msg-ok':'msg-err'">{{ resultMsg }}</div>
        </div>

        <!-- 映射表 -->
        <div class="card map-card">
          <div class="card-hdr">
            中文→英文映射表
            <button class="btn-add-map" @click="addMapRow">+ 添加</button>
          </div>
          <div class="map-list">
            <div v-for="(row, idx) in mapRows" :key="idx" class="map-row">
              <input class="map-input" v-model="row.zh" placeholder="中文" />
              <span class="map-arrow">→</span>
              <input class="map-input" v-model="row.en" placeholder="English tag" />
              <button class="btn-del-map" @click="delMapRow(idx)">✕</button>
            </div>
          </div>
          <button class="btn-save-map" @click="saveMap">保存映射表</button>
        </div>
      </div>

      <!-- 右侧预览 -->
      <div class="lv-right">
        <div class="card preview-card">
          <div class="card-hdr">预览 <span class="count-badge" v-if="items.length">{{ items.length }} 张</span></div>
          <div v-if="!items.length" class="empty-hint">填写文件夹路径后点击「扫描预览」</div>
          <div class="items-list" v-else>
            <div v-for="item in items" :key="item.idx" class="label-item">
              <div class="item-head">
                <span class="item-idx">{{ item.new_name }}</span>
                <span class="item-orig">← {{ item.original }}</span>
              </div>
              <div class="item-caption">
                <textarea v-model="item.caption" rows="2" @change="editCaption(item)"></textarea>
              </div>
              <div class="item-tags">
                <span v-for="tag in item.tags" :key="tag" class="tag">{{ tag }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
<script>
const DEFAULT_MAP = [
  {zh:'旋盖式',en:'screw cap'},
  {zh:'泵头式',en:'pump dispenser'},
  {zh:'翻盖式',en:'flip cap'},
  {zh:'沐浴露',en:'body wash'},
  {zh:'修长',en:'tall and slender'},
  {zh:'矮胖',en:'short and wide'},
  {zh:'均匀',en:'balanced proportion'},
  {zh:'白色',en:'white'},
  {zh:'黑色',en:'black'},
  {zh:'黄色',en:'yellow'},
  {zh:'红色',en:'red'},
  {zh:'蓝色',en:'blue'},
];
export default {
  name: 'LabelView',
  data() {
    return { folder:'',trigger:'',items:[],scanning:false,running:false,
      resultMsg:'',resultOk:false,mapRows:[],
      browseOpen:false,browseCurrent:'D:\\\\',browseParent:'D:\\\\',browseEntries:[],browseDrives:[] };
  },
  async mounted() {
    await this.loadMapFromServer();
  },
  methods: {
    async openBrowse(){this.browseOpen=true;await this.browseNav(this.folder||'D:\\\\');},
    async browseNav(path){
      try{
        const r=await fetch('/api/browse_dir',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({path})});
        const d=await r.json();
        if(d.success){this.browseCurrent=d.current;this.browseParent=d.parent;this.browseEntries=d.entries;this.browseDrives=d.drives;}
      }catch(e){}
    },
    confirmBrowse(){this.folder=this.browseCurrent;this.browseOpen=false;},
    addMapRow(){this.mapRows.push({zh:'',en:''});},
    delMapRow(idx){this.mapRows.splice(idx,1);},
    async loadMapFromServer(){
      try{
        const r=await fetch('/api/label_map',{method:'GET'});
        const d=await r.json();
        if(d.success){
          this.mapRows=Object.entries(d.map).map(([zh,en])=>({zh,en}));
        }
      }catch(e){console.error('加载映射表失败:',e);}
    },
    async saveMap(){
      const obj={};
      for(const row of this.mapRows){if(row.zh&&row.en)obj[row.zh]=row.en;}
      try{
        const r=await fetch('/api/label_map',{method:'POST',headers:{'Content-Type':'application/json'},
          body:JSON.stringify({map:obj})});
        const d=await r.json();
        if(d.success){
          this.resultMsg='映射表已保存到服务器';
          this.resultOk=true;
        }else{
          this.resultMsg='保存失败: '+(d.error||'未知错误');
          this.resultOk=false;
        }
      }catch(e){
        this.resultMsg='请求失败: '+e;
        this.resultOk=false;
      }
      setTimeout(()=>{this.resultMsg='';},2000);
    },
    async scanFolder(){
      this.scanning=true;this.resultMsg='';
      try{
        const r=await fetch('/api/label/scan',{method:'POST',headers:{'Content-Type':'application/json'},
          body:JSON.stringify({folder:this.folder,trigger:this.trigger})});
        const d=await r.json();
        if(d.success)this.items=d.items;
        else{this.resultMsg=d.error||'扫描失败';this.resultOk=false;}
      }catch(e){this.resultMsg='请求失败: '+e;this.resultOk=false;}
      this.scanning=false;
    },
    async runLabel(){
      if(!confirm('确认执行打标？此操作不可撤销。'))return;
      this.running=true;this.resultMsg='';
      try{
        const r=await fetch('/api/label/run',{method:'POST',headers:{'Content-Type':'application/json'},
          body:JSON.stringify({folder:this.folder,trigger:this.trigger})});
        const d=await r.json();
        if(d.success){
          this.resultMsg='完成！已处理 '+d.count+' 张图片';
          this.resultOk=true;this.items=d.done.map(i=>({...i,tags:[]}));
        }else{this.resultMsg=d.error||'执行失败';this.resultOk=false;}
      }catch(e){this.resultMsg='请求失败: '+e;this.resultOk=false;}
      this.running=false;
    },
    async editCaption(item){
      try{await fetch('/api/label/edit',{method:'POST',headers:{'Content-Type':'application/json'},
        body:JSON.stringify({folder:this.folder,txt_name:item.txt_name,caption:item.caption})});}
      catch(e){}
    },
  },
};
</script>
<style scoped>
.lv{display:flex;flex-direction:column;height:calc(100vh - 56px);background:#0a0a0c;color:#c8c8c8;font-family:'Consolas',monospace;overflow:hidden;}
.lv-top{padding:14px 20px 10px;border-bottom:1px solid #1a1a1e;flex-shrink:0;}
.lv-title{font-size:1rem;font-weight:600;color:#f97316;}
.lv-desc{font-size:.72rem;color:#666;margin-top:3px;}
.lv-body{display:flex;flex:1;overflow:hidden;gap:12px;padding:12px;}
.lv-left{width:300px;flex-shrink:0;display:flex;flex-direction:column;overflow:hidden;gap:10px;}
.lv-right{flex:1;overflow:hidden;display:flex;flex-direction:column;}
.card{background:#0d0d10;border:1px solid #1a1a1e;border-radius:5px;padding:12px;}
.map-card{flex:1;overflow:hidden;display:flex;flex-direction:column;}
.preview-card{height:100%;display:flex;flex-direction:column;}
.card-hdr{font-size:.68rem;color:#666;text-transform:uppercase;letter-spacing:.06em;margin-bottom:10px;display:flex;align-items:center;justify-content:space-between;}
.fgroup{display:flex;flex-direction:column;gap:3px;margin-bottom:8px;}
.fgroup label{font-size:.65rem;color:#555;text-transform:uppercase;letter-spacing:.04em;}
.fgroup input{background:#080809;border:1px solid #222;color:#c8c8c8;padding:5px 8px;border-radius:3px;font-size:.75rem;font-family:inherit;outline:none;}
.fgroup input:focus{border-color:#f97316;}
.input-row{display:flex;gap:4px;}
.input-row input{flex:1;}
.btn-browse{background:#1a1a1e;border:1px solid #333;color:#c8c8c8;padding:4px 8px;border-radius:3px;cursor:pointer;font-size:.85rem;flex-shrink:0;}
.btn-browse:hover{border-color:#f97316;}
.btn-row{display:flex;gap:6px;margin-top:4px;}
.btn-scan{flex:1;background:#1a1a1e;border:1px solid #333;color:#c8c8c8;padding:6px;border-radius:3px;cursor:pointer;font-size:.75rem;font-family:inherit;}
.btn-scan:hover:not(:disabled){border-color:#f97316;color:#f97316;}
.btn-scan:disabled,.btn-run:disabled{opacity:.4;cursor:not-allowed;}
.btn-run{flex:1;background:#f97316;border:none;color:#fff;padding:6px;border-radius:3px;cursor:pointer;font-size:.75rem;font-family:inherit;font-weight:600;}
.btn-run:hover:not(:disabled){background:#ea6c0a;}
.result-msg{margin-top:8px;font-size:.72rem;padding:5px 8px;border-radius:3px;}
.msg-ok{background:#22c55e15;color:#22c55e;border:1px solid #22c55e33;}
.msg-err{background:#ef444415;color:#ef4444;border:1px solid #ef444433;}
.btn-add-map{background:#1a1a1e;border:1px solid #333;color:#f97316;padding:2px 8px;border-radius:3px;cursor:pointer;font-size:.68rem;}
.map-list{flex:1;overflow-y:auto;display:flex;flex-direction:column;gap:3px;margin-bottom:8px;}
.map-row{display:flex;align-items:center;gap:4px;}
.map-input{flex:1;background:#080809;border:1px solid #222;color:#c8c8c8;padding:3px 5px;border-radius:2px;font-size:.7rem;font-family:inherit;outline:none;min-width:0;}
.map-input:focus{border-color:#f97316;}
.map-arrow{color:#444;font-size:.7rem;flex-shrink:0;}
.btn-del-map{background:transparent;border:none;color:#555;cursor:pointer;padding:0 2px;}
.btn-del-map:hover{color:#ef4444;}
.btn-save-map{width:100%;background:#f9731620;border:1px solid #f9731644;color:#f97316;padding:5px;border-radius:3px;cursor:pointer;font-size:.72rem;font-family:inherit;margin-top:4px;}
.count-badge{background:#f9731620;color:#f97316;border:1px solid #f9731644;padding:1px 6px;border-radius:10px;font-size:.65rem;text-transform:none;}
.empty-hint{color:#444;font-size:.75rem;text-align:center;padding:40px 0;font-style:italic;}
.items-list{flex:1;overflow-y:auto;display:flex;flex-direction:column;gap:8px;}
.label-item{background:#080809;border:1px solid #1a1a1e;border-radius:4px;padding:8px;}
.item-head{display:flex;align-items:center;gap:8px;margin-bottom:5px;}
.item-idx{font-size:.72rem;color:#f97316;font-weight:600;flex-shrink:0;}
.item-orig{font-size:.62rem;color:#444;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;flex:1;}
.item-caption textarea{width:100%;background:#0d0d10;border:1px solid #222;color:#c8c8c8;padding:4px 6px;border-radius:3px;font-size:.72rem;font-family:inherit;resize:none;outline:none;box-sizing:border-box;}
.item-caption textarea:focus{border-color:#f97316;}
.item-tags{display:flex;flex-wrap:wrap;gap:3px;margin-top:4px;}
.tag{background:#1a1a1e;border:1px solid #2a2a2e;color:#888;padding:1px 6px;border-radius:2px;font-size:.62rem;}
.modal-overlay{position:fixed;inset:0;background:#00000099;display:flex;align-items:center;justify-content:center;z-index:200;}
.modal{background:#111114;border:1px solid #2a2a2e;border-radius:6px;width:480px;max-height:70vh;display:flex;flex-direction:column;}
.modal-hdr{display:flex;align-items:center;justify-content:space-between;padding:12px 16px;border-bottom:1px solid #1a1a1e;font-size:.8rem;}
.modal-hdr button{background:transparent;border:none;color:#666;cursor:pointer;font-size:1rem;}
.modal-hdr button:hover{color:#c8c8c8;}
.browse-path{padding:8px 16px;font-size:.72rem;color:#f97316;background:#080809;border-bottom:1px solid #1a1a1e;word-break:break-all;}
.browse-drives{display:flex;gap:4px;padding:6px 16px;border-bottom:1px solid #1a1a1e;flex-wrap:wrap;}
.drive-btn{background:#1a1a1e;border:1px solid #333;color:#aaa;padding:2px 8px;border-radius:2px;cursor:pointer;font-size:.7rem;}
.drive-btn:hover{border-color:#f97316;color:#f97316;}
.browse-list{flex:1;overflow-y:auto;padding:4px 8px;}
.browse-item{padding:5px 8px;border-radius:3px;cursor:pointer;font-size:.75rem;display:flex;align-items:center;gap:6px;}
.browse-item:hover{background:#1a1a1e;}
.browse-item.parent{color:#888;font-style:italic;}
.browse-footer{padding:10px 16px;border-top:1px solid #1a1a1e;display:flex;align-items:center;justify-content:space-between;gap:8px;}
.browse-sel{font-size:.68rem;color:#666;flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}
.btn-confirm{background:#f97316;border:none;color:#fff;padding:5px 14px;border-radius:3px;cursor:pointer;font-size:.75rem;font-family:inherit;flex-shrink:0;}
.btn-confirm:hover{background:#ea6c0a;}
</style>
