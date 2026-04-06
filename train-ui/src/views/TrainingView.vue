<template>
  <div class="tv">
    <div class="statusbar">
      <div class="sb-left">
        <span class="badge" :class="kohyaOk ? 'badge-ok' : 'badge-err'">
          <i class="dot"></i>{{ kohyaOk ? 'Kohya_ss 就绪' : 'Kohya_ss 未连接' }}
        </span>
        <span class="sb-info" v-if="isTraining">Epoch {{ st.current_epoch }}/{{ st.total_epochs }} · Loss <b>{{ (st.loss||0).toFixed(4) }}</b> · {{ Math.round(st.progress||0) }}% · {{ fmtElapsed(st.elapsed) }}</span>
        <span class="sb-idle" v-else>待机 · 配置参数后点击开始训练</span>
      </div>
      <div class="sb-right">
        <button class="btn-run" :disabled="isTraining" @click="startTraining"><svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21"/></svg>{{ isTraining ? '训练中...' : '开始训练' }}</button>
        <button class="btn-stop" :disabled="!isTraining" @click="stopTraining"><svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor"><rect x="3" y="3" width="18" height="18" rx="2"/></svg></button>
        <button class="btn-ghost" @click="previewCmd">预览命令</button>
        <button class="btn-ghost" @click="saveConfig">保存</button>
        <button class="btn-ghost" @click="loadConfigFile">载入</button>
      </div>
    </div>
    <div class="workspace">
      <aside class="cpanel">
        <div class="tab-bar">
          <button v-for="t in tabs" :key="t.id" :class="['tab-btn',activeTab===t.id&&'tab-active']" @click="activeTab=t.id">{{ t.label }}</button>
        </div>
        <!-- 路径 -->
        <div v-show="activeTab==='paths'" class="tab-body">
          <div class="fgroup"><label>模型类型</label><div class="seg"><button v-for="m in modelTypes" :key="m.v" :class="{active:cfg.modelType===m.v}" @click="cfg.modelType=m.v">{{ m.l }}</button></div></div>
          <div class="fgroup"><label>底座模型</label><div class="input-row"><input v-model="cfg.baseModel" placeholder="D:\models\base.safetensors" /><button class="btn-scan" @click="scanBaseModels">⟳</button></div><select v-if="baseModels.length" v-model="cfg.baseModel" class="mt-s"><option value="">-- 从扫描结果选择 --</option><option v-for="m in baseModels" :key="m.path" :value="m.path">{{ m.name }} ({{ m.size_mb }}MB)</option></select></div>
          <div class="fgroup"><label>VAE</label><input v-model="cfg.vae" placeholder="留空" /></div>
          <div class="fgroup row2"><div><label>v2</label><div class="tog-row" @click="cfg.v2=!cfg.v2"><div class="tog" :class="{on:cfg.v2}"><div class="th"></div></div><span>{{ cfg.v2?'启用':'关闭' }}</span></div></div><div><label>v-param</label><div class="tog-row" @click="cfg.vParameterization=!cfg.vParameterization"><div class="tog" :class="{on:cfg.vParameterization}"><div class="th"></div></div><span>{{ cfg.vParameterization?'启用':'关闭' }}</span></div></div></div>
          <div class="fgroup"><label>数据集路径</label><div class="input-row"><input v-model="cfg.datasetPath" placeholder="D:\dataset\subject" /><button class="btn-scan" @click="scanDatasets">⟳</button></div><select v-if="datasetDirs.length" v-model="cfg.datasetPath" class="mt-s"><option value="">-- 从扫描结果选择 --</option><option v-for="d in datasetDirs" :key="d" :value="d">{{ d }}</option></select></div>
          <div class="fgroup"><label>正则化数据集</label><input v-model="cfg.regDataDir" placeholder="留空" /></div>
          <div class="fgroup"><label>输出路径</label><div class="input-row"><input v-model="cfg.outputPath" placeholder="D:\output\lora" /><button class="btn-scan" @click="fillOutputFromSettings">↙</button></div></div>
          <div class="fgroup"><label>模型名称</label><input v-model="cfg.modelName" placeholder="my_lora" /></div>
          <div class="fgroup"><label>日志目录</label><input v-model="cfg.loggingDir" placeholder="留空" /></div>
          <div class="fgroup"><label>数据集配置文件</label><input v-model="cfg.datasetConfig" placeholder="D:\dataset\config.toml" /></div>
          <div class="fgroup"><label>训练备注</label><input v-model="cfg.trainingComment" /></div>
          <div class="fgroup"><label>继续训练</label><input v-model="cfg.resumeFrom" placeholder="留空" /></div>
        </div>
        <!-- 基础 -->
        <div v-show="activeTab==='basic'" class="tab-body">
          <div class="fgroup"><label>分辨率</label><div class="seg"><button v-for="r in resOptions" :key="r.v" :class="{active:cfg.resolution===r.v}" @click="cfg.resolution=r.v">{{ r.l }}</button></div><input v-model="cfg.resolution" style="margin-top:.3rem" /></div>
          <div class="fgroup row2"><div><label>批次大小</label><div class="num-ctrl"><button @click="cfg.batchSize=Math.max(1,cfg.batchSize-1)">−</button><input type="number" v-model.number="cfg.batchSize" min="1" /><button @click="cfg.batchSize++">+</button></div></div><div><label>训练轮数</label><div class="num-ctrl"><button @click="cfg.epochs=Math.max(1,cfg.epochs-1)">−</button><input type="number" v-model.number="cfg.epochs" min="1" /><button @click="cfg.epochs++">+</button></div></div></div>
          <div class="fgroup row2"><div><label>最大步数</label><input type="number" v-model.number="cfg.maxTrainSteps" min="0" /></div><div><label>每N轮保存</label><div class="num-ctrl"><button @click="cfg.saveEveryNEpochs=Math.max(1,cfg.saveEveryNEpochs-1)">−</button><input type="number" v-model.number="cfg.saveEveryNEpochs" /><button @click="cfg.saveEveryNEpochs++">+</button></div></div></div>
          <div class="fgroup row2"><div><label>每N步保存</label><input type="number" v-model.number="cfg.saveEveryNSteps" min="0" /></div><div><label>保留最后N轮</label><input type="number" v-model.number="cfg.saveLastNEpochs" min="0" /></div></div>
          <div class="fgroup row2"><div><label>保存精度</label><div class="seg"><button v-for="p in ['fp16','bf16','fp32']" :key="p" :class="{active:cfg.savePrecision===p}" @click="cfg.savePrecision=p">{{ p }}</button></div></div><div><label>保存格式</label><div class="seg"><button v-for="f in ['safetensors','ckpt']" :key="f" :class="{active:cfg.saveModelAs===f}" @click="cfg.saveModelAs=f">{{ f }}</button></div></div></div>
          <div class="fgroup"><label>Caption扩展名</label><div class="seg"><button v-for="e in ['.txt','.caption','.cap']" :key="e" :class="{active:cfg.captionExtension===e}" @click="cfg.captionExtension=e">{{ e }}</button></div></div>
          <div class="fgroup row2"><div><label>随机种子</label><input type="number" v-model.number="cfg.seed" min="0" /></div><div><label>最大Token长度</label><div class="seg"><button v-for="t in [75,150,225]" :key="t" :class="{active:cfg.maxTokenLength===t}" @click="cfg.maxTokenLength=t">{{ t }}</button></div></div></div>
          <div class="fgroup"><label>Caption选项</label><div class="checks"><label><input type="checkbox" v-model="cfg.shuffleCaption" /> Shuffle</label><label><input type="checkbox" v-model="cfg.weightedCaptions" /> Weighted</label></div></div>
          <div class="fgroup row2"><div><label>保留前N Token</label><input type="number" v-model.number="cfg.keepNTokens" min="0" /></div><div><label>Token分隔符</label><input v-model="cfg.keepTokenSeparator" placeholder="|" /></div></div>
          <div class="fgroup"><label>潜变量缓存</label><div class="checks"><label><input type="checkbox" v-model="cfg.cacheLatents" /> 缓存潜变量</label><label><input type="checkbox" v-model="cfg.cacheLatentsToDisk" /> 缓存到磁盘</label></div></div>
        </div>
        <!-- 优化器 -->
        <div v-show="activeTab==='optim'" class="tab-body">
          <div class="fgroup"><label>优化器</label><div class="seg wrap"><button v-for="o in optimizers" :key="o" :class="{active:cfg.optimizer===o}" @click="cfg.optimizer=o">{{ o }}</button></div></div>
          <div class="fgroup"><label>优化器参数</label><input v-model="cfg.optimizerArgs" placeholder="可选" /></div>
          <div class="fgroup"><label>学习率</label><div class="seg"><button v-for="lr in lrPresets" :key="lr" :class="{active:cfg.learningRate===lr}" @click="cfg.learningRate=lr">{{ lr }}</button></div><input v-model="cfg.learningRate" style="margin-top:.3rem" /></div>
          <div class="fgroup row2"><div><label>文本编码器LR</label><input v-model="cfg.textEncoderLr" placeholder="留空" /></div><div><label>UNet LR</label><input v-model="cfg.unetLr" placeholder="留空" /></div></div>
          <div class="fgroup"><label>LR调度器</label><div class="seg wrap"><button v-for="s in lrSchedulers" :key="s" :class="{active:cfg.lrScheduler===s}" @click="cfg.lrScheduler=s">{{ s }}</button></div></div>
          <div class="fgroup row2"><div><label>预热比例</label><input type="number" v-model.number="cfg.lrWarmupRatio" min="0" max="1" step="0.01" /></div><div><label>预热步数</label><input type="number" v-model.number="cfg.lrWarmupSteps" min="0" /></div></div>
          <div class="fgroup row2"><div><label>Cosine 循环数</label><input type="number" v-model.number="cfg.lrSchedulerNumCycles" min="1" /></div><div><label>调度器幂次</label><input type="number" v-model.number="cfg.lrSchedulerPower" step="0.1" /></div></div>
          <div class="fgroup"><label>混合精度</label><div class="seg"><button v-for="p in ['fp16','bf16','fp32','no']" :key="p" :class="{active:cfg.mixedPrecision===p}" @click="cfg.mixedPrecision=p">{{ p }}</button></div></div>
        </div>
        <!-- LoRA -->
        <div v-show="activeTab==='network'" class="tab-body">
          <div class="fgroup"><label>网络模块</label><div class="seg wrap"><button v-for="nm in networkModules" :key="nm.v" :class="{active:cfg.networkModule===nm.v}" @click="cfg.networkModule=nm.v">{{ nm.l }}</button></div></div>
          <div class="fgroup row2"><div><label>网络维度</label><div class="num-ctrl"><button @click="cfg.networkDim=Math.max(1,cfg.networkDim-8)">−</button><input type="number" v-model.number="cfg.networkDim" min="1" /><button @click="cfg.networkDim+=8">+</button></div></div><div><label>网络Alpha</label><div class="num-ctrl"><button @click="cfg.networkAlpha=Math.max(1,cfg.networkAlpha-1)">−</button><input type="number" v-model.number="cfg.networkAlpha" min="1" /><button @click="cfg.networkAlpha++">+</button></div></div></div>
          <div class="fgroup row2"><div><label>卷积维度</label><input type="number" v-model.number="cfg.convDim" min="0" /></div><div><label>卷积Alpha</label><input type="number" v-model.number="cfg.convAlpha" min="0" /></div></div>
          <div class="fgroup"><label>网络Dropout</label><input type="number" v-model.number="cfg.networkDropout" min="0" max="1" step="0.05" placeholder="0" /></div>
          <div class="fgroup"><label>LyCORIS 算法</label><div class="seg wrap"><button v-for="a in lycAlgos" :key="a" :class="{active:cfg.algo===a}" @click="cfg.algo=a">{{ a||'默认' }}</button></div></div>
          <div class="fgroup"><label>预载网络权重</label><input v-model="cfg.networkWeights" placeholder="留空" /></div>
          <div class="fgroup"><label>训练目标</label><div class="checks"><label><input type="checkbox" v-model="cfg.networkTrainUnetOnly" /> 仅训练 UNet</label><label><input type="checkbox" v-model="cfg.networkTrainTextEncoderOnly" /> 仅训练文本编码器</label></div></div>
        </div>
        <!-- Bucket -->
        <div v-show="activeTab==='bucket'" class="tab-body">
          <div class="fgroup"><label>启用分辨率分桶</label><div class="tog-row" @click="cfg.enableBucket=!cfg.enableBucket"><div class="tog" :class="{on:cfg.enableBucket}"><div class="th"></div></div><span>{{ cfg.enableBucket?'已启用':'已关闭' }}</span></div></div>
          <div v-if="cfg.enableBucket">
            <div class="fgroup row2"><div><label>最小分辨率</label><input type="number" v-model.number="cfg.minBucketReso" step="64" /></div><div><label>最大分辨率</label><input type="number" v-model.number="cfg.maxBucketReso" step="64" /></div></div>
            <div class="fgroup row2"><div><label>步进分辨率</label><input type="number" v-model.number="cfg.bucketResoSteps" step="8" /></div><div><label>禁止放大</label><div class="tog-row" @click="cfg.bucketNoUpscale=!cfg.bucketNoUpscale"><div class="tog" :class="{on:cfg.bucketNoUpscale}"><div class="th"></div></div><span>{{ cfg.bucketNoUpscale?'启用':'关闭' }}</span></div></div></div>
          </div>
        </div>
        <!-- 高级 -->
        <div v-show="activeTab==='advanced'" class="tab-body">
          <div class="fgroup"><label>显存优化</label><div class="checks">
            <label><input type="checkbox" v-model="cfg.gradientCheckpointing" /> 梯度检查点</label>
            <label><input type="checkbox" v-model="cfg.xformers" /> xFormers</label>
            <label><input type="checkbox" v-model="cfg.sdpaAttn" :disabled="cfg.xformers" /> SDPA</label>
            <label><input type="checkbox" v-model="cfg.fp8BaseUnet" /> FP8 UNet</label>
            <label><input type="checkbox" v-model="cfg.fullFp16" /> Full FP16</label>
            <label><input type="checkbox" v-model="cfg.noHalfVae" /> VAE Full Precision</label>
            <label><input type="checkbox" v-model="cfg.highvram" :disabled="cfg.lowvram" /> 高显存模式</label>
            <label><input type="checkbox" v-model="cfg.lowvram" :disabled="cfg.highvram" /> 低显存模式</label>
          </div></div>
          <div class="fgroup row2"><div><label>梯度累积步数</label><input type="number" v-model.number="cfg.gradientAccumulationSteps" min="1" /></div><div><label>最大梯度范数</label><input type="number" v-model.number="cfg.maxGradNorm" step="0.1" /></div></div>
          <div class="fgroup row2"><div><label>CPU线程数</label><input type="number" v-model.number="cfg.numCpuThreads" min="1" /></div><div><label>Dynamo后端</label><div class="seg"><button v-for="d in ['no','inductor','eager']" :key="d" :class="{active:cfg.dynamoBackend===d}" @click="cfg.dynamoBackend=d">{{ d }}</button></div></div></div>
          <div class="fgroup row2"><div><label>噪声偏移</label><input type="number" v-model.number="cfg.noiseOffset" step="0.01" min="0" /></div><div><label>自适应噪声缩放</label><input type="number" v-model.number="cfg.adaptiveNoiseScale" step="0.01" /></div></div>
          <div class="fgroup row2"><div><label>最小SNR Gamma</label><input type="number" v-model.number="cfg.minSnrGamma" step="0.5" min="0" /></div><div><label>权重范数缩放</label><input type="number" v-model.number="cfg.scaleWeightNorm" step="0.1" min="0" /></div></div>
          <div class="fgroup row2"><div><label>多分辨率噪声折扣</label><input type="number" v-model.number="cfg.multiresNoiseDiscount" step="0.05" /></div><div><label>多分辨率噪声迭代</label><input type="number" v-model.number="cfg.multiresNoiseIterations" min="0" /></div></div>
          <div class="fgroup row2"><div><label>IP噪声Gamma</label><input type="number" v-model.number="cfg.ipNoiseGamma" step="0.01" /></div><div><label>去偏估计</label><div class="tog-row" @click="cfg.debiasedEstimationLoss=!cfg.debiasedEstimationLoss"><div class="tog" :class="{on:cfg.debiasedEstimationLoss}"><div class="th"></div></div><span>{{ cfg.debiasedEstimationLoss?'启用':'关闭' }}</span></div></div></div>
          <div class="fgroup"><label>额外参数</label><textarea v-model="cfg.additionalArgs" rows="3" placeholder="--some_arg value"></textarea></div>
        </div>
      </aside>
      <!-- 右侧监控 -->
      <main class="monitor">
        <div class="mon-top">
          <div class="mcard prog-card">
            <div class="card-lbl">训练进度</div>
            <div class="ring-wrap">
              <svg class="ring-svg" viewBox="0 0 120 120">
                <circle class="ring-bg" cx="60" cy="60" r="52"/>
                <circle class="ring-fg" cx="60" cy="60" r="52" :stroke-dasharray="326.7" :stroke-dashoffset="326.7*(1-(st.progress||0)/100)"/>
              </svg>
              <div class="ring-ctr">
                <span class="ring-pct">{{ Math.round(st.progress||0) }}<em>%</em></span>
                <span class="ring-sub" v-if="isTraining">运行中</span>
                <span class="ring-sub done" v-else-if="(st.progress||0)===100">完成</span>
                <span class="ring-sub" v-else>待机</span>
              </div>
            </div>
            <div class="prog-meta">
              <div class="pm-item"><span>轮次</span><b>{{ st.current_epoch }}/{{ st.total_epochs }}</b></div>
              <div class="pm-item"><span>Loss</span><b class="loss-val">{{ (st.loss||0).toFixed(4) }}</b></div>
              <div class="pm-item"><span>LR</span><b>{{ (st.lr||0).toExponential(2) }}</b></div>
              <div class="pm-item"><span>耗时</span><b>{{ fmtElapsed(st.elapsed) }}</b></div>
            </div>
          </div>
          <div class="mcard chart-card">
            <div class="card-lbl">Loss 曲线</div>
            <div class="chart-inner">
              <svg class="chart-svg" viewBox="0 0 400 100" preserveAspectRatio="xMidYMid meet">
                <defs><linearGradient id="lg" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#f97316" stop-opacity="0.35"/><stop offset="100%" stop-color="#f97316" stop-opacity="0"/></linearGradient></defs>
                <path v-if="lossHistory.length>1" :d="lossArea" fill="url(#lg)"/>
                <path v-if="lossHistory.length>1" :d="lossLine" fill="none" stroke="#f97316" stroke-width="2"/>
              </svg>
              <div v-if="!lossHistory.length" class="chart-empty">训练开始后显示曲线</div>
            </div>
          </div>
        </div>
        <div class="mcard log-card">
          <div class="card-lbl">实时日志
            <div class="lbl-actions">
              <button class="ghost-xs" @click="logs=[];logOffset=0">清空</button>
              <button class="ghost-xs" @click="autoScroll=!autoScroll">{{ autoScroll?'自动滚动 ON':'自动滚动 OFF' }}</button>
            </div>
          </div>
          <div class="log-box" ref="logBox">
            <div v-if="!logs.length" class="log-empty">等待训练启动...</div>
            <div v-for="(line,i) in logs" :key="i" class="log-line" :class="logCls(line.msg)">
              <span class="log-ts">{{ fmtTs(line.t) }}</span>
              <span>{{ line.msg }}</span>
            </div>
          </div>
        </div>
      </main>
    </div>
    <div class="modal-overlay" v-if="cmdModal" @click.self="cmdModal=false">
      <div class="modal">
        <div class="modal-hdr">生成命令预览 <button @click="cmdModal=false">✕</button></div>
        <pre class="cmd-pre">{{ cmdPreview }}</pre>
        <button class="btn-ghost" @click="copyCmd">复制</button>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'TrainingView',
  data() {
    return {
      activeTab: 'paths',
      tabs: [
        {id:'paths',   label:'路径'},
        {id:'basic',   label:'基础'},
        {id:'optim',   label:'优化'},
        {id:'network', label:'LoRA'},
        {id:'bucket',  label:'Bucket'},
        {id:'advanced',label:'高级'},
      ],
      modelTypes: [{v:'sdxl',l:'SDXL'},{v:'sd15',l:'SD1.5'},{v:'flux',l:'Flux'},{v:'sd3',l:'SD3'}],
      resOptions: [{v:'512,512',l:'512'},{v:'768,768',l:'768'},{v:'1024,1024',l:'1024'},{v:'1280,1280',l:'1280'}],
      lrPresets: ['1e-5','5e-5','1e-4','2e-4','5e-4'],
      lrSchedulers: ['constant','cosine','cosine_with_restarts','linear','polynomial','constant_with_warmup','adafactor'],
      optimizers: ['AdamW','AdamW8bit','AdamWScheduleFree','Adafactor','Lion','Prodigy','DAdaptAdam','SGDNesterov'],
      networkModules: [
        {v:'networks.lora',l:'LoRA'},{v:'networks.loha',l:'LoHa'},
        {v:'networks.lokr',l:'LoKr'},{v:'lycoris.kohya',l:'LyCORIS'},
      ],
      lycAlgos: ['','lora','loha','lokr','ia3','dylora','full','diag-oft'],
      baseModels: [], datasetDirs: [],
      isTraining: false, kohyaOk: false,
      cmdModal: false, cmdPreview: '',
      logs: [], logOffset: 0, autoScroll: true,
      lossHistory: [],
      st: {current_epoch:0,total_epochs:0,loss:0,lr:0,progress:0,elapsed:0},
      pollTimer: null, logTimer: null,
      cfg: {
        modelType:'sdxl', baseModel:'', vae:'', v2:false, vParameterization:false,
        datasetPath:'', regDataDir:'', outputPath:'', modelName:'lora_output',
        loggingDir:'', datasetConfig:'', trainingComment:'', resumeFrom:'',
        resolution:'1024,1024', batchSize:1, epochs:10, maxTrainSteps:0,
        saveEveryNEpochs:1, saveEveryNSteps:0, saveLastNEpochs:0,
        savePrecision:'fp16', saveModelAs:'safetensors',
        captionExtension:'.txt', seed:42, maxTokenLength:75,
        shuffleCaption:false, weightedCaptions:false,
        keepNTokens:0, keepTokenSeparator:'',
        cacheLatents:true, cacheLatentsToDisk:false,
        optimizer:'AdamW8bit', optimizerArgs:'', learningRate:'1e-4',
        textEncoderLr:'', unetLr:'', lrScheduler:'cosine',
        lrWarmupRatio:0.05, lrWarmupSteps:0,
        lrSchedulerNumCycles:1, lrSchedulerPower:1.0,
        mixedPrecision:'fp16',
        networkModule:'networks.lora', networkDim:32, networkAlpha:16,
        convDim:0, convAlpha:0, networkDropout:0, algo:'',
        networkWeights:'', networkTrainUnetOnly:false, networkTrainTextEncoderOnly:false,
        enableBucket:true, minBucketReso:256, maxBucketReso:2048,
        bucketResoSteps:64, bucketNoUpscale:false,
        gradientCheckpointing:true, xformers:true, sdpaAttn:false,
        fp8BaseUnet:false, fullFp16:false, noHalfVae:false,
        highvram:false, lowvram:false,
        gradientAccumulationSteps:1, maxGradNorm:1.0,
        numCpuThreads:8, dynamoBackend:'no',
        noiseOffset:0, adaptiveNoiseScale:0,
        minSnrGamma:0, scaleWeightNorm:0,
        multiresNoiseDiscount:0, multiresNoiseIterations:0,
        ipNoiseGamma:0, debiasedEstimationLoss:false,
        zeroTerminalSnr:false, scaleVPredLoss:false,
        flipAug:false, colorAug:false, randomCrop:false, saveState:false,
        lossType:'l2', additionalArgs:'',
      },
    };
  },

  computed: {
    lossLine() {
      if (this.lossHistory.length < 2) return '';
      const h = this.lossHistory, W = 400, H = 100;
      const mn = Math.min(...h), mx = Math.max(...h);
      const rng = mx - mn || 1;
      return h.map((v,i) => {
        const x = i/(h.length-1)*W;
        const y = H - (v-mn)/rng*H*0.9 - H*0.05;
        return (i===0?'M':'L') + x.toFixed(1) + ' ' + y.toFixed(1);
      }).join(' ');
    },
    lossArea() {
      if (this.lossHistory.length < 2) return '';
      const h = this.lossHistory, W = 400, H = 100;
      const mn = Math.min(...h), mx = Math.max(...h);
      const rng = mx - mn || 1;
      const pts = h.map((v,i) => {
        const x = i/(h.length-1)*W;
        const y = H - (v-mn)/rng*H*0.9 - H*0.05;
        return x.toFixed(1) + ' ' + y.toFixed(1);
      });
      return 'M' + pts[0] + ' L' + pts.slice(1).join(' L') +
        ' L' + (400).toFixed(1) + ' ' + H + ' L0 ' + H + ' Z';
    },
  },
  methods: {
    async checkKohya() {
      try {
        console.log('开始检查Kohya状态...');
        const r = await fetch('/api/kohya_status');
        console.log('响应状态:', r.status);
        const d = await r.json();
        console.log('响应数据:', d);
        console.log('installed值:', d.installed);
        console.log('installed类型:', typeof d.installed);
        this.kohyaOk = Boolean(d.installed);
        console.log('kohyaOk更新后的值:', this.kohyaOk);
      } catch (e) {
        console.error('检查Kohya状态时出错:', e);
        this.kohyaOk = false;
      }
    },
    async startTraining() {
      if (!this.kohyaOk) { alert('Kohya_ss 未就绪，请检查路径'); return; }
      if (!this.cfg.baseModel) { alert('请填写底座模型路径'); return; }
      if (!this.cfg.datasetPath) { alert('请填写数据集路径'); return; }
      if (!this.cfg.outputPath) { alert('请填写输出路径'); return; }
      try {
        const r = await fetch('/api/start_training', {
          method: 'POST',
          headers: {'Content-Type':'application/json'},
          body: JSON.stringify(this.cfg),
        });
        const d = await r.json();
        if (d.success) { this.isTraining=true; this.logs=[]; this.lossHistory=[]; this.logOffset=0; this.startPolling(); }
        else { alert('启动失败: ' + (d.error||'')); }
      } catch(e) { alert('请求失败: '+e); }
    },
    async stopTraining() {
      try {
        const r = await fetch('/api/stop_training', {method:'POST'});
        const d = await r.json();
        if (d.success) { this.isTraining=false; this.stopPolling(); }
      } catch(e) {}
    },
    startPolling() {
      this.pollTimer = setInterval(async () => {
        try {
          const r = await fetch('/api/training_status');
          const d = await r.json();
          this.st = d;
          if (d.loss && d.loss > 0) this.lossHistory.push(d.loss);
          if (!d.is_training) { this.isTraining=false; this.stopPolling(); }
        } catch(e) {}
      }, 1000);
      this.logTimer = setInterval(async () => {
        try {
          const r = await fetch('/api/training_logs?offset='+this.logOffset);
          const d = await r.json();
          if (d.logs && d.logs.length) {
            this.logs.push(...d.logs);
            this.logOffset += d.logs.length;
            if (this.autoScroll && this.$refs.logBox) {
              this.$nextTick(() => { this.$refs.logBox.scrollTop = this.$refs.logBox.scrollHeight; });
            }
          }
        } catch(e) {}
      }, 1000);
    },
    stopPolling() {
      if (this.pollTimer) { clearInterval(this.pollTimer); this.pollTimer=null; }
      if (this.logTimer) { clearInterval(this.logTimer); this.logTimer=null; }
    },
    async previewCmd() {
      try {
        const r = await fetch('/api/preview_command', {
          method:'POST', headers:{'Content-Type':'application/json'},
          body: JSON.stringify(this.cfg),
        });
        const d = await r.json();
        this.cmdPreview = d.command || d.error || '';
        this.cmdModal = true;
      } catch(e) { this.cmdPreview = e.toString(); this.cmdModal=true; }
    },
    copyCmd() { navigator.clipboard.writeText(this.cmdPreview).catch(()=>{}); },
    async saveConfig() {
      const json = JSON.stringify(this.cfg, null, 2);
      const blob = new Blob([json], {type:'application/json'});
      const a = document.createElement('a'); a.href=URL.createObjectURL(blob);
      a.download = (this.cfg.modelName||'config') + '.json'; a.click();
    },
    loadConfigFile() {
      const inp = document.createElement('input'); inp.type='file'; inp.accept='.json';
      inp.onchange = e => {
        const file = e.target.files[0]; if (!file) return;
        const reader = new FileReader();
        reader.onload = ev => {
          try { const c = JSON.parse(ev.target.result); Object.assign(this.cfg, c); }
          catch(err) { alert('JSON 格式错误'); }
        };
        reader.readAsText(file);
      };
      inp.click();
    },
    async scanBaseModels() {
      try {
        const r = await fetch('/api/scan_models');
        const d = await r.json();
        this.baseModels = d.models || [];
      } catch(e) {}
    },
    async scanDatasets() {
      try {
        const r = await fetch('/api/scan_datasets');
        const d = await r.json();
        this.datasetDirs = d.dirs || [];
      } catch(e) {}
    },
    fillOutputFromSettings() {
      fetch('/api/settings').then(r=>r.json()).then(d=>{ if(d.output_dir) this.cfg.outputPath=d.output_dir; }).catch(()=>{});
    },
    fmtElapsed(s) {
      if (!s) return '--';
      const h=Math.floor(s/3600), m=Math.floor((s%3600)/60), sec=Math.floor(s%60);
      if (h>0) return h+'h '+m+'m';
      if (m>0) return m+'m '+sec+'s';
      return sec+'s';
    },
    fmtTs(t) {
      if (!t) return '';
      const d=new Date(t*1000);
      return d.toTimeString().slice(0,8);
    },
    logCls(msg) {
      if (!msg) return '';
      const m=msg.toLowerCase();
      if (m.includes('error')||m.includes('错误')) return 'log-err';
      if (m.includes('warn')||m.includes('警告')) return 'log-warn';
      if (m.includes('epoch')||m.includes('step')||m.includes('loss')) return 'log-info';
      return '';
    },
  },
  mounted() {
    this.checkKohya();
    setInterval(this.checkKohya, 10000);
    fetch('/api/training_status').then(r=>r.json()).then(d=>{
      if (d.is_training) { this.isTraining=true; this.st=d; this.startPolling(); }
    }).catch(()=>{});
  },
  beforeUnmount() { this.stopPolling(); },
};
</script>
<style scoped>
.tv{display:flex;flex-direction:column;height:calc(100vh - 56px);background:#0a0a0c;font-family:'Consolas','SF Mono','Fira Code',monospace;color:#c8c8c8;}
.statusbar{display:flex;align-items:center;justify-content:space-between;padding:0 16px;height:36px;background:#111114;border-bottom:1px solid #222;flex-shrink:0;}
.sb-left{display:flex;align-items:center;gap:10px;font-size:.75rem;}
.sb-right{display:flex;align-items:center;gap:6px;}
.badge{display:flex;align-items:center;gap:5px;padding:2px 8px;border-radius:3px;font-size:.7rem;border:1px solid #333;}
.badge-ok{border-color:#22c55e44;color:#22c55e;}
.badge-err{border-color:#ef444444;color:#ef4444;}
.dot{width:6px;height:6px;border-radius:50%;background:currentColor;}
.sb-info{color:#888;font-size:.72rem;}
.sb-idle{color:#555;font-size:.72rem;font-style:italic;}
.btn-run{display:flex;align-items:center;gap:5px;background:#f97316;color:#fff;border:none;padding:4px 12px;border-radius:3px;cursor:pointer;font-size:.75rem;font-family:inherit;}
.btn-run:hover{background:#ea6c0a;}
.btn-run:disabled{opacity:.4;cursor:not-allowed;}
.btn-stop{background:#333;color:#c8c8c8;border:none;padding:4px 8px;border-radius:3px;cursor:pointer;display:flex;align-items:center;}
.btn-stop:hover{background:#444;}
.btn-stop:disabled{opacity:.4;cursor:not-allowed;}
.btn-ghost{background:transparent;color:#888;border:1px solid #333;padding:3px 8px;border-radius:3px;cursor:pointer;font-size:.72rem;font-family:inherit;}
.btn-ghost:hover{border-color:#555;color:#c8c8c8;}
.workspace{display:flex;flex:1;overflow:hidden;}
.cpanel{width:340px;min-width:280px;border-right:1px solid #1a1a1e;display:flex;flex-direction:column;overflow:hidden;flex-shrink:0;}
.tab-bar{display:flex;border-bottom:1px solid #1a1a1e;background:#0d0d10;flex-shrink:0;}
.tab-btn{flex:1;padding:6px 2px;background:transparent;border:none;color:#666;cursor:pointer;font-size:.7rem;font-family:inherit;border-bottom:2px solid transparent;}
.tab-btn:hover{color:#aaa;}
.tab-active{color:#f97316!important;border-bottom-color:#f97316!important;}
.tab-body{flex:1;overflow-y:auto;padding:10px;display:flex;flex-direction:column;gap:6px;}
.fgroup{display:flex;flex-direction:column;gap:3px;}
.fgroup label{font-size:.68rem;color:#666;text-transform:uppercase;letter-spacing:.04em;}
.fgroup input,.fgroup select,.fgroup textarea{background:#0d0d10;border:1px solid #222;color:#c8c8c8;padding:4px 7px;border-radius:3px;font-size:.75rem;font-family:inherit;outline:none;}
.fgroup input:focus,.fgroup select:focus,.fgroup textarea:focus{border-color:#f97316;}
.row2{display:grid;grid-template-columns:1fr 1fr;gap:6px;}
.fgroup.row2{display:grid;grid-template-columns:1fr 1fr;gap:6px;}
.seg{display:flex;flex-wrap:wrap;gap:3px;}
.seg.wrap{flex-wrap:wrap;}
.seg button{background:#111;border:1px solid #222;color:#888;padding:2px 7px;border-radius:2px;cursor:pointer;font-size:.7rem;font-family:inherit;}
.seg button:hover{border-color:#444;color:#aaa;}
.seg button.active{background:#f9731620;border-color:#f97316;color:#f97316;}
.num-ctrl{display:flex;align-items:center;gap:2px;}
.num-ctrl button{background:#111;border:1px solid #222;color:#888;width:22px;height:24px;border-radius:2px;cursor:pointer;font-size:.85rem;}
.num-ctrl input{flex:1;text-align:center;}
.checks{display:flex;flex-direction:column;gap:3px;}
.checks label{display:flex;align-items:center;gap:5px;font-size:.73rem;color:#999;cursor:pointer;text-transform:none;letter-spacing:0;}
.tog-row{display:flex;align-items:center;gap:6px;cursor:pointer;margin-top:2px;}
.tog{width:30px;height:16px;background:#222;border-radius:8px;position:relative;transition:background .2s;}
.tog.on{background:#f97316;}
.th{width:12px;height:12px;background:#fff;border-radius:50%;position:absolute;top:2px;left:2px;transition:left .2s;}
.tog.on .th{left:16px;}
.input-row{display:flex;gap:4px;}
.input-row input{flex:1;}
.btn-scan{background:#111;border:1px solid #222;color:#888;padding:4px 8px;border-radius:3px;cursor:pointer;font-size:.85rem;flex-shrink:0;}
.btn-scan:hover{border-color:#444;color:#aaa;}
.mt-s{margin-top:4px;}
.monitor{flex:1;display:flex;flex-direction:column;padding:12px;gap:10px;overflow:hidden;}
.mon-top{display:flex;gap:10px;flex-shrink:0;}
.mcard{background:#0d0d10;border:1px solid #1a1a1e;border-radius:5px;padding:10px;}
.card-lbl{font-size:.68rem;color:#666;text-transform:uppercase;letter-spacing:.06em;margin-bottom:8px;display:flex;align-items:center;justify-content:space-between;}
.prog-card{width:200px;flex-shrink:0;}
.ring-wrap{display:flex;justify-content:center;margin-bottom:8px;}
.ring-svg{width:90px;height:90px;transform:rotate(-90deg);}
.ring-bg{fill:none;stroke:#1a1a1e;stroke-width:8;}
.ring-fg{fill:none;stroke:#f97316;stroke-width:8;stroke-linecap:round;transition:stroke-dashoffset .5s;}
.ring-ctr{position:absolute;display:flex;flex-direction:column;align-items:center;justify-content:center;width:90px;height:90px;pointer-events:none;}
.ring-wrap{position:relative;}
.ring-pct{font-size:1.2rem;font-weight:bold;color:#c8c8c8;}
.ring-pct em{font-size:.65rem;font-style:normal;color:#666;}
.ring-sub{font-size:.65rem;color:#666;}
.ring-sub.done{color:#22c55e;}
.prog-meta{display:grid;grid-template-columns:1fr 1fr;gap:4px;}
.pm-item{display:flex;flex-direction:column;gap:1px;}
.pm-item span{font-size:.62rem;color:#555;}
.pm-item b{font-size:.75rem;color:#c8c8c8;}
.loss-val{color:#f97316;}
.chart-card{flex:1;display:flex;flex-direction:column;}
.chart-inner{flex:1;position:relative;min-height:80px;}
.chart-svg{width:100%;height:100%;}
.chart-empty{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;color:#444;font-size:.72rem;}
.log-card{flex:1;display:flex;flex-direction:column;overflow:hidden;}
.lbl-actions{display:flex;gap:4px;}
.ghost-xs{background:transparent;border:1px solid #222;color:#666;padding:1px 6px;border-radius:2px;cursor:pointer;font-size:.65rem;font-family:inherit;}
.ghost-xs:hover{border-color:#444;color:#999;}
.log-box{flex:1;overflow-y:auto;font-size:.72rem;line-height:1.5;background:#080809;border-radius:3px;padding:6px;}
.log-empty{color:#444;font-style:italic;}
.log-line{display:flex;gap:8px;}
.log-ts{color:#444;flex-shrink:0;}
.log-err .log-ts,.log-err span{color:#ef4444;}
.log-warn .log-ts,.log-warn span{color:#f59e0b;}
.log-info span{color:#60a5fa;}
.modal-overlay{position:fixed;inset:0;background:#00000088;display:flex;align-items:center;justify-content:center;z-index:100;}
.modal{background:#111114;border:1px solid #2a2a2e;border-radius:6px;padding:16px;width:700px;max-width:95vw;max-height:80vh;display:flex;flex-direction:column;gap:10px;}
.modal-hdr{display:flex;align-items:center;justify-content:space-between;font-size:.8rem;}
.modal-hdr button{background:transparent;border:none;color:#666;cursor:pointer;font-size:1rem;}
.cmd-pre{background:#080809;border:1px solid #1a1a1e;border-radius:3px;padding:10px;font-size:.72rem;overflow:auto;flex:1;white-space:pre-wrap;word-break:break-all;color:#a8d8a8;}
</style>
