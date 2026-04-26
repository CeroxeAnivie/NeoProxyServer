/**
 * terminal.js — 系统终端
 *
 * 日志渲染、命令发送、ASCII 表格解析。
 */

/**
 * 渲染原始日志文本到终端输出区域
 * 自动识别日志格式、高亮 IP 地址
 */
function logRaw(text) {
    const div = document.createElement('div');
    div.className = 'log-entry';

    if (text.startsWith('>')) {
        // 用户输入的命令
        div.className = 'log-cmd';
        div.textContent = text;
    } else if (text.startsWith('[')) {
        // 结构化日志: [time] [level] [source]: message
        const r = /^\[(.*?)\] \[(.*?)\] \[(.*?)\]: (.*)$/;
        const m = text.match(r);
        if (m) {
            const [_, t, l, s, c] = m;
            let h = `<span class="l-time">[${t}]</span> <span class="l-lvl ${l}">[${l}]</span> <span class="l-src">[${s}]</span>: `;
            // IPv4 和 IPv6 正则 — 用于高亮
            const ir = /((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)|([\da-fA-F]{1,4}:){7}[\da-fA-F]{1,4}|([\da-fA-F]{1,4}:){1,7}:|:((:[\da-fA-F]{1,4}){1,7}|:)/g;
            h += `<span class="l-text">${escapeHtml(c).replace(ir, m => `<span class="l-ip">${m}</span>`)}</span>`;
            div.innerHTML = h;
        } else {
            div.textContent = text;
        }
    } else {
        div.textContent = text;
    }

    termOutput.appendChild(div);
    requestAnimationFrame(() => termOutput.scrollTop = termOutput.scrollHeight);
}

/**
 * 渲染 ASCII Logo 到终端
 */
function logLogo(text) {
    const div = document.createElement('div');
    div.className = 'log-logo';
    div.textContent = text;
    termOutput.appendChild(div);
    requestAnimationFrame(() => termOutput.scrollTop = termOutput.scrollHeight);
}

/**
 * 发送终端命令
 */
function sendCmd() {
    const val = cmdInput.value.trim();
    if (val && ws.readyState === 1) {
        ws.send(val);
        cmdInput.value = '';
    }
}

/**
 * 检查日志流中是否包含 ASCII 表格数据
 * 在 isExpectingTable=true 时被调用
 */
function checkAndParseTableData(rawPayload) {
    let content = rawPayload;
    const match = content.match(/^\[.*?\] \[.*?\] \[.*?\]: ([\s\S]*)$/);
    if (match) content = match[1];

    // 检测 ASCII 表格特征字符
    if (content.includes("┌") || content.includes("│")) {
        handleTableData(content);
        isExpectingTable = false;
        document.getElementById(tableTarget + '-table-wrapper').innerHTML = "";
        handleTableData(content);
        return;
    }

    // 检测空列表关键词
    const emptyKeywords = [
        "No active HostClient", "没有活跃的 HostClient",
        "Ban list is empty", "封禁列表为空",
        "No data to display", "没有数据可显示"
    ];
    for (let key of emptyKeywords) {
        if (content.includes(key)) {
            renderEmptyTable(tableTarget);
            isExpectingTable = false;
            return;
        }
    }
}

/**
 * 渲染空表格提示
 */
function renderEmptyTable(type) {
    const div = document.getElementById(type + '-table-wrapper');
    if (div) div.innerHTML = `<div style="padding:2rem;text-align:center;color:var(--text-sub)"><span data-i18n="no_data">${t('no_data')}</span></div>`;
}

/**
 * 处理 ASCII 表格数据并路由到对应的渲染函数
 */
function handleTableData(data) {
    const lines = data.split('\n');
    let cl = [];
    let fs = false;
    for (let l of lines) {
        if (!fs && (l.includes('┌') || l.includes('|') || l.includes('│'))) {
            const s = Math.max(l.indexOf('┌'), Math.max(l.indexOf('|'), l.indexOf('│')));
            if (s !== -1) {
                fs = true;
                cl.push(l.substring(s));
                continue;
            }
        }
        if (fs) cl.push(l);
    }

    const parsed = parseAsciiTable(cl.join('\n'));
    if (tableTarget === 'key') renderKeyTable(parsed);
    else if (tableTarget === 'client') renderClientTable(parsed);
    else if (tableTarget === 'ban') renderBanTable(parsed);
}

/**
 * 解析后端 CLI 输出的 ASCII 表格为结构化数据
 * @returns {{ headers: string[], rows: string[][] } | null}
 */
function parseAsciiTable(t) {
    const lines = t.split('\n').filter(l =>
        (l.includes('│') || l.includes('|')) &&
        !l.includes('┌') && !l.includes('├') &&
        !l.includes('└') && !l.includes('─')
    );

    if (lines.length < 2) return null;

    const split = l => {
        let p = l.split(/[│|]/);
        if (p.length > 0 && p[0].trim() === '') p.shift();
        if (p.length > 0 && p[p.length - 1].trim() === '') p.pop();
        return p.map(s => s.trim());
    };

    const headers = split(lines[0]);
    const rows = [];
    let last = null;

    for (let i = 1; i < lines.length; i++) {
        const c = split(lines[i]);
        if (c.length === headers.length) {
            if (c.every(s => s === '')) continue;
            if (c[0] === '' && last) {
                // 续行 — 合并到上一行
                for (let j = 0; j < c.length; j++) if (c[j]) last[j] += '<br>' + c[j];
            } else {
                last = c;
                rows.push(last);
            }
        }
    }

    return {headers, rows};
}
