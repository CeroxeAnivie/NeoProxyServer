/**
 * modals.js — 弹窗管理
 *
 * 包含确认弹窗、冲突弹窗、输入弹窗的打开/关闭逻辑。
 * 所有弹窗基于 Promise 模式，支持 async/await 调用。
 */

let confirmResolver = null;
let conflictResolver = null;
let inputResolver = null;

/**
 * 打开确认弹窗
 * @param {string} msg 确认消息
 * @returns {Promise<boolean>} 用户选择: true=确认, false=取消
 */
function openConfirmModal(msg) {
    return new Promise(resolve => {
        confirmResolver = resolve;
        document.getElementById('confirm-msg').innerText = msg;
        document.getElementById('confirm-modal').style.display = 'flex';
        document.getElementById('confirm-modal').classList.add('show');
    });
}

/**
 * 响应确认弹窗
 */
function resolveConfirm(result) {
    document.getElementById('confirm-modal').classList.remove('show');
    setTimeout(() => document.getElementById('confirm-modal').style.display = 'none', 300);
    if (confirmResolver) confirmResolver(result);
}

/**
 * 打开文件冲突弹窗
 * @param {string} filename 冲突文件名
 * @returns {Promise<string>} 用户选择: 'cancel' | 'rename' | 'overwrite'
 */
function openConflictModal(filename) {
    return new Promise(resolve => {
        conflictResolver = resolve;
        document.getElementById('conflict-msg').innerText = `${t('conflict_msg')} "${filename}"`;
        document.getElementById('conflict-modal').style.display = 'flex';
        document.getElementById('conflict-modal').classList.add('show');
    });
}

/**
 * 响应冲突弹窗
 */
function resolveConflict(action) {
    document.getElementById('conflict-modal').classList.remove('show');
    setTimeout(() => document.getElementById('conflict-modal').style.display = 'none', 300);
    if (conflictResolver) conflictResolver(action);
}

/**
 * 打开输入弹窗
 * @param {string} title        弹窗标题
 * @param {string} placeholder  输入框占位符
 * @param {string} defaultValue 默认值
 * @returns {Promise<string|null>} 用户输入的值，取消返回 null
 */
function openInputModal(title, placeholder, defaultValue = "") {
    return new Promise(resolve => {
        inputResolver = resolve;
        document.getElementById('input-title').innerText = title;
        document.getElementById('input-value').placeholder = placeholder;
        document.getElementById('input-value').value = defaultValue;
        document.getElementById('input-modal').style.display = 'flex';
        document.getElementById('input-modal').classList.add('show');
        const input = document.getElementById('input-value');
        input.focus();
        if (defaultValue) {
            input.value = defaultValue;
            const dot = defaultValue.lastIndexOf('.');
            if (dot > 0) input.setSelectionRange(0, dot);
            else input.select();
        }
    });
}

/**
 * 响应输入弹窗
 */
function resolveInput(val) {
    document.getElementById('input-modal').classList.remove('show');
    setTimeout(() => document.getElementById('input-modal').style.display = 'none', 300);
    if (inputResolver) inputResolver(val);
}
