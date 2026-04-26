/* bans.js */

function doBanIp() {
    var ip = banInput.value.trim();
    if (ip) {
        reqBanIp(ip);
        banInput.value = "";
    }
}

async function reqBanIp(ip) {
    if (await openConfirmModal(t("confirm_ban") + " (" + ip + ")")) {
        ws.send("ban " + ip);
        setTimeout(refreshBans, 500);
    }
}

function refreshBans() {
    isExpectingTable = true;
    tableTarget = "ban";
    document.getElementById("ban-table-wrapper").innerHTML =
        '<div style="padding:2rem;text-align:center;color:var(--text-sub)">' +
        '<i class="fas fa-circle-notch fa-spin"></i> ' +
        '<span data-i18n="loading">' + t("loading") + "</span></div>";
    ws.send("listbans");
}

function renderBanTable(data) {
    lastBanData = data;
    var div = document.getElementById("ban-table-wrapper");
    if (!data) return renderEmptyTable("ban");

    var h = '<table><thead><tr>' +
        "<th>" + t("th_ip") + "</th>" +
        "<th>" + t("th_loc") + "</th>" +
        "<th>" + t("th_isp") + "</th>" +
        "<th>" + t("th_op") + "</th>" +
        "</tr></thead><tbody>";

    data.rows.forEach(function (row) {
        var ip = row[0];
        if (!ip) return;
        var loc = row[1] || t("unknown");
        var isp = row[2] || "-";
        // 【安全修复】所有动态数据必须转义防止 XSS
        var escIp = escapeHtml(ip);
        var escLoc = escapeHtml(loc);
        var escIsp = escapeHtml(isp);
        h += "<tr>" +
            '<td><span class="l-ip">' + escIp + "</span></td>" +
            "<td>" + escLoc + "</td>" +
            "<td>" + escIsp + "</td>" +
            '<td><button class="btn btn-action" onclick="ws.send(&apos;unban ' + escIp + "&apos;);setTimeout(refreshBans,500)">" +
            '<i class="fas fa-unlock"></i> ' + t("act_unban") + "</button></td>" +
            "</tr>";
    });

    div.innerHTML = h + "</tbody></table>";
}
