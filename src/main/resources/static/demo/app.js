/* ============================================================
 * Moka Agent Demo Console — Application Logic
 * ============================================================ */

// ============================================================
// 1. 状态管理
// ============================================================
const state = {
    currentPage: 1,
    isUploading: false,
    uploadStatus: '',          // '' | 'uploading' | 'identifying' | 'done' | 'error'
    uploadError: '',
    orderData: null,
    dishKnowledge: null,
    realtimeInfo: null,
    experienceUnderstanding: null,
    conversationPlan: null,
    runtimePrompt: null,
};

// 用于取消进行中的请求
let currentAbortController = null;

// ============================================================
// 2. DOM 缓存
// ============================================================
const $ = (id) => document.getElementById(id);

const el = {};
function cacheDom() {
    el.header         = $('header');
    el.stepIndicator  = $('step-indicator');
    el.step1          = $('step-1');
    el.step2          = $('step-2');
    el.page1          = $('page1');
    el.page2          = $('page2');
    el.dropZone       = $('drop-zone');
    el.fileInput      = $('file-input');
    el.uploadBtn      = $('upload-btn');
    el.progressText   = $('progress-text');
    el.orderContent   = $('order-content');
    el.dishContent    = $('dish-content');
    el.realtimeContent = $('realtime-content');
    el.resetBtn       = $('reset-btn');
    el.nextBtn        = $('next-btn');
    el.experienceList = $('experience-list');
    el.arrow1         = $('arrow1');
    el.planContent    = $('plan-content');
    el.arrow2         = $('arrow2');
    el.promptCode     = $('prompt-code');
    el.promptMeta     = $('prompt-meta');
    el.backBtn        = $('back-btn');
    el.callBtn        = $('call-btn');
    el.experienceBox  = $('experience-box');
    el.planBox        = $('plan-box');
    el.promptBox      = $('prompt-box');
    el.page2Buttons   = $('page2-buttons');
}

// ============================================================
// 3. 初始状态模板（供 reset 恢复用）
// ============================================================
const TEMPLATES = {
    order:      '<span class="text-gray-500 text-sm">等待上传...</span>',
    dish:       '<span class="text-gray-500 text-sm">等待上传...</span>',
    realtime:   '<span class="text-gray-500 text-sm">等待上传...</span>',
    experience: '<p class="text-gray-500 text-sm">等待上传...</p>',
    plan:       '<p class="text-gray-500 text-sm">等待上传...</p>',
    prompt:     '等待上传...',
};

// ============================================================
// 4. 渲染函数
// ============================================================

/** 页面切换 */
function renderPage() {
    const show1 = state.currentPage === 1;
    el.page1.classList.toggle('hidden', !show1);
    el.page2.classList.toggle('hidden', show1);
    // 从 page2 回到 page1 时重置动画状态
    if (show1) {
        resetAnimationState();
    }
}

/** 步骤条 */
function renderStepIndicator() {
    const hasData = state.orderData !== null;
    const step2Circle = el.step2.querySelector('span:first-child');
    if (hasData) {
        el.step2.classList.remove('text-gray-600');
        el.step2.classList.add('text-gray-100', 'cursor-pointer');
        step2Circle.classList.remove('bg-gray-800', 'text-gray-500');
        step2Circle.classList.add('bg-indigo-600', 'text-white');
        el.step2.onclick = () => goToPage2();
    } else {
        el.step2.classList.remove('text-gray-100', 'cursor-pointer');
        el.step2.classList.add('text-gray-600');
        step2Circle.classList.remove('bg-indigo-600', 'text-white');
        step2Circle.classList.add('bg-gray-800', 'text-gray-500');
        el.step2.onclick = null;
    }
}

