/**
 * app.js — 全局状态、常量、工具函数
 *
 * 作为第一个加载的 JS 模块，定义所有全局状态和通用工具函数，
 * 供后续模块直接引用。所有函数和变量挂载在 window 上，
 * 确保各模块间可自由调用（与原单文件行为完全一致）。
 */

/* ========== 连接常量 ========== */
const PROTOCOL = window.location.protocol === 'https:' ? 'wss' : 'ws';
const HOST = window.location.host;
const TOKEN = new URLSearchParams(window.location.search).get('token') || '';
const WS_URL = `${PROTOCOL}://${HOST}/ws?token=${TOKEN}`;

/* ========== 全局状态 ========== */
let ws;                        // WebSocket 实例
let curPath = "";              // 文件管理器当前路径
let moveModalPath = "";        // 移动弹窗当前路径
let createModeDir = false;     // 创建模式: true=文件夹, false=文件
let curLang = 'zh';            // 当前语言
let currentTab = 'dashboard';  // 当前激活的 Tab
let isEditMode = false;        // 密钥编辑模式
let originalKeyName = "";      // 编辑中的原始密钥名
let isExpectingTable = false;  // 是否正在等待表格数据
let tableTarget = '';          // 表格数据目标: key / client / ban
let chartRange = 60;           // 流量图时间范围 (秒)
const trafficHistory = [];     // 流量历史数据
let heartbeatInterval;         // 心跳定时器
let dashInterval;              // 仪表盘轮询定时器
let rawPortData = {tcp: [], udp: []};  // 端口原始数据
let selectedFiles = new Set();             // 文件多选集合
let lastKeyData = null;        // 缓存的密钥表格数据
let lastClientData = null;     // 缓存的客户端表格数据
let lastBanData = null;        // 缓存的封禁表格数据
let fileRequestTarget = 'view'; // 文件请求目标: view / modal
let clipboardAction = null;    // 剪贴板操作: copy / cut
let clipboardFiles = new Set(); // 剪贴板文件集合
let currentFileList = [];      // 当前文件列表数据

/* ========== DOM 引用 ========== */
const termOutput = document.getElementById('term-output');
const cmdInput = document.getElementById('cmd-input');
const banInput = document.getElementById('ban-ip-input');

/* ========== 工具函数 ========== */

/**
 * HTML 实体转义 — 防止 XSS
 */
function escapeHtml(text) {
    if (!text) return text;
    return text
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

/**
 * 显示 Toast 通知
 * @param {string} msg  消息内容
 * @param {string} type 类型: success / danger
 */
function showToast(msg, type) {
    const d = document.createElement('div');
    d.className = `toast ${type}`;
    d.innerHTML = `<i class="fas fa-info-circle"></i> ${msg}`;
    document.getElementById('toast-container').appendChild(d);
    setTimeout(() => d.remove(), 3000);
}

/**
 * 国际化翻译 — 根据当前语言返回对应文本
 * @param {string} k 翻译键
 * @returns {string} 翻译后的文本，找不到则返回键名本身
 */
function t(k) {
    return (LANG[curLang] && LANG[curLang][k]) || k;
}

/**
 * 格式化字节数 (带单位自动选择)
 * @param {number} bytes     字节数
 * @param {number} decimals  小数位数
 * @returns {string} 如 "1.5 GB"
 */
function formatBytes(bytes, decimals = 2) {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(decimals < 0 ? 0 : decimals))
        + ' ' + ['B', 'KB', 'MB', 'GB', 'TB'][i];
}

/**
 * 轻量级字节格式化 — 仅 B/KB/MB 三档
 * @param {number} s 字节数
 * @returns {string} 如 "1.5MB"
 */
function fmtSize(s) {
    if (s < 1024) return s + "B";
    if (s < 1048576) return (s / 1024).toFixed(1) + "KB";
    return (s / 1048576).toFixed(1) + "MB";
}

/**
 * Tab 切换
 */
function switchTab(id) {
    currentTab = id;
    document.querySelectorAll('.view').forEach(e => e.classList.remove('active'));
    document.getElementById('view-' + id).classList.add('active');
    document.querySelectorAll('.nav-btn, .b-nav-btn').forEach(b => b.classList.remove('active'));
    const sel = `.nav-btn[onclick*="${id}"], .b-nav-btn[onclick*="${id}"]`;
    document.querySelectorAll(sel).forEach(b => b.classList.add('active'));

    const fab = document.querySelector('.upload-fab');
    if (fab) fab.style.display = (id === 'files') ? 'flex' : 'none';
    if (id !== 'files') document.getElementById('upload-panel').classList.remove('show');

    if (ws && ws.readyState === 1) {
        refreshCurrentTab();
        if (id === 'dashboard') requestAnimationFrame(drawChart);
        if (id === 'files') refreshFiles();
    }
}

/**
 * 刷新当前 Tab 的数据
 */
function refreshCurrentTab() {
    if (currentTab === 'keys') refreshKeys();
    else if (currentTab === 'clients') refreshClients();
    else if (currentTab === 'bans') refreshBans();
}

/**
 * 启动仪表盘轮询
 */
function startDashPolling() {
    if (dashInterval) clearInterval(dashInterval);
    dashInterval = setInterval(() => {
        if (ws && ws.readyState === 1) {
            ws.send("#GET_DASHBOARD");
            if (document.getElementById('view-performance').classList.contains('active')) {
                ws.send("#GET_PERFORMANCE");
                ws.send("#GET_PORTS");
            }
        }
    }, 500);
}

/**
 * 初始化 — 在所有模块加载完成后由 index.html 尾部调用
 */
function init() {
    const savedTheme = localStorage.getItem('theme') || 'system';
    applyTheme(savedTheme);
    systemThemeMedia.addListener((e) => {
        if (localStorage.getItem('theme') === 'system') {
            applyTheme('system');
        }
    });

    setLang(navigator.language.startsWith('zh') ? 'zh' : 'en');
    connectWs();

    const dz = document.getElementById('drop-zone');
    if (dz) {
        dz.ondragover = e => {
            e.preventDefault();
            dz.classList.add('dragover');
        };
        dz.ondragleave = () => dz.classList.remove('dragover');
        dz.ondrop = e => {
            e.preventDefault();
            dz.classList.remove('dragover');
            handleDrop(e);
        };
    }

    if (typeof initDatePicker === 'function') initDatePicker();

    window.addEventListener('resize', () => {
        requestAnimationFrame(drawChart);
    });
    cmdInput.addEventListener('keydown', e => {
        if (e.key === 'Enter') sendCmd();
    });
    banInput.addEventListener('keydown', e => {
        if (e.key === 'Enter') doBanIp();
    });
    document.addEventListener('click', e => {
        document.getElementById('context-menu').style.display = 'none';
    });
    document.getElementById('file-area-root').addEventListener('contextmenu', e => {
        if (e.target.closest('.file-item')) return;
        e.preventDefault();
        showContextMenu(e, null);
    });
}
