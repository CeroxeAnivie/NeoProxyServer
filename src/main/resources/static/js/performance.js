/**
 * performance.js — 性能监控视图
 *
 * CPU/内存/磁盘进度条 + TCP/UDP 端口列表。
 */

/**
 * 更新系统性能数据
 * @param {object} data 服务端推送的性能数据
 */
function updatePerf(data) {
    setBar('bar-cpu-sys', data.cpu.sys * 100);
    setBar('bar-cpu-proc', data.cpu.proc * 100);
    document.getElementById('perf-cpu-val').innerText =
        (data.cpu.sys * 100).toFixed(1) + '% (Proc: ' + (data.cpu.proc * 100).toFixed(1) + '%)';
    document.getElementById('cpu-model').innerText =
        data.cpu.cores + " " + t('p_cores') + " | " + data.cpu.model;

    const usedGB = (data.mem.used / 1073741824).toFixed(1);
    const totalGB = (data.mem.total / 1073741824).toFixed(1);
    setBar('bar-mem-sys', (data.mem.used / data.mem.total) * 100);
    setBar('bar-mem-jvm', (data.mem.jvm / data.mem.total) * 100);
    document.getElementById('perf-mem-val').innerText = usedGB + "/" + totalGB + " GB";
    document.getElementById('mem-detail').innerText =
        t('p_used_jvm') + (data.mem.jvm / 1048576).toFixed(0) + " MB";

    setBar('bar-disk', (data.disk.used / data.disk.total) * 100);
    document.getElementById('perf-disk-val').innerText =
        (data.disk.used / 1073741824).toFixed(0) + "GB " + t('p_used');
    document.getElementById('os-info').innerText = data.os;
}

/**
 * 设置进度条宽度 (安全钳位到 0-100)
 */
function setBar(id, val) {
    const el = document.getElementById(id);
    if (el) el.style.width = Math.min(100, Math.max(0, val)) + '%';
}

/**
 * 更新端口占用数据
 * @param {object} data { tcp: [...], udp: [...] }
 */
function updatePorts(data) {
    rawPortData = data;
    filterPorts('tcp');
    filterPorts('udp');
}

/**
 * 筛选并渲染端口列表
 * @param {string} type 'tcp' | 'udp'
 */
function filterPorts(type) {
    const searchInput = document.getElementById(`search-${type}`);
    const query = searchInput ? searchInput.value.toLowerCase() : "";
    const listContainer = document.getElementById(`${type}-list`);
    if (!listContainer) return;

    const rawList = rawPortData[type] || [];
    const filtered = rawList.filter(p => p.toLowerCase().includes(query));

    let html = "";
    if (filtered.length === 0) {
        html = `<div style="text-align:center;color:var(--text-sub);padding:10px;font-size:0.8rem;">
            <span data-i18n="no_data">${t('no_data')}</span></div>`;
    } else {
        const color = type === 'tcp' ? 'var(--accent)' : 'var(--warning)';
        const label = type.toUpperCase();
        html = filtered.map(p =>
            `<div class="port-card">
                <span class="port-addr">${escapeHtml(p)}</span>
                <span class="port-tag" style="color:${color};border-color:${color}">${label}</span>
            </div>`
        ).join('');
    }

    if (listContainer.innerHTML !== html) listContainer.innerHTML = html;
}
