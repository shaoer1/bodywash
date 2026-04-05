import os
os.environ.setdefault('HF_ENDPOINT', 'https://hf-mirror.com')
os.environ.setdefault('HUGGINGFACE_HUB_OFFLINE', '0')
import json
import logging
import subprocess
import threading
import time
import re
import sys
import shlex
from datetime import datetime
from flask import Flask, request, jsonify, Response, stream_with_context
from flask_cors import CORS

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

app = Flask(__name__)
CORS(app)

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
SETTINGS_FILE = os.path.join(BASE_DIR, 'settings.json')

DEFAULT_SETTINGS = {
    'kohya_path': r'D:\kohya_ss',
    'models_dir': r'D:\models',
    'output_dir': r'D:\文生图\output',
    'dataset_base': r'D:\文生图\dataset',
    'inference_model': '',
}

# 映射表文件路径
LABEL_MAP_FILE = os.path.join(BASE_DIR, 'label_map.json')

# 默认映射表
DEFAULT_LABEL_MAP = {
    # 产品类型
    '旋盖式': 'screw cap',
    '泵头式': 'pump dispenser',
    '翻盖式': 'flip cap',
    # 产品品类
    '沐浴露': 'body wash',
    # 比例
    '修长': 'tall and slender',
    '矮胖': 'short and wide',
    '均匀': 'balanced proportion',
    # 颜色
    '白色': 'white',
    '黑色': 'black',
    '黄色': 'yellow',
    '红色': 'red',
    '蓝色': 'blue',
}

def load_label_map():
    """加载映射表"""
    if os.path.exists(LABEL_MAP_FILE):
        try:
            with open(LABEL_MAP_FILE, 'r', encoding='utf-8') as f:
                return json.load(f)
        except Exception:
            pass
    return DEFAULT_LABEL_MAP.copy()

def save_label_map(data):
    """保存映射表"""
    with open(LABEL_MAP_FILE, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)

def load_settings():
    # Start from file; only use DEFAULT_SETTINGS for keys not present in file
    s = {}
    if os.path.exists(SETTINGS_FILE):
        try:
            with open(SETTINGS_FILE, 'r', encoding='utf-8') as f:
                s = json.load(f)
        except Exception:
            pass
    # Fill missing keys from defaults (pure ASCII paths)
    for k, v in DEFAULT_SETTINGS.items():
        if k not in s:
            s[k] = v
    return s

def save_settings_to_disk(data):
    with open(SETTINGS_FILE, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)

settings = load_settings()

# ── Training State ────────────────────────────────────────────────────────────
training_state = {
    'is_training': False, 'progress': 0.0,
    'current_step': 0, 'total_steps': 0,
    'current_epoch': 0, 'total_epochs': 0,
    'loss': 0.0, 'lr': 0.0,
    'start_time': None, 'mode': 'idle', 'error': None,
}
log_lines = []
log_lock = threading.Lock()
training_process = None

# ── Inference Pipeline Cache ─────────────────────────────────────────────────
_pipe_cache = {}  # key: (model_path, is_sdxl) -> pipe
_pipe_cache_key = None

def append_log(line):
    with log_lock:
        log_lines.append({'t': time.time(), 'msg': line})
        if len(log_lines) > 5000:
            del log_lines[:1000]

def get_kohya_path():
    return settings.get('kohya_path', r'D:\kohya_ss')

def get_kohya_python():
    kp = get_kohya_path()
    for rel in [r'venv\Scripts\python.exe', r'venv/bin/python']:
        p = os.path.join(kp, rel)
        if os.path.exists(p):
            return p
    return sys.executable

def load_ckpt_as_pipeline(model_path, is_sdxl, dtype, device):
    import os, torch
    from diffusers import StableDiffusionPipeline, StableDiffusionXLPipeline
    from diffusers.pipelines.stable_diffusion.convert_from_ckpt import download_from_original_stable_diffusion_ckpt
    # Use HF mirror for China
    os.environ.setdefault('HF_ENDPOINT', 'https://hf-mirror.com')
    # Patch torch.load for torch 2.6+ weights_only restriction
    _orig = torch.load
    def _patched(f, *a, **k):
        k['weights_only'] = False
        return _orig(f, *a, **k)
    torch.load = _patched
    try:
        pipe = download_from_original_stable_diffusion_ckpt(
            checkpoint_path_or_dict=model_path,
            original_config_file=r'D:\train\model\v1-inference.yaml' if not is_sdxl else None,
            from_safetensors=model_path.endswith('.safetensors'),
            extract_ema=False,
            image_size=512,
            device='cpu',
            load_safety_checker=False,
        )
        pipe = pipe.to(dtype=dtype)
    finally:
        torch.load = _orig
    return pipe

def get_accelerate():
    kp = get_kohya_path()
    for rel in [r'venv\Scripts\accelerate.exe', r'venv\Scripts\accelerate', r'venv/bin/accelerate']:
        p = os.path.join(kp, rel)
        if os.path.exists(p):
            return p
    return 'accelerate'