/** 上传区域三种状态 */
function renderUploadArea() {
    const btn = el.uploadBtn;
    const dropText = el.dropZone.querySelector('p');
    const iconDiv = el.dropZone.querySelector('div:first-child');

    switch (state.uploadStatus) {
        case '':
            btn.disabled = true;
            btn.textContent = '开始解析订单';
            dropText.innerHTML = '将小票照片拖到此处<br>或点击选择';
            iconDiv.textContent = '\u{1F4F8}';
            el.dropZone.classList.remove('border-green-500', 'border-red-500');
            el.dropZone.classList.add('border-gray-700', 'border-dashed');
            break;

        case 'uploading':
        case 'identifying':
            btn.disabled = true;
            btn.textContent = '⏳ 解析中...';
            iconDiv.textContent = '\u{1F4E4}';
            el.dropZone.classList.remove('border-green-500', 'border-red-500');
            el.dropZone.classList.add('border-gray-700', 'border-dashed');
            break;

        case 'done':
            btn.disabled = false;
            btn.textContent = '✅ 解析完成';
            dropText.innerHTML = '点击重新上传';
            iconDiv.textContent = '✅';
            el.dropZone.classList.remove('border-red-500', 'border-gray-700');
            el.dropZone.classList.add('border-green-500', 'border-solid');
            break;

        case 'error':
            btn.disabled = false;
            btn.textContent = '\u{1F504} 重试';
            dropText.innerHTML = state.uploadError || '上传失败';
            iconDiv.textContent = '⚠️';
            el.dropZone.classList.remove('border-green-500', 'border-gray-700');
            el.dropZone.classList.add('border-red-500', 'border-solid');
            break;
    }
}

/** 进度文字 */
function renderProgress(text) {
    el.progressText.textContent = text;
}

/** B1 订单信息 */
function renderOrderData() {
    const data = state.orderData;
    if (!data) {
        el.orderContent.innerHTML = TEMPLATES.order;
        return;
    }
    const items = (data.items || []).map(item =>
        `<tr class="border-b border-gray-800 last:border-0">
            <td class="py-1.5 text-gray-100">${esc(item.name)}</td>
            <td class="py-1.5 text-gray-400 text-center">${item.quantity || 1}</td>
            <td class="py-1.5 text-gray-300 text-right">¥${esc(item.price || '—')}</td>
            <td class="py-1.5 text-gray-500 text-right text-xs">${renderItemTags(item)}</td>
        </tr>`
    ).join('');

    el.orderContent.innerHTML = `
        <div class="space-y-2">
            <div class="flex items-center gap-2">
                <span class="text-lg">🏪</span>
                <span class="text-base font-semibold text-gray-100">${esc(data.restaurant || '未知')}</span>
            </div>
            <div class="grid grid-cols-2 gap-2 text-sm">
                <div><span class="text-gray-500">时间：</span>${esc(data.time || '—')}</div>
                <div><span class="text-gray-500">人数：</span>${data.people || '—'} 人</div>
            </div>
            <table class="w-full text-sm mt-2">
                <thead><tr class="text-gray-500 text-xs border-b border-gray-800">
                    <th class="text-left pb-1">菜品</th>
                    <th class="text-center pb-1">数量</th>
                    <th class="text-right pb-1">价格</th>
                    <th class="text-right pb-1">备注</th>
                </tr></thead>
                <tbody>${items}</tbody>
            </table>
        </div>
    `;
}

/** 菜品标签渲染 */
function renderItemTags(item) {
    const tags = [];
    if (item.spiceLevel) tags.push(`🌶️ ${esc(item.spiceLevel)}`);
    if (item.notes) tags.push(`📝 ${esc(item.notes)}`);
    return tags.join(' ') || '';
}

/** 去除括号及内容（全角半角均支持），用于前端比对 */
function stripParens(s) {
    return s.replace(/[（(][^）)]*[）)]/g, '').trim();
}

/** B2 菜品知识 */
function renderDishKnowledge() {
    const dishes = state.dishKnowledge || [];
    const orderItems = state.orderData?.items || [];

    if (!dishes.length && !orderItems.length) {
        el.dishContent.innerHTML = TEMPLATES.dish;
        return;
    }

    let html = '<div class="space-y-2">';
    orderItems.forEach(item => {
        // 先精确匹配，再试试去括号匹配
        let matched = dishes.find(dk => dk.dishName === item.name);
        if (!matched) {
            const stripped = stripParens(item.name);
            if (stripped !== item.name) {
                matched = dishes.find(dk => stripParens(dk.dishName) === stripped);
            }
        }
        if (matched) {
            const features = (matched.features || []).map(f =>
                `<span class="inline-block bg-indigo-900/40 text-indigo-300 text-xs px-2 py-0.5 rounded-full mr-1">${esc(f)}</span>`
            ).join('');
            const tags = (matched.experienceTags || []).map(t =>
                `<span class="inline-block text-gray-400 text-xs mr-2">${esc(t)}</span>`
            ).join('');
            html += `
                <div class="border border-gray-800 rounded-lg p-3">
                    <div class="flex items-center justify-between mb-1">
                        <span class="text-sm font-medium text-gray-100">${esc(matched.dishName)}</span>
                        <span class="text-xs text-gray-500">${esc(matched.dishRole || '')}</span>
                    </div>
                    <div class="mb-1">${features}</div>
                    <div>${tags}</div>
                </div>`;
        } else {
            html += `
                <div class="border border-gray-800 rounded-lg p-3 opacity-50">
                    <span class="text-sm text-gray-500">⚠️ 未找到「${esc(item.name)}」的菜品知识</span>
                </div>`;
        }
    });
    html += '</div>';
    el.dishContent.innerHTML = html;
}

