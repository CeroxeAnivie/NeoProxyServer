/**
 * chart.js — SVG 流量图表
 *
 * 纯 SVG 绘制，无第三方图表库依赖。
 * 支持 60s/300s/600s 时间范围切换。
 */

/**
 * 设置图表时间范围
 * @param {number} range 秒数: 60 / 300 / 600
 */
function setChartRange(range) {
    chartRange = range;
    document.querySelectorAll('.chart-btn').forEach(b => b.classList.remove('active'));
    const btn = document.querySelector(`.chart-btn[onclick="setChartRange(${range})"]`);
    if (btn) btn.classList.add('active');
    drawChart();
}

/**
 * 绘制流量图表 — 每次收到新数据或窗口大小变化时调用
 */
function drawChart() {
    const container = document.querySelector('.chart-container');
    const svg = document.getElementById('traffic-chart');
    const w = container.clientWidth;
    const h = container.clientHeight;
    svg.setAttribute('viewBox', `0 0 ${w} ${h}`);

    const now = Date.now();
    const startTime = now - (chartRange * 1000);
    const data = trafficHistory.filter(d => d.t >= startTime);

    // 绘制水平网格线
    const linesGroup = document.getElementById('chart-grid-lines');
    linesGroup.innerHTML = '';
    [0.25, 0.5, 0.75].forEach(p => {
        const line = document.createElementNS("http://www.w3.org/2000/svg", "line");
        line.setAttribute("x1", 0);
        line.setAttribute("y1", h * p);
        line.setAttribute("x2", w);
        line.setAttribute("y2", h * p);
        linesGroup.appendChild(line);
    });

    if (data.length < 2) return;

    // 计算最大值 (确保最小 1KB)
    let maxVal = 0;
    data.forEach(d => {
        if (d.v > maxVal) maxVal = d.v;
    });
    if (maxVal < 1024) maxVal = 1024;

    const yUnitStr = formatBytes(maxVal).split(' ')[1];

    // 绘制标签
    const labels = document.getElementById('chart-labels');
    labels.innerHTML = '';
    const addText = (x, y, txt, anchor = 'start') => {
        const t = document.createElementNS("http://www.w3.org/2000/svg", "text");
        t.setAttribute("x", x);
        t.setAttribute("y", y);
        t.setAttribute("class", "chart-text");
        t.setAttribute("text-anchor", anchor);
        t.textContent = txt;
        labels.appendChild(t);
    };

    addText(5, 15, formatBytes(maxVal) + "/s");
    addText(5, h / 2 + 5, formatBytes(maxVal / 2) + "/s");
    addText(5, h - 18, "0 " + yUnitStr + "/s");

    const dStart = new Date(startTime);
    const dEnd = new Date(now);
    const fmtTime = d => d.getHours().toString().padStart(2, '0') + ':' +
        d.getMinutes().toString().padStart(2, '0') + ':' +
        d.getSeconds().toString().padStart(2, '0');
    addText(5, h - 5, fmtTime(dStart));
    addText(w - 5, h - 5, fmtTime(dEnd), 'end');

    // 绘制折线路径
    let pathD = "";
    data.forEach((pt, i) => {
        const x = ((pt.t - startTime) / (chartRange * 1000)) * w;
        const y = h - ((pt.v / maxVal) * h);
        const clX = Math.max(0, Math.min(w, x));
        if (i === 0) pathD += `M ${clX} ${y} `;
        else pathD += `L ${clX} ${y} `;
    });

    if (pathD) {
        document.getElementById('chart-line').setAttribute('d', pathD);

        const lastX = ((data[data.length - 1].t - startTime) / (chartRange * 1000)) * w;
        const firstX = ((data[0].t - startTime) / (chartRange * 1000)) * w;
        const areaD = `M ${firstX} ${h} L ${firstX} ${h - ((data[0].v / maxVal) * h)} ${pathD.substring(1)} L ${lastX} ${h} Z`;
        document.getElementById('chart-area').setAttribute('d', areaD);
    }
}