def kohya_available():
    kp = get_kohya_path()
    return os.path.exists(os.path.join(kp, 'sd-scripts', 'train_network.py'))

def get_train_script(model_type='sdxl'):
    kp = get_kohya_path()
    scripts = {
        'sdxl': 'sdxl_train_network.py',
        'sd15': 'train_network.py',
        'flux': 'flux_train_network.py',
        'sd3':  'sd3_train_network.py',
    }
    return os.path.join(kp, 'sd-scripts', scripts.get(model_type, 'train_network.py'))

# ── Build Kohya Command ──────────────────────────────────────────────────────
def build_kohya_cmd(cfg):
    kp = get_kohya_path()
    model_type = cfg.get('modelType', 'sdxl')
    # Auto-detect model type from filename if not explicitly set
    base_model = cfg.get('baseModel', '')
    if base_model:
        name_lower = os.path.basename(base_model).lower()
        if any(x in name_lower for x in ['v1-5', 'v1_5', 'sd15', 'sd1.5', '1.5', 'v1-inference']):
            model_type = 'sd15'
        elif any(x in name_lower for x in ['sdxl', 'xl', 'sd_xl']):
            model_type = 'sdxl'
        elif 'flux' in name_lower:
            model_type = 'flux'
        elif 'sd3' in name_lower:
            model_type = 'sd3'
    train_script = get_train_script(model_type)
    accelerate = get_accelerate()
    mixed_precision = cfg.get('mixedPrecision', 'fp16')

    cmd = [
        accelerate, 'launch',
        '--num_cpu_threads_per_process', str(cfg.get('numCpuThreads', 2)),
        '--num_processes', str(cfg.get('numProcesses', 1)),
        '--mixed_precision', mixed_precision,
    ]
    dynamo = cfg.get('dynamoBackend', 'no')
    if dynamo and dynamo != 'no':
        cmd += ['--dynamo_backend', dynamo]
    cmd.append(train_script)

    # Source model
    cmd += ['--pretrained_model_name_or_path', cfg['baseModel']]
    if cfg.get('vae'): cmd += ['--vae', cfg['vae']]
    if cfg.get('v2'): cmd.append('--v2')
    if cfg.get('vParameterization'): cmd.append('--v_parameterization')

    # Folders
    output_dir = cfg.get('outputPath') or settings.get('output_dir', os.path.join(BASE_DIR, 'output'))
    os.makedirs(output_dir, exist_ok=True)
    log_dir = cfg.get('loggingDir') or os.path.join(output_dir, 'logs')
    os.makedirs(log_dir, exist_ok=True)
    cmd += ['--train_data_dir', cfg['datasetPath'], '--output_dir', output_dir,
            '--output_name', cfg.get('modelName', 'lora_output'), '--logging_dir', log_dir]
    if cfg.get('regDataDir'): cmd += ['--reg_data_dir', cfg['regDataDir']]
    if cfg.get('datasetConfig'): cmd += ['--dataset_config', cfg['datasetConfig']]

    # Basic training
    cmd += [
        '--resolution',          cfg.get('resolution', '1024,1024'),
        '--train_batch_size',    str(cfg.get('batchSize', 1)),
        '--max_train_epochs',    str(cfg.get('epochs', 10)),
        '--save_every_n_epochs', str(cfg.get('saveEveryNEpochs', 1)),
        '--learning_rate',       str(cfg.get('learningRate', '1e-4')),
        '--lr_scheduler',        cfg.get('lrScheduler', 'cosine_with_restarts'),
        '--optimizer_type',      cfg.get('optimizer', 'AdamW8bit'),
        '--mixed_precision',     mixed_precision,
        '--save_precision',      cfg.get('savePrecision', 'fp16'),
        '--save_model_as',       cfg.get('saveModelAs', 'safetensors'),
    ]
    seed = int(cfg.get('seed', 0) or 0)
    if seed: cmd += ['--seed', str(seed)]
    max_steps = int(cfg.get('maxTrainSteps', 0) or 0)
    if max_steps > 0: cmd += ['--max_train_steps', str(max_steps)]
    lr_warmup_steps = int(cfg.get('lrWarmupSteps', 0) or 0)
    if lr_warmup_steps > 0: cmd += ['--lr_warmup_steps', str(lr_warmup_steps)]
    cycles = int(cfg.get('lrSchedulerNumCycles', 1) or 1)
    if cycles > 1: cmd += ['--lr_scheduler_num_cycles', str(cycles)]
    power = float(cfg.get('lrSchedulerPower', 1.0) or 1.0)
    if power != 1.0: cmd += ['--lr_scheduler_power', str(power)]
    if cfg.get('lrSchedulerArgs'): cmd += ['--lr_scheduler_args'] + cfg['lrSchedulerArgs'].split()
    if cfg.get('optimizerArgs'): cmd += ['--optimizer_args'] + cfg['optimizerArgs'].split()
    te_lr = str(cfg.get('textEncoderLr', '')).strip()
    unet_lr = str(cfg.get('unetLr', '')).strip()
    if te_lr: cmd += ['--text_encoder_lr', te_lr]
    if unet_lr: cmd += ['--unet_lr', unet_lr]

    # Captions
    cmd += ['--caption_extension', cfg.get('captionExtension', '.txt')]
    if cfg.get('shuffleCaption'): cmd.append('--shuffle_caption')
    kn = int(cfg.get('keepNTokens', 0) or 0)
    if kn > 0: cmd += ['--keep_n_tokens', str(kn)]
    mtl = int(cfg.get('maxTokenLength', 75) or 75)
    if mtl > 75: cmd += ['--max_token_length', str(mtl)]
    if cfg.get('weightedCaptions'): cmd.append('--weighted_captions')
    sep = str(cfg.get('keepTokenSeparator', '') or '').strip()
    if sep: cmd += ['--keep_tokens_separator', sep]

    # Bucket
    if cfg.get('enableBucket', True):
        cmd.append('--enable_bucket')
        cmd += ['--min_bucket_reso', str(int(cfg.get('minBucketReso', 256) or 256)),
                '--max_bucket_reso', str(int(cfg.get('maxBucketReso', 2048) or 2048)),
                '--bucket_reso_steps', str(int(cfg.get('bucketResoSteps', 64) or 64))]
        if cfg.get('bucketNoUpscale'): cmd.append('--bucket_no_upscale')

    # Caching
    if cfg.get('cacheLatents', True): cmd.append('--cache_latents')
    if cfg.get('cacheLatentsToDisk'): cmd.append('--cache_latents_to_disk')

    # Network (LoRA)
    cmd += ['--network_module', cfg.get('networkModule', 'networks.lora'),
            '--network_dim',    str(cfg.get('networkDim', 32)),
            '--network_alpha',  str(cfg.get('networkAlpha', 16))]
    nd = float(cfg.get('networkDropout', 0) or 0)
    if nd > 0: cmd += ['--network_dropout', str(nd)]
    network_args = []
    conv_dim = int(cfg.get('convDim', 0) or 0)
    conv_alpha = int(cfg.get('convAlpha', 0) or 0)
    if conv_dim > 0: network_args.append(f'conv_dim={conv_dim}')
    if conv_alpha > 0: network_args.append(f'conv_alpha={conv_alpha}')
    algo = str(cfg.get('algo', '') or '').strip()
    if algo: network_args.append(f'algo={algo}')
    if network_args: cmd += ['--network_args'] + network_args
    if cfg.get('networkWeights'): cmd += ['--network_weights', cfg['networkWeights']]
    if cfg.get('networkTrainUnetOnly'): cmd.append('--network_train_unet_only')
    if cfg.get('networkTrainTextEncoderOnly'): cmd.append('--network_train_text_encoder_only')

    # Advanced
    if cfg.get('gradientCheckpointing', True): cmd.append('--gradient_checkpointing')
    ga = int(cfg.get('gradientAccumulationSteps', 1) or 1)
    if ga > 1: cmd += ['--gradient_accumulation_steps', str(ga)]
    mgn = float(cfg.get('maxGradNorm', 1.0) or 1.0)
    if mgn != 1.0: cmd += ['--max_grad_norm', str(mgn)]
    if cfg.get('xformers'): cmd.append('--xformers')
    elif cfg.get('sdpaAttn'): cmd.append('--sdpa')
    if cfg.get('fp8BaseUnet'): cmd.append('--fp8_base_unet')
    if cfg.get('fullFp16'): cmd.append('--full_fp16')
    if cfg.get('noHalfVae'): cmd.append('--no_half_vae')
    if cfg.get('highvram'): cmd.append('--highvram')
    elif cfg.get('lowvram'): cmd.append('--lowvram')

    # Noise / SNR
    no = float(cfg.get('noiseOffset', 0) or 0)
    if no > 0:
        cmd += ['--noise_offset', str(no)]
        if cfg.get('noiseOffsetType') == 'multires':
            cmd += ['--multires_noise_discount', str(float(cfg.get('multiresMoiseDiscountRate', 0.3) or 0.3))]
    ans = float(cfg.get('adaptiveNoiseScale', 0) or 0)
    if ans != 0: cmd += ['--adaptive_noise_scale', str(ans)]
    ipng = float(cfg.get('ipNoiseGamma', 0) or 0)
    if ipng > 0: cmd += ['--ip_noise_gamma', str(ipng)]
    snr = float(cfg.get('minSnrGamma', 0) or 0)
    if snr > 0: cmd += ['--min_snr_gamma', str(snr)]
    if cfg.get('zeroTerminalSnr'): cmd.append('--zero_terminal_snr')
    if cfg.get('scaleVPredLoss'): cmd.append('--scale_v_pred_loss_like_noise_pred')
    loss_type = cfg.get('lossType', 'l2')
    if loss_type != 'l2': cmd += ['--loss_type', loss_type]

    # Data augmentation
    if cfg.get('flipAug'): cmd.append('--flip_aug')
    if cfg.get('colorAug'): cmd.append('--color_aug')
    if cfg.get('randomCrop'): cmd.append('--random_crop')

    # Save state
    if cfg.get('saveState'): cmd.append('--save_state')
    sln = int(cfg.get('saveLastNEpochs', 0) or 0)
    if sln > 0: cmd += ['--save_last_n_epochs', str(sln)]
    sens = int(cfg.get('saveEveryNSteps', 0) or 0)
    if sens > 0: cmd += ['--save_every_n_steps', str(sens)]
    if cfg.get('resumeFrom'): cmd += ['--resume', cfg['resumeFrom']]
    if cfg.get('trainingComment'): cmd += ['--training_comment', cfg['trainingComment']]

    if cfg.get('logWith', 'tensorboard') != 'none':
        cmd += ['--log_with', cfg.get('logWith', 'tensorboard')]

    if cfg.get('additionalArgs'):
        try:
            cmd += shlex.split(cfg['additionalArgs'])
        except Exception:
            pass
    return cmd

