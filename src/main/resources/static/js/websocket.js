/**
 * websocket.js — WebSocket 通信 & 消息分发
 *
 * 必须在所有其他模块之后加载，因为 onmessage 处理器
 * 需要调用各模块定义的函数。
 */

function connectWs() {
    ws = new WebSocket(WS_URL);

    ws.onopen = function () {
        updateStatus(true);
        if (heartbeatInterval) clearInterval(heartbeatInterval);
        heartbeatInterval = setInterval(function () {
            if (ws.readyState === 1) ws.send("PING");
        }, 5000);
        startDashPolling();
        if (currentTab === "files") loadDir("");
        refreshCurrentTab();
    };

    ws.onclose = function (event) {
        updateStatus(false);
        if (event.code === 409 || event.code === 1008 || event.code === 4403 || event.code === 1006) {
            lockDown();
        } else {
            setTimeout(connectWs, 3000);
        }
    };

    ws.onmessage = function (e) {
        try {
            var msg = JSON.parse(e.data);

            if (msg.type === "log") {
                logRaw(msg.payload);
                if (isExpectingTable) checkAndParseTableData(msg.payload);
            } else if (msg.type === "logo") {
                logLogo(msg.payload);
            } else if (msg.type === "cmd_result") {
                handleTableData(msg.payload);
            } else if (msg.type === "dashboard_data") {
                updateDashboard(JSON.parse(msg.payload));
            } else if (msg.type === "action" && msg.payload === "refresh_clients") {
                refreshClients();
            } else if (msg.type === "perf_sys") {
                updatePerf(msg.payload);
            } else if (msg.type === "perf_ports") {
                updatePorts(msg.payload);
            } else if (msg.type === "file_list") {
                handleFileList(msg.payload, msg.path);
            } else if (msg.type === "file_content") {
                showEditorContent(msg.payload);
            } else if (msg.type === "file_too_large") {
                confirmDownload(msg.path);
            } else if (msg.type === "toast") {
                var text = msg.payload;
                if (text.startsWith("Deleted: ")) text = t("msg_deleted") + text.substring(9);
                else if (text.startsWith("Created: ")) text = t("msg_created") + text.substring(9);
                else if (text.startsWith("Moved: ")) text = text;
                showToast(text, "success");
            } else if (msg.type === "error") {
                showToast(msg.payload, "danger");
            } else if (msg.type === "action" && msg.payload === "refresh_files") {
                if (fileRequestTarget === "view") refreshFiles();
            }
        } catch (err) { /* 忽略非JSON消息 */
        }
    };
}

function updateStatus(connected) {
    var el = document.getElementById("status-display");
    var elM = document.getElementById("status-display-m");
    if (connected) {
        el.classList.remove("disconnected");
        el.classList.add("connected");
        el.innerHTML = '<i class="fas fa-link"></i> ' + t("connected");
        elM.className = "status-badge connected";
        elM.innerHTML = '<i class="fas fa-check"></i>';
    } else {
        el.classList.remove("connected");
        el.classList.add("disconnected");
        el.innerHTML = '<i class="fas fa-unlink"></i> ' + t("disconnected");
        elM.className = "status-badge disconnected";
        elM.innerHTML = '<i class="fas fa-times"></i>';
    }
}

function lockDown() {
    if (ws) ws.close();
    document.getElementById("error-overlay").style.display = "flex";
}