/** B3 实时环境 */
function renderRealtimeInfo() {
    const data = state.realtimeInfo;
    if (!data) {
        el.realtimeContent.innerHTML = TEMPLATES.realtime;
        return;
    }
    const holiday = data.holiday ? esc(data.holiday) : '无';
    el.realtimeContent.innerHTML = `
        <div class="grid grid-cols-1 sm:grid-cols-3 gap-3 text-sm">
            <div class="flex items-center gap-2">
                <span>☀️</span>
                <span class="text-gray-100">${esc(data.weather || '—')}</span>
            </div>
            <div class="flex items-center gap-2">
                <span>🕐</span>
                <span class="text-gray-100">${esc(data.currentTime || '—')}</span>
            </div>
            <div class="flex items-center gap-2">
                <span>🎉</span>
                <span class="text-gray-100">${holiday}</span>
            </div>
        </div>
    `;
}

/** C1 体验理解 */
function renderExperienceUnderstanding() {
    const exp = state.experienceUnderstanding;
    if (!exp || !exp.possibilities || !exp.possibilities.length) {
        el.experienceList.innerHTML = '<p class="text-gray-500 text-sm">ℹ️ 暂无体验理解数据</p>';
        return;
    }
    const items = exp.possibilities.map(p => {
        const level = p.confidenceLevel || 'LOW';
        const badgeClass = level === 'MEDIUM'
            ? 'bg-green-600 text-white'
            : 'bg-yellow-400 text-yellow-900';
        return `
            <div class="border border-gray-800 rounded-lg p-3">
                <div class="flex items-start gap-2 mb-1">
                    <span class="inline-block text-xs font-bold px-2 py-0.5 rounded ${badgeClass}">${esc(level)}</span>
                    <span class="text-sm text-gray-100">${esc(p.description)}</span>
                </div>
                <div class="text-xs text-gray-500 mt-1 pl-1">依据：${esc(p.evidenceSource || '')}</div>
            </div>`;
    }).join('');
    el.experienceList.innerHTML = items;
}

/** C2 对话规划 */
function renderConversationPlan() {
    const plan = state.conversationPlan;
    if (!plan) {
        el.planContent.innerHTML = TEMPLATES.plan;
        return;
    }

    const section = (title, items, borderColor) => {
        if (!items || !items.length) {
            return `<div class="mb-3">
                <h3 class="text-xs font-semibold uppercase tracking-wider text-gray-500 mb-1">${title}</h3>
                <p class="text-gray-600 text-sm">无</p>
            </div>`;
        }
        const list = items.map(i => `<li class="text-sm text-gray-100 ml-4">${esc(i)}</li>`).join('');
        return `<div class="mb-3 border-l-4 ${borderColor} pl-3">
            <h3 class="text-xs font-semibold uppercase tracking-wider text-gray-500 mb-1">${title}</h3>
            <ul class="space-y-0.5">${list}</ul>
        </div>`;
    };

    el.planContent.innerHTML =
        section('方向', plan.directions, 'border-green-500') +
        section('机会点', plan.availableHooks, 'border-blue-500') +
        section('限制', plan.avoid, 'border-red-500');
}

/** C3 Runtime Prompt */
function renderRuntimePrompt() {
    const rp = state.runtimePrompt;
    if (!rp || !rp.finalPrompt) {
        el.promptCode.textContent = TEMPLATES.prompt;
        el.promptMeta.textContent = '';
        return;
    }
    el.promptCode.textContent = rp.finalPrompt;
    el.promptCode.classList.remove('text-gray-400');
    el.promptCode.classList.add('text-gray-100');
    el.promptMeta.textContent =
        `字符数: ${rp.finalPrompt.length} | 生成耗时: ${rp.assemblyDurationMs || '?'}ms`;
}