# ── Training Thread ─────────────────────────────────────────────────────────
def run_training_thread(cfg):
    global training_process
    try:
        cmd = build_kohya_cmd(cfg)
        kp = get_kohya_path()
        cmd_str = ' '.join(f'"{c}"' if ' ' in str(c) else str(c) for c in cmd)
        append_log(f'[START] {datetime.now().isoformat()}')
        append_log(f'[CMD] {cmd_str}')
        logger.info(f'Launching: {cmd_str}')

        env = dict(os.environ)
        env['PYTHONIOENCODING'] = 'utf-8'
        env['PYTHONUTF8'] = '1'
        training_process = subprocess.Popen(
            cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            text=True, encoding='utf-8', errors='replace', cwd=kp,
            env=env,
        )
        training_state['total_epochs'] = int(cfg.get('epochs', 10))

        for raw in training_process.stdout:
            line = raw.rstrip()
            if not line:
                continue
            append_log(line)

            m = re.search(r'epoch\s+(\d+)/(\d+)', line, re.IGNORECASE)
            if m:
                training_state['current_epoch'] = int(m.group(1))
                training_state['total_epochs'] = int(m.group(2))
                training_state['progress'] = training_state['current_epoch'] / training_state['total_epochs'] * 100

            # tqdm format: steps:  37%|...| 182/490 [..., avr_loss=0.08]
            m = re.search(r'(\d+)/(\d+)\s*\[', line)
            if m:
                current = int(m.group(1))
                total = int(m.group(2))
                if total > 0:
                    training_state['current_step'] = current
                    training_state['total_steps'] = total
                    training_state['progress'] = current / total * 100

            m = re.search(r'(\d+)/(\d+)\s+steps', line)
            if m:
                training_state['current_step'] = int(m.group(1))
                training_state['total_steps'] = int(m.group(2))

            m = re.search(r'avr_loss=(\d+\.\d+)', line)
            if m:
                training_state['loss'] = float(m.group(1))

            m = re.search(r'\bloss[=:\s]+(\d+\.\d+)', line, re.IGNORECASE)
            if m:
                training_state['loss'] = float(m.group(1))

            m = re.search(r'\blr[=:\s]+([\d.e+-]+)', line, re.IGNORECASE)
            if m:
                try:
                    training_state['lr'] = float(m.group(1))
                except ValueError:
                    pass

            if not training_state['is_training']:
                training_process.terminate()
                break

        training_process.wait()
        rc = training_process.returncode
        if training_state['is_training']:
            if rc == 0:
                training_state['progress'] = 100.0
                training_state['current_epoch'] = training_state['total_epochs']
                append_log(f'[DONE] Training completed successfully (exit code 0)')
            else:
                training_state['error'] = f'Process exited with code {rc}'
                append_log(f'[ERROR] exit code {rc}')
    except Exception as e:
        logger.error(f'Training thread error: {e}')
        training_state['error'] = str(e)
        append_log(f'[EXCEPTION] {e}')
    finally:
        training_state['is_training'] = False

