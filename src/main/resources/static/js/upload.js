/* upload.js — 上传队列管理: 文件选择、拖拽上传、并发控制、进度追踪 */

var uploadQueue = [];
var activeUploads = 0;
var MAX_CONCURRENT = 3;
var totalFiles = 0;
var finishedFiles = 0;
var uploadControllers = new Map();

function toggleUploadPanel() {
    document.getElementById("upload-panel").classList.toggle("show");
}

function updateHeader() {
    var txt = document.getElementById("up-title-text");
    if (txt) txt.innerText = t("up_stats") + " " + finishedFiles + " / " + (totalFiles - finishedFiles);

    var circle = document.querySelector(".fab-circle");
    if (circle) {
        var r = 28;
        var c = 2 * Math.PI * r;
        var pct = totalFiles > 0 ? finishedFiles / totalFiles : 0;
        circle.style.strokeDashoffset = c * (1 - pct);
    }
}

function addUploadItem(id, name, size) {
    var list = document.getElementById("up-list");
    var d = document.createElement("div");
    d.className = "up-item";
    d.id = "up-item-" + id;
    d.innerHTML =
        '<div class="up-row-1"><span><i class="fas fa-file"></i> ' + name + '</span><span id="up-pct-' + id + '">0%</span></div>' +
        '<div class="up-row-2"><div class="up-bar" id="up-bar-' + id + '" style="width:0%"></div></div>' +
        '<div class="up-meta" id="up-meta-' + id + '">' +
        '<span id="up-stat-' + id + '">' + t("up_wait") + '</span>' +
        '<span id="up-size-' + id + '">' + fmtSize(size) +
        ' <i class="fas fa-times up-cancel" onclick="abortUpload(\'' + id + "')\"></i></span></div>";
    list.appendChild(d);
    return d;
}

function markItemDone(id) {
    var item = document.getElementById("up-item-" + id);
    if (item) {
        item.classList.add("done");
        var list = document.getElementById("up-list");
        list.insertBefore(item, list.firstChild);
        var cancelBtn = item.querySelector(".up-cancel");
        if (cancelBtn) cancelBtn.remove();
    }
}

function updateItemProgress(id, pct, status, speed, uploaded, total) {
    var bar = document.getElementById("up-bar-" + id);
    var pEl = document.getElementById("up-pct-" + id);
    var sEl = document.getElementById("up-stat-" + id);
    var szEl = document.getElementById("up-size-" + id);
    if (bar) bar.style.width = pct + "%";
    if (pEl) pEl.innerText = pct.toFixed(1) + "%";
    if (sEl) {
        sEl.innerText = status;
        if (speed) sEl.innerText += " (" + speed + ")";
    }
    if (szEl && uploaded && total) szEl.innerText = uploaded + " / " + total;
}

function uploadNext() {
    if (uploadQueue.length === 0) return;
    while (activeUploads < MAX_CONCURRENT && uploadQueue.length > 0) {
        var task = uploadQueue.shift();
        processUpload(task);
    }
}

async function processUpload(task) {
    var file = task.file, id = task.id, relPath = task.relPath;
    activeUploads++;
    var token = new URLSearchParams(location.search).get("token");
    var baseUrl = window.location.origin;
    var finalName = file.name;

    try {
        var uploadPath = curPath;
        if (relPath) {
            var parts = relPath.split("/");
            parts.pop();
            if (parts.length > 0) {
                var sub = parts.join("/");
                uploadPath = curPath ? (curPath + "/" + sub) : sub;
            }
        }

        // 检查文件是否已存在
        var checkOk = false;
        try {
            var checkUrl = new URL(baseUrl + "/check_exists");
            checkUrl.searchParams.append("path", uploadPath);
            checkUrl.searchParams.append("filename", finalName);
            if (token) checkUrl.searchParams.append("token", token);
            var controller = new AbortController();
            var timeoutId = setTimeout(function () {
                controller.abort();
            }, 3000);
            var checkRes = await fetch(checkUrl, {signal: controller.signal});
            clearTimeout(timeoutId);
            if (checkRes.ok && (await checkRes.text()) === "true") checkOk = true;
        } catch (e) { /* 超时或网络错误则跳过检查 */
        }

        if (checkOk) {
            var action = await openConflictModal(finalName);
            if (action === "cancel") {
                activeUploads--;
                updateItemProgress(id, 0, t("err_cancel"));
                finishedFiles++;
                updateHeader();
                uploadNext();
                return;
            }
            if (action === "rename") finalName = Date.now() + "_" + finalName;
        }

        var url = new URL(baseUrl + "/upload");
        url.searchParams.append("path", uploadPath);
        url.searchParams.append("filename", finalName);
        if (token) url.searchParams.append("token", token);

        await uploadXHR(file, url.toString(), id);
        finishedFiles++;
        updateHeader();
        markItemDone(id);
    } catch (e) {
        if (e.name === "AbortError") updateItemProgress(id, 0, t("err_cancel"));
        else updateItemProgress(id, 0, t("up_fail"));
    } finally {
        activeUploads--;
        uploadControllers.delete(id);
        uploadNext();
    }
}