/** 页面 2 动画状态重置 */
function resetAnimationState() {
    [el.experienceBox, el.arrow1, el.planBox, el.arrow2, el.promptBox, el.page2Buttons].forEach(el => {
        if (el) {
            el.classList.remove('fade-in');
            el.classList.add('opacity-0');
        }
    });
    if (el.callBtn) el.callBtn.classList.remove('fade-in');
}

/** 页面 2 逐步骤淡入 */
function animatePage2() {
    const steps = [
        { el: el.experienceBox, delay: 500 },
        { el: el.arrow1,        delay: 1000 },
        { el: el.planBox,       delay: 1500 },
        { el: el.arrow2,        delay: 2000 },
        { el: el.promptBox,     delay: 2500 },
        { el: el.page2Buttons,  delay: 3000 },
    ];
    if (el.callBtn) {
        steps.push({ el: el.callBtn, delay: 3100 });
    }
    steps.forEach(({ el: target, delay }) => {
        setTimeout(() => {
            if (target) {
                target.classList.remove('opacity-0');
                target.classList.add('fade-in');
            }
        }, delay);
    });
}

// ============================================================
// 5. API 调用
// ============================================================
async function handleFileUpload(file) {
    // 校验文件类型
    if (!file.type.startsWith('image/')) {
        state.uploadStatus = 'error';
        state.uploadError = '请上传 JPG/PNG 格式的图片';
        renderUploadArea();
        return;
    }

    // 取消上一次进行中的请求
    if (currentAbortController) {
        currentAbortController.abort();
    }
    currentAbortController = new AbortController();
    const signal = currentAbortController.signal;

    // 120 秒超时
    const timeoutId = setTimeout(() => currentAbortController.abort(), 120000);

    state.isUploading = true;
    state.uploadStatus = 'uploading';
    renderUploadArea();
    renderProgress('\u{1F4E4} 图片上传中...');

    // 延迟显示识别状态
    setTimeout(() => {
        if (state.isUploading) {
            state.uploadStatus = 'identifying';
            renderProgress('\u{1F50D} 视觉模型识别中...    （通常需要 60-90 秒）');
        }
    }, 800);

    const formData = new FormData();
    formData.append('file', file);

    try {
        const response = await fetch('/api/v1/calls/demo', {
            method: 'POST',
            body: formData,
            signal,
        });
        clearTimeout(timeoutId);

        if (!response.ok) {
            throw new Error(`服务器错误: ${response.status}${response.statusText ? ' ' + response.statusText : ''}`);
        }

        const data = await response.json();

        if (!data.success) {
            throw new Error('后端处理失败');
        }

        // 填充数据
        state.orderData = data.orderData || null;
        state.dishKnowledge = data.dishKnowledge || null;
        state.realtimeInfo = data.realtimeInfo || null;
        state.experienceUnderstanding = data.experienceUnderstanding || null;
        state.conversationPlan = data.conversationPlan || null;
        state.runtimePrompt = data.runtimePrompt || null;

        state.isUploading = false;
        state.uploadStatus = 'done';
        currentAbortController = null;

        // 更新 UI
        renderUploadArea();
        renderProgress('✅ 解析完成');
        renderOrderData();
        renderDishKnowledge();
        renderRealtimeInfo();

        // 启用步骤 ② 和下一步按钮
        renderStepIndicator();
        el.nextBtn.disabled = false;

    } catch (err) {
        clearTimeout(timeoutId);
        if (err.name === 'AbortError') {
            state.uploadError = '请求超时（超过 120 秒），请重试';
        } else {
            state.uploadError = err.message || '未知错误';
        }
        state.isUploading = false;
        state.uploadStatus = 'error';
        currentAbortController = null;
        renderUploadArea();
        renderProgress('');
    }
}

// ============================================================
// 6. 页面导航
// ============================================================

/** 进入页面 2 */
function goToPage2() {
    if (state.orderData === null) return;
    state.currentPage = 2;
    renderPage();
    // 先渲染内容再触发动画
    renderExperienceUnderstanding();
    renderConversationPlan();
    renderRuntimePrompt();
    // 延迟一帧确保 DOM 更新后再启动动画
    requestAnimationFrame(() => { requestAnimationFrame(animatePage2); });
}