# ── Model Scanning ───────────────────────────────────────────────────────────
def scan_models(directory, exts=('.safetensors', '.ckpt', '.pt')):
    models = []
    if not directory or not os.path.isdir(directory):
        return models
    for root, dirs, files in os.walk(directory):
        dirs[:] = [d for d in dirs if not d.startswith('.')]
        for f in files:
            if any(f.lower().endswith(e) for e in exts):
                full = os.path.join(root, f)
                rel = os.path.relpath(full, directory)
                try:
                    size = os.path.getsize(full)
                except OSError:
                    size = 0
                models.append({
                    'name': os.path.splitext(f)[0],
                    'filename': f,
                    'path': full,
                    'rel': rel,
                    'size': size,
                    'size_mb': round(size / 1024 / 1024, 1),
                    'mtime': os.path.getmtime(full),
                })
    models.sort(key=lambda x: x['mtime'], reverse=True)
    return models

# ── Flask Routes ──────────────────────────────────────────────────────────────

@app.route('/api/start_training', methods=['POST'])
def api_start_training():
    global training_process
    if training_state['is_training']:
        return jsonify({'success': False, 'message': '训练已在进行中'})
    cfg = request.json or {}
    if not cfg.get('baseModel'):
        return jsonify({'success': False, 'message': '缺少 baseModel 参数'}), 400
    if not cfg.get('datasetPath'):
        return jsonify({'success': False, 'message': '缺少 datasetPath 参数'}), 400
    if not kohya_available():
        return jsonify({'success': False, 'message': f'Kohya_ss 未找到，请检查路径: {get_kohya_path()}'}), 400

    training_state.update({
        'is_training': True, 'progress': 0.0,
        'current_step': 0, 'total_steps': 0,
        'current_epoch': 0, 'total_epochs': int(cfg.get('epochs', 10)),
        'loss': 0.0, 'lr': 0.0,
        'start_time': time.time(), 'mode': 'kohya', 'error': None,
    })
    with log_lock:
        log_lines.clear()

    t = threading.Thread(target=run_training_thread, args=(cfg,), daemon=True)
    t.start()
    return jsonify({'success': True, 'message': '训练已启动'})


