/**
 * keys.js — 密钥管理
 *
 * 密钥的创建、编辑、删除、启用/禁用、表格渲染。
 */

/**
 * 提交密钥表单 (创建或编辑)
 */
function submitKeyForm() {
    const name = document.getElementById('k-name').value.trim();
    const bal = document.getElementById('k-bal').value;
    const time = document.getElementById('k-time').value;
    const port = document.getElementById('k-port').value;
    const rate = document.getElementById('k-rate').value;
    const web = document.getElementById('k-web').checked;

    if (!name) return showToast(t('msg_name_req'), "danger");

    if (isEditMode && name !== originalKeyName) {
        // 编辑模式且名称变更: 先删旧再建新
        ws.send(`key del ${originalKeyName}`);
        ws.send(`key add ${name} ${bal} ${time} ${port} ${rate} ${web}`);
    } else if (isEditMode) {
        // 编辑模式名称不变: 使用 set 命令
        ws.send(`key set ${name} b=${bal} r=${rate} p=${port} t=${time} w=${web}`);
    } else {
        // 创建模式
        ws.send(`key add ${name} ${bal} ${time} ${port} ${rate} ${web}`);
    }

    if (isEditMode) exitEditMode();
    else {
        document.getElementById('k-name').value = '';
        document.getElementById('k-bal').value = '1024';
        initDatePicker();
    }

    setTimeout(refreshKeys, 500);
}

/**
 * 进入密钥编辑模式
 */
function editKey(name, bal, time, port, rate, web) {
    isEditMode = true;
    originalKeyName = name;
    document.getElementById('key-form-title').innerText = t('key_edit_title');
    document.getElementById('btn-submit-key').innerText = t('btn_save');
    document.getElementById('btn-cancel-edit').style.display = 'inline-flex';
    document.getElementById('k-name').value = name;
    document.getElementById('k-bal').value = bal;
    document.getElementById('k-time').value = time;
    document.getElementById('k-port').value = port;
    document.getElementById('k-rate').value = rate;
    document.getElementById('k-web').checked = web;
    document.getElementById('k-name').focus();
}

/**
 * 退出密钥编辑模式
 */
function exitEditMode() {
    isEditMode = false;
    originalKeyName = "";
    document.getElementById('key-form-title').innerText = t('key_create_title');
    document.getElementById('btn-submit-key').innerText = t('btn_create');
    document.getElementById('btn-cancel-edit').style.display = 'none';
    document.getElementById('k-name').value = '';
}

/**
 * 请求删除密钥
 */
async function reqDeleteKey(name) {
    if (await openConfirmModal(t('confirm_del') + ` (${name})`)) {
        ws.send('key del ' + name);
        setTimeout(refreshKeys, 500);
    }
}

/**
 * 刷新密钥列表
 */
function refreshKeys() {
    isExpectingTable = true;
    tableTarget = 'key';
    document.getElementById('key-table-wrapper').innerHTML =
        `<div style="padding:2rem;text-align:center;color:var(--text-sub)">
            <i class="fas fa-circle-notch fa-spin"></i>
            <span data-i18n="loading">${t('loading')}</span>
        </div>`;
    ws.send('key list');
}

/**
 * 初始化日期选择器 — 默认过期时间为 30 天后
 */
function initDatePicker() {
    const d = new Date();
    d.setDate(d.getDate() + 30);
    const p = n => String(n).padStart(2, '0');
    const el = document.getElementById('k-time');
    if (el) el.value = `${d.getFullYear()}/${p(d.getMonth() + 1)}/${p(d.getDate())}-23:59`;
}

/**
 * 渲染密钥表格
 * @param {{ headers: string[], rows: string[][] }} data 解析后的表格数据
 */
function renderKeyTable(data) {
    lastKeyData = data;
    const div = document.getElementById('key-table-wrapper');
    if (!data) return renderEmptyTable('key');

    let h = '<table><thead><tr>';
    const hKeys = ['th_name', 'th_bal', 'th_time', 'th_port', 'th_rate', 'st_enable'];
    hKeys.forEach(k => h += `<th>${t(k)}</th>`);
    h += `<th>WebHTML</th>`;
    h += `<th>${t('th_op')}</th></tr></thead><tbody>`;

    data.rows.forEach(row => {
        if (row.length > 0) {
            let name = row[0];
            if (!name) return;
            const isRemote = name.includes('(R)');
            const cleanName = name.replace(/ \([RL]\)$/, '');
            const isEn = row[5].includes('✓') || row[5].includes('true');
            const isWeb = row[6].includes('✓') || row[6].includes('true');
            const rate = row[4].replace('mbps', '');
            
            // 【安全修复】所有动态数据必须转义防止 XSS
            const escName = escapeHtml(name);
            const escCleanName = escapeHtml(cleanName);
            const escBalance = escapeHtml(row[1]);
            const escTime = escapeHtml(row[2]);
            const escPort = escapeHtml(row[3]);
            const escRate = escapeHtml(rate);

            h += '<tr>';
            row.forEach((c, i) => {
                if (i === 1) c = escBalance + ' MiB';
                else if (i === 2) c = escTime;
                else if (i === 3) c = escPort;
                else if (i === 4) c = escRate + 'mbps';
                else if (i === 0) c = escName;
                if (i === 5) c = isEn
                    ? `<span style="color:var(--success)">● ${t('st_enable')}</span>`
                    : `<span style="color:var(--text-sub)">○ ${t('st_disable')}</span>`;
                if (i === 6) c = isWeb
                    ? `<span style="color:var(--accent)">● ${t('st_on')}</span>`
                    : `<span style="color:var(--text-sub)">○ ${t('st_off')}</span>`;
                if (i !== 7) h += `<td>${c}</td>`;
            });

            if (isRemote) {
                h += `<td><button class="btn btn-action" disabled
                    style="opacity:0.6;cursor:not-allowed;border-style:dashed"
                    title="${t('tip_remote_key')}"><i class="fas fa-lock"></i></button></td>`;
            } else {
                // 【安全修复】editKey 参数需要转义
                const editArgs = `'${escCleanName}','${escBalance}','${escTime}','${escPort}','${escRate}',${isWeb}`;
                h += `<td>
                    <button class="btn btn-action" title="${t('act_edit')}" onclick="editKey(${editArgs})">
                        <i class="fas fa-edit"></i>
                    </button>
                    <button class="btn btn-action" style="width:80px"
                        onclick="ws.send('key ${isEn ? 'disable' : 'enable'} ${escCleanName}');setTimeout(refreshKeys,500)">
                        ${isEn ? t('st_disable') : t('st_enable')}
                    </button>
                    <button class="btn btn-danger" title="${t('act_del')}" style="min-width:40px"
                        onclick="reqDeleteKey('${escCleanName}')">
                        <i class="fas fa-trash"></i>
                    </button>
                </td>`;
            }
            h += '</tr>';
        }
    });

    div.innerHTML = h + '</tbody></table>';
}