/** 重置所有状态 */
function resetAll() {
    // 取消进行中的请求
    if (currentAbortController) {
        currentAbortController.abort();
        currentAbortController = null;
    }

    Object.assign(state, {
        currentPage: 1,
        isUploading: false,
        uploadStatus: '',
        uploadError: '',
        orderData: null,
        dishKnowledge: null,
        realtimeInfo: null,
        experienceUnderstanding: null,
        conversationPlan: null,
        runtimePrompt: null,
    });

    // 恢复初始占位
    el.orderContent.innerHTML = TEMPLATES.order;
    el.dishContent.innerHTML = TEMPLATES.dish;
    el.realtimeContent.innerHTML = TEMPLATES.realtime;
    el.experienceList.innerHTML = TEMPLATES.experience;
    el.planContent.innerHTML = TEMPLATES.plan;
    el.promptCode.textContent = TEMPLATES.prompt;
    el.promptCode.classList.remove('text-gray-100');
    el.promptCode.classList.add('text-gray-400');
    el.promptMeta.textContent = '';
    el.progressText.textContent = '';

    renderPage();
    renderUploadArea();
    renderStepIndicator();
    el.nextBtn.disabled = true;
    el.resetBtn.disabled = true;
}

// ============================================================
// 7. 事件绑定
// ============================================================
function bindEvents() {

    // ---------- Drag & Drop ----------
    let dragCounter = 0;

    el.dropZone.addEventListener('dragenter', (e) => {
        e.preventDefault();
        e.stopPropagation();
        dragCounter++;
        if (state.isUploading) return;
        el.dropZone.classList.add('drag-over');
    });

    el.dropZone.addEventListener('dragover', (e) => {
        e.preventDefault();
        e.stopPropagation();
    });

    el.dropZone.addEventListener('dragleave', (e) => {
        e.preventDefault();
        e.stopPropagation();
        dragCounter--;
        if (dragCounter <= 0) {
            dragCounter = 0;
            el.dropZone.classList.remove('drag-over');
        }
    });

    el.dropZone.addEventListener('drop', (e) => {
        e.preventDefault();
        e.stopPropagation();
        dragCounter = 0;
        el.dropZone.classList.remove('drag-over');
        if (state.isUploading) return;
        const files = e.dataTransfer.files;
        if (files.length > 0) {
            el.fileInput.files = files;
            handleFileUpload(files[0]);
        }
    });

    // ---------- 文件选择 ----------
    el.dropZone.addEventListener('click', () => {
        if (state.isUploading) return;
        // 如果已上传完成，点击直接打开文件选择（便于重新上传）
        el.fileInput.click();
    });

    el.fileInput.addEventListener('change', () => {
        if (el.fileInput.files.length > 0) {
            handleFileUpload(el.fileInput.files[0]);
        }
    });

    // 上传按钮点击触发文件选择
    el.uploadBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        if (state.isUploading) return;
        if (state.uploadStatus === 'error') {
            // 错误状态下点击重试，直接打开文件选择
            el.fileInput.click();
            return;
        }
        el.fileInput.click();
    });

    // ---------- 页面导航按钮 ----------
    el.nextBtn.addEventListener('click', goToPage2);
    el.backBtn.addEventListener('click', () => {
        state.currentPage = 1;
        renderPage();
    });
    el.resetBtn.addEventListener('click', resetAll);

    // ---------- 文件输入变化后启用按钮 ----------
    el.fileInput.addEventListener('change', () => {
        const hasFile = el.fileInput.files.length > 0;
        // 如果上传状态为空且有文件，可以启用上传按钮（但上传按钮逻辑已统一在 handleFileUpload 中）
    });

    // ---------- 上传状态变化时控制按钮状态 ----------
    // 监听器：数据加载完成后启用重置按钮
    // 在上传成功流程中已处理
}

// ============================================================
// 8. XSS 防护 —— HTML 转义
// ============================================================
function esc(str) {
    if (str == null) return '';
    const div = document.createElement('div');
    div.appendChild(document.createTextNode(String(str)));
    return div.innerHTML;
}

// ============================================================
// 初始化
// ============================================================
document.addEventListener('DOMContentLoaded', () => {
    cacheDom();
    renderPage();
    renderUploadArea();
    renderStepIndicator();
    bindEvents();
});