@app.route('/api/stop_training', methods=['POST'])
def api_stop_training():
    global training_process
    if not training_state['is_training']:
        return jsonify({'success': False, 'message': '没有正在运行的训练'})
    training_state['is_training'] = False
    if training_process and training_process.poll() is None:
        training_process.terminate()
        try:
            training_process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            training_process.kill()
    append_log('[STOPPED] 训练被用户中止')
    return jsonify({'success': True, 'message': '训练已停止'})


@app.route('/api/training_status', methods=['GET'])
def api_training_status():
    s = dict(training_state)
    if s['start_time']:
        s['elapsed'] = round(time.time() - s['start_time'], 1)
    else:
        s['elapsed'] = 0
    return jsonify(s)


@app.route('/api/training_logs', methods=['GET'])
def api_training_logs():
    since = float(request.args.get('since', 0))
    with log_lock:
        if since:
            filtered = [l for l in log_lines if l['t'] > since]
        else:
            filtered = list(log_lines[-200:])
    return jsonify({'logs': filtered, 'server_time': time.time()})


@app.route('/api/training_logs/stream')
def api_training_logs_stream():
    """SSE stream of log lines."""
    def generate():
        sent = 0
        while True:
            with log_lock:
                new = log_lines[sent:]
            for item in new:
                data = json.dumps(item, ensure_ascii=False)
                yield f'data: {data}\n\n'
            sent += len(new)
            if not training_state['is_training'] and sent >= len(log_lines):
                yield 'data: {"done":true}\n\n'
                break
            time.sleep(0.3)
    return Response(stream_with_context(generate()),
                    mimetype='text/event-stream',
                    headers={'Cache-Control': 'no-cache', 'X-Accel-Buffering': 'no'})


@app.route('/api/kohya_status', methods=['GET'])
def api_kohya_status():
    installed = kohya_available()
    kp = get_kohya_path()
    return jsonify({
        'installed': installed,
        'path': kp,
        'python': get_kohya_python(),
        'accelerate': get_accelerate(),
        'message': 'Kohya_ss 已就绪' if installed else f'未找到训练脚本，请检查路径: {kp}',
    })


@app.route('/api/check_environment', methods=['GET'])
def api_check_environment():
    try:
        import torch
        torch_ver = torch.__version__
        cuda_ok = torch.cuda.is_available()
        cuda_ver = torch.version.cuda if cuda_ok else None
        gpu_count = torch.cuda.device_count() if cuda_ok else 0
        gpus = []
        for i in range(gpu_count):
            gpus.append({
                'index': i,
                'name': torch.cuda.get_device_name(i),
                'memory_total': round(torch.cuda.get_device_properties(i).total_memory / 1024**3, 1),
            })
    except ImportError:
        torch_ver = None; cuda_ok = False; cuda_ver = None; gpu_count = 0; gpus = []

    deps = {}
    for pkg in ['transformers', 'diffusers', 'accelerate', 'peft', 'safetensors', 'xformers']:
        try:
            m = __import__(pkg)
            deps[pkg] = getattr(m, '__version__', 'installed')
        except ImportError:
            deps[pkg] = None

    kohya_ok = kohya_available()
    system_ready = bool(torch_ver and cuda_ok and kohya_ok)
    return jsonify({
        'python': f'{sys.version_info.major}.{sys.version_info.minor}.{sys.version_info.micro}',
        'pytorch': torch_ver,
        'cuda_available': cuda_ok,
        'cuda_version': cuda_ver,
        'gpu_count': gpu_count,
        'gpus': gpus,
        'dependencies': deps,
        'kohya_installed': kohya_ok,
        'kohya_path': get_kohya_path(),
        'system_ready': system_ready,
    })

