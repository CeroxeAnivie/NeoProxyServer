/**
 * clients.js — 在线客户端管理
 */

function refreshClients() {
    isExpectingTable = true;
    tableTarget = 'client';
    document.getElementById('client-table-wrapper').innerHTML =
        '<div style="padding:2rem;text-align:center;color:var(--text-sub)">' +
        '<i class="fas fa-circle-notch fa-spin"></i> ' +
        '<span data-i18n="loading">' + t('loading') + '</span></div>';
    ws.send('list');
}

function renderClientTable(data) {
    lastClientData = data;
    var div = document.getElementById('client-table-wrapper');
    if (!data) return renderEmptyTable('client');

    var h = '<table><thead><tr>' +
        '<th>IP</th>' +
        '<th>' + t('th_name') + '</th>' +
        '<th>' + t('th_loc') + '</th>' +
        '<th>' + t('th_isp') + '</th>' +
        '<th>' + t('th_port') + '</th>' +
        '<th>' + t('th_ext') + '</th>' +
        '<th>' + t('th_op') + '</th>' +
        '</tr></thead><tbody>';

    data.rows.forEach(function (row) {
        if (row.length >= 4) {
            var ip = row[0];
            if (!ip) return;
            var loc = row[2];
            if (loc === '' || loc === 'null') loc = t('unknown');
            // 【安全修复】所有动态数据必须转义防止 XSS
            var escIp = escapeHtml(ip);
            var escName = escapeHtml(row[1]);
            var escLoc = escapeHtml(loc);
            var escIsp = escapeHtml(row[3]);
            var escPort = escapeHtml(row[4] || '-');
            var escExt = escapeHtml(row[5] || '0');
            var locHtml = '<div style="display:flex; align-items:center; gap:8px;">' +
                '<span>' + escLoc + '</span>' +
                '<button class="btn btn-action btn-sm" onclick="ws.send(\'#REFRESH_LOC:' + escIp + '\">' +
                '<i class="fas fa-sync-alt"></i></button></div>';
            h += '<tr>' +
                '<td>' + escIp + '</td>' +
                '<td>' + escName + '</td>' +
                '<td>' + locHtml + '</td>' +
                '<td>' + escIsp + '</td>' +
                '<td>' + escPort + '</td>' +
                '<td>' + escExt + '</td>' +
                '<td><button class="btn btn-danger" onclick="reqBanIp(\'' + escIp + '\')">' +
                '<i class="fas fa-ban"></i> ' + t('act_ban') + '</button></td>' +
                '</tr>';
        }
    });

    div.innerHTML = h + '</tbody></table>';
}
