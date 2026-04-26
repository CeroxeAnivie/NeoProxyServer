/**
 * dashboard.js — 仪表盘视图
 *
 * 更新仪表盘数据卡片和流量历史记录。
 */

/**
 * 更新仪表盘数据
 * @param {object} data 服务端推送的仪表盘数据
 */
function updateDashboard(data) {
    document.getElementById('d-hc').innerText = data.hc;
    document.getElementById('d-ec').innerText = data.ec + (data.uc || 0);
    document.getElementById('d-ver').innerText = data.v;
    document.getElementById('d-sver').innerText = data.sv;
    document.getElementById('d-ports').innerText = data.p;

    const now = Date.now();
    let speed = data.gs || 0;

    trafficHistory.push({t: now, v: speed});

    // 保留最近 11 分钟的数据
    const cutoff = now - (11 * 60 * 1000);
    while (trafficHistory.length > 0 && trafficHistory[0].t < cutoff) {
        trafficHistory.shift();
    }

    document.getElementById('d-speed').innerText = formatBytes(speed) + "/s";
    drawChart();
}