@app.route('/api/get_models', methods=['GET'])
def api_get_models():
    model_type = request.args.get('type', 'lora')
    if model_type == 'lora':
        directory = settings.get('output_dir', os.path.join(BASE_DIR, 'output'))
    elif model_type == 'base':
        directory = settings.get('models_dir', r'D:\models')
    else:
        directory = settings.get('output_dir', os.path.join(BASE_DIR, 'output'))
    models = scan_models(directory)
    return jsonify({'success': True, 'models': models, 'directory': directory})


@app.route('/api/scan_directory', methods=['POST'])
def api_scan_directory():
    data = request.json or {}
    directory = data.get('path', '')
    exts = data.get('exts', ['.safetensors', '.ckpt', '.pt'])
    models = scan_models(directory, exts=tuple(exts))
    return jsonify({'success': True, 'models': models, 'count': len(models)})


@app.route('/api/scan_models', methods=['GET'])
def api_scan_models():
    directory = settings.get('models_dir', r'D:\models')
    models = scan_models(directory, exts=('.safetensors', '.ckpt', '.pt'))
    return jsonify({'success': True, 'models': models})


@app.route('/api/browse_dir', methods=['POST'])
def api_browse_dir():
    """列出指定路径下的子目录和上级目录，用于前端目录浏览"""
    data = request.json or {}
    current = data.get('path', 'C:\\')
    if not current or not os.path.isdir(current):
        current = 'C:\\'
    parent = os.path.dirname(current.rstrip('\\/')) or current
    try:
        entries = []
        for name in sorted(os.listdir(current)):
            full = os.path.join(current, name)
            if os.path.isdir(full):
                entries.append({'name': name, 'path': full})
        drives = []
        import string
        for d in string.ascii_uppercase:
            dp = d + ':\\'
            if os.path.isdir(dp):
                drives.append({'name': dp, 'path': dp})
        return jsonify({'success': True, 'current': current, 'parent': parent, 'entries': entries, 'drives': drives})
    except PermissionError:
        return jsonify({'success': False, 'error': '无权限访问该目录', 'current': current, 'parent': parent, 'entries': [], 'drives': []})


@app.route('/api/scan_datasets', methods=['GET'])
def api_scan_datasets():
    base = settings.get('dataset_base', r'D:\dataset')
    dirs = []
    if os.path.isdir(base):
        for name in os.listdir(base):
            full = os.path.join(base, name)
            if os.path.isdir(full):
                dirs.append(full)
    return jsonify({'success': True, 'dirs': dirs})


@app.route('/api/preview_command', methods=['POST'])
def api_preview_command():
    data = request.json or {}
    try:
        cmd = build_kohya_cmd(data)
        return jsonify({'success': True, 'command': ' '.join(str(c) for c in cmd)})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e), 'command': str(e)})


@app.route('/api/list_subdirs', methods=['POST'])
def api_list_subdirs():
    data = request.json or {}
    directory = data.get('path', '')
    if not directory or not os.path.isdir(directory):
        return jsonify({'success': False, 'dirs': [], 'message': f'目录不存在: {directory}'})
    dirs = []
    try:
        for name in sorted(os.listdir(directory)):
            full = os.path.join(directory, name)
            if os.path.isdir(full) and not name.startswith('.'):
                dirs.append({'name': name, 'path': full})
    except Exception as e:
        return jsonify({'success': False, 'dirs': [], 'message': str(e)})
    return jsonify({'success': True, 'dirs': dirs, 'count': len(dirs)})