function uploadXHR(file, url, id) {
    return new Promise(function (resolve, reject) {
        var xhr = new XMLHttpRequest();
        uploadControllers.set(id, xhr);
        var lastLoaded = 0;
        var lastTime = Date.now();
        xhr.open("POST", url, true);

        xhr.upload.onprogress = function (e) {
            if (e.lengthComputable) {
                var now = Date.now();
                var dt = (now - lastTime) / 1000;
                if (dt >= 0.5) {
                    var speed = (e.loaded - lastLoaded) / dt;
                    updateItemProgress(id, (e.loaded / e.total) * 100,
                        t("toast_uploading"), fmtSize(speed) + "/s",
                        fmtSize(e.loaded), fmtSize(e.total));
                    lastLoaded = e.loaded;
                    lastTime = now;
                }
            }
        };

        xhr.onload = function () {
            if (xhr.status >= 200 && xhr.status < 300) {
                updateItemProgress(id, 100, t("up_complete"));
                resolve();
            } else reject(new Error("HTTP " + xhr.status));
        };
        xhr.onerror = function () {
            reject(new Error("Network Error"));
        };
        xhr.onabort = function () {
            reject(new Error("AbortError"));
        };
        xhr.send(file);
    });
}

function abortUpload(id) {
    var xhr = uploadControllers.get(id);
    if (xhr) {
        xhr.abort();
        uploadControllers.delete(id);
        var item = document.getElementById("up-item-" + id);
        if (item) item.querySelector(".up-row-1 span").style.textDecoration = "line-through";
    } else {
        var item = document.getElementById("up-item-" + id);
        if (item) item.remove();
    }
}

async function handleDrop(e) {
    var items = e.dataTransfer.items;
    if (!items) return;
    e.preventDefault();
    var entries = [];
    for (var i = 0; i < items.length; i++) {
        var entry = items[i].webkitGetAsEntry ? items[i].webkitGetAsEntry() : null;
        if (entry) entries.push(entry);
    }
    if (entries.length > 0) {
        document.getElementById("upload-panel").classList.add("show");
        for (var j = 0; j < entries.length; j++) {
            await traverseFileTree(entries[j]);
        }
    }
    uploadNext();
}

async function traverseFileTree(item, path) {
    path = path || "";
    if (item.isFile) {
        item.file(function (file) {
            var id = Date.now() + "_" + Math.random().toString(36).substr(2, 9);
            var fullPath = path + file.name;
            addUploadItem(id, fullPath, file.size);
            uploadQueue.push({file: file, id: id, relPath: fullPath});
            totalFiles++;
            updateHeader();
            uploadNext();
        });
    } else if (item.isDirectory) {
        var dirReader = item.createReader();
        var readEntries = async function () {
            var entries = await new Promise(function (resolve) {
                dirReader.readEntries(resolve);
            });
            if (entries.length > 0) {
                for (var k = 0; k < entries.length; k++) {
                    await traverseFileTree(entries[k], path + item.name + "/");
                }
                await readEntries();
            }
        };
        await readEntries();
    }
}

function handleUpload(files) {
    if (!files || files.length === 0) return;
    document.getElementById("upload-panel").classList.add("show");
    var arr = Array.from(files);
    arr.forEach(function (f) {
        var id = Date.now() + "_" + Math.random().toString(36).substr(2, 9);
        addUploadItem(id, f.name, f.size);
        uploadQueue.push({file: f, id: id, relPath: f.name});
        totalFiles++;
    });
    updateHeader();
    uploadNext();
}