@app.route('/api/generate_image', methods=['POST'])
def api_generate_image():
    data = request.json or {}
    prompt = data.get('prompt', '')
    negative_prompt = data.get('negative_prompt', '')
    model_path = data.get('model_path', '')
    loras = data.get('loras', [])  # [{path, weight}]
    width = int(data.get('width', 1024))
    height = int(data.get('height', 1024))
    steps = int(data.get('steps', 28))
    cfg_scale = float(data.get('cfg_scale', 7.0))
    seed = int(data.get('seed', -1))
    num_images = int(data.get('num_images', 1))
    scheduler = data.get('scheduler', 'euler_a')

    if not model_path:
        model_path = settings.get('inference_model', '')
    if not model_path or not os.path.exists(model_path):
        return jsonify({'success': False, 'message': f'模型文件不存在: {model_path}'}), 400

    try:
        import torch
        from diffusers import (
            StableDiffusionXLPipeline, StableDiffusionPipeline,
            EulerAncestralDiscreteScheduler, EulerDiscreteScheduler,
            DPMSolverMultistepScheduler, DDIMScheduler,
        )
        from diffusers.models import AutoencoderKL
        import random, base64
        from io import BytesIO

        # Use explicit flag from frontend; only fall back to size heuristic if not provided
        is_sdxl_explicit = data.get('is_sdxl')
        if is_sdxl_explicit is None:
            # heuristic: SDXL base is ~6.5GB, but SD1.5 full precision can also be large
            # safer default: assume SD1.5 unless filename contains 'xl' or 'sdxl'
            fname = os.path.basename(model_path).lower()
            is_sdxl = 'xl' in fname or 'sdxl' in fname
        else:
            is_sdxl = bool(is_sdxl_explicit)

        device = 'cuda' if torch.cuda.is_available() else 'cpu'
        dtype = torch.float16 if device == 'cuda' else torch.float32

        # Use cached pipeline if same model
        global _pipe_cache, _pipe_cache_key
        cache_key = (model_path, is_sdxl)
        if _pipe_cache_key != cache_key or not _pipe_cache:
            # Clear old cache to free VRAM
            if _pipe_cache:
                old_pipe = _pipe_cache.get('pipe')
                if old_pipe is not None:
                    del old_pipe
                _pipe_cache = {}
                import gc; gc.collect()
                if device == 'cuda':
                    torch.cuda.empty_cache()

            PipeClass = StableDiffusionXLPipeline if is_sdxl else StableDiffusionPipeline
            # Use custom loader to bypass from_single_file bugs
            pipe = load_ckpt_as_pipeline(model_path, is_sdxl, dtype, device)

            sched_map = {
                'euler_a':   EulerAncestralDiscreteScheduler,
                'euler':     EulerDiscreteScheduler,
                'dpm++2m':   DPMSolverMultistepScheduler,
                'ddim':      DDIMScheduler,
            }
            sched_cls = sched_map.get(scheduler, EulerAncestralDiscreteScheduler)
            pipe.scheduler = sched_cls.from_config(pipe.scheduler.config)
            pipe = pipe.to(device)
            pipe.enable_attention_slicing(1)
            if hasattr(pipe, 'enable_vae_slicing'): pipe.enable_vae_slicing()
            if device == 'cuda':
                try:
                    pipe.enable_xformers_memory_efficient_attention()
                    logger.info('xformers enabled')
                except Exception:
                    try:
                        pipe.unet.set_attn_processor(__import__('diffusers').models.attention_processor.AttnProcessor2_0())
                    except Exception:
                        pass
                torch.cuda.empty_cache()

            _pipe_cache = {'pipe': pipe}
            _pipe_cache_key = cache_key
            logger.info(f'Pipeline loaded and cached: {model_path} is_sdxl={is_sdxl}')
        else:
            pipe = _pipe_cache['pipe']
            # Update scheduler if changed
            sched_map = {
                'euler_a':   EulerAncestralDiscreteScheduler,
                'euler':     EulerDiscreteScheduler,
                'dpm++2m':   DPMSolverMultistepScheduler,
                'ddim':      DDIMScheduler,
            }
            sched_cls = sched_map.get(scheduler, EulerAncestralDiscreteScheduler)
            pipe.scheduler = sched_cls.from_config(pipe.scheduler.config)
            logger.info(f'Using cached pipeline for: {model_path}')

        # Load LoRAs
        # Unload any previously loaded LoRAs first
        try:
            pipe.unfuse_lora()
        except Exception:
            pass
        try:
            pipe.unload_lora_weights()
        except Exception:
            pass
        for lora in loras:
            lp = lora.get('path', '')
            lw = float(lora.get('weight', 0.8))
            if lp and os.path.exists(lp):
                adapter_name = os.path.splitext(os.path.basename(lp))[0]
                pipe.load_lora_weights(lp, adapter_name=adapter_name)
                pipe.set_adapters([adapter_name], adapter_weights=[lw])

        # Generate
        if seed < 0:
            seed = random.randint(0, 2**32 - 1)
        generator = torch.Generator(device=device).manual_seed(seed)

        start = time.time()
        kwargs = dict(
            prompt=prompt,
            negative_prompt=negative_prompt,
            width=width, height=height,
            num_inference_steps=steps,
            guidance_scale=cfg_scale,
            generator=generator,
            num_images_per_prompt=num_images,
        )
        result = pipe(**kwargs)
        elapsed = round(time.time() - start, 2)

        images_b64 = []
        out_dir = os.path.join(BASE_DIR, 'static', 'generated')
        os.makedirs(out_dir, exist_ok=True)
        for i, img in enumerate(result.images):
            buf = BytesIO()
            img.save(buf, format='PNG')
            b64 = base64.b64encode(buf.getvalue()).decode()
            images_b64.append(f'data:image/png;base64,{b64}')

        return jsonify({
            'success': True,
            'images': images_b64,
            'seed': seed,
            'elapsed': elapsed,
            'count': len(images_b64),
        })

    except Exception as e:
        logger.error(f'Inference error: {e}', exc_info=True)
        return jsonify({'success': False, 'message': str(e)}), 500


@app.route('/api/settings', methods=['GET'])
def api_get_settings():
    return jsonify(load_settings())


@app.route('/api/settings', methods=['POST'])
def api_save_settings():
    global settings
    data = request.json or {}
    settings.update(data)
    save_settings_to_disk(settings)
    # Reload from disk to confirm
    settings = load_settings()
    return jsonify({'success': True, 'message': '设置已保存', 'settings': settings})


@app.route('/api/build_cmd_preview', methods=['POST'])
def api_build_cmd_preview():
    """Return the command that would be run, for user inspection."""
    cfg = request.json or {}
    if not cfg.get('baseModel'):
        cfg['baseModel'] = '/path/to/model.safetensors'
    if not cfg.get('datasetPath'):
        cfg['datasetPath'] = '/path/to/dataset'
    try:
        cmd = build_kohya_cmd(cfg)
        return jsonify({'success': True, 'cmd': cmd, 'cmd_str': ' '.join(
            f'"{c}"' if ' ' in str(c) else str(c) for c in cmd
        )})
    except Exception as e:
        return jsonify({'success': False, 'message': str(e)}), 400


@app.route('/api/label_map', methods=['GET'])
def api_get_label_map():
    """获取映射表"""
    return jsonify({'success': True, 'map': load_label_map()})


@app.route('/api/label_map', methods=['POST'])
def api_save_label_map():
    """保存映射表"""
    data = request.json or {}
    map_data = data.get('map', {})
    try:
        save_label_map(map_data)
        return jsonify({'success': True, 'message': '映射表已保存'})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)})


@app.route('/api/label/scan', methods=['POST'])
def api_label_scan():
    """扫描文件夹内图片，返回预览列表"""
    data = request.json or {}
    folder = data.get('folder', '')
    if not os.path.isdir(folder):
        return jsonify({'success': False, 'error': f'目录不存在: {folder}'})
    IMAGE_EXTS = {'.png', '.jpg', '.jpeg', '.webp', '.bmp'}
    images = sorted([f for f in os.listdir(folder)
                     if os.path.splitext(f)[1].lower() in IMAGE_EXTS])
    sys.path.insert(0, BASE_DIR)
    import importlib, label_tool
    importlib.reload(label_tool)
    from label_tool import parse_filename_to_tags, build_caption_with_trigger
    # 使用后端保存的映射表
    label_tool.ZH2EN.update(load_label_map())
    trigger_word = data.get('trigger', '')
    result = []
    for idx, name in enumerate(images, 1):
        tags = parse_filename_to_tags(name)
        caption = build_caption_with_trigger(tags, trigger_word)
        result.append({
            'idx': idx,
            'original': name,
            'new_name': f'{idx:03d}{os.path.splitext(name)[1].lower()}',
            'txt_name': f'{idx:03d}.txt',
            'caption': caption,
            'tags': tags,
        })
    return jsonify({'success': True, 'items': result, 'count': len(result)})


@app.route('/api/label/run', methods=['POST'])
def api_label_run():
    """执行打标：重命名图片 + 写入 txt"""
    import shutil
    data = request.json or {}
    folder = data.get('folder', '')
    trigger_word = data.get('trigger', '')
    if not os.path.isdir(folder):
        return jsonify({'success': False, 'error': f'目录不存在: {folder}'})
    IMAGE_EXTS = {'.png', '.jpg', '.jpeg', '.webp', '.bmp'}
    images = sorted([f for f in os.listdir(folder)
                     if os.path.splitext(f)[1].lower() in IMAGE_EXTS])
    sys.path.insert(0, BASE_DIR)
    import importlib, label_tool
    importlib.reload(label_tool)
    from label_tool import parse_filename_to_tags, build_caption_with_trigger
    # 使用后端保存的映射表
    label_tool.ZH2EN.update(load_label_map())
    done = []
    for idx, name in enumerate(images, 1):
        src = os.path.join(folder, name)
        ext = os.path.splitext(name)[1].lower()
        new_img = os.path.join(folder, f'{idx:03d}{ext}')
        new_txt = os.path.join(folder, f'{idx:03d}.txt')
        tags = parse_filename_to_tags(name)
        caption = build_caption_with_trigger(tags, trigger_word)
        if src != new_img:
            shutil.move(src, new_img)
        with open(new_txt, 'w', encoding='utf-8') as f:
            f.write(caption)
        done.append({'idx': idx, 'new_name': f'{idx:03d}{ext}', 'caption': caption})
    return jsonify({'success': True, 'done': done, 'count': len(done)})


@app.route('/api/label/edit', methods=['POST'])
def api_label_edit():
    """编辑单个 txt"""
    data = request.json or {}
    folder = data.get('folder', '')
    txt_name = data.get('txt_name', '')
    caption = data.get('caption', '')
    path = os.path.join(folder, txt_name)
    try:
        with open(path, 'w', encoding='utf-8') as f:
            f.write(caption)
        return jsonify({'success': True})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)})


if __name__ == '__main__':
    os.makedirs(os.path.join(BASE_DIR, 'output'), exist_ok=True)
    os.makedirs(os.path.join(BASE_DIR, 'static', 'generated'), exist_ok=True)
    logger.info(f'Kohya_ss path: {get_kohya_path()} | Available: {kohya_available()}')
    logger.info('Starting Flask on http://0.0.0.0:5000')
    app.run(host='0.0.0.0', port=5000, debug=False, threaded=True)
