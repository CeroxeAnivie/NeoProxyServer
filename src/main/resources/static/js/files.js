/* files.js — 文件管理器: 导航、渲染、拖拽、编辑、移动、右键菜单、剪贴板、属性 */

let dragSrcItem = null;
let dragGhost = null;
let dragTimer = null;
let currentEditPath = "";

/* ========== 文件导航 ========== */

function loadDir(path) {
    fileRequestTarget = "view";
    curPath = path;
    selectedFiles.clear();
    updateMoveBtn();
    document.getElementById("file-grid").innerHTML =
        '<div style="grid-column:1/-1;text-align:center;padding:2rem;color:var(--text-sub)">' +
        '<i class="fas fa-circle-notch fa-spin"></i> <span data-i18n="loading">' + t("loading") + "</span></div>";
    ws.send("#GET_FILES:" + path);
}

function refreshFiles() {
    loadDir(curPath);
}

function handleFileList(list, path) {
    list = list || [];
    if (fileRequestTarget === "view") {
        currentFileList = list;
        renderFiles(list, path);
    } else renderMoveList(list, path);
}

/* ========== 文件渲染 ========== */

function isImage(name) {
    return /\.(jpg|jpeg|png|gif|webp|svg|ico|bmp)$/i.test(name);
}

function getFileIcon(name, isDir) {
    if (isDir) return "fa-folder";
    if (isImage(name)) return "fa-image";
    if (name.endsWith(".jar")) return "fa-cube";
    if (name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z") ||
        name.endsWith(".tar") || name.endsWith(".gz")) return "fa-file-archive";
    if (name.endsWith(".js") || name.endsWith(".html") || name.endsWith(".css") ||
        name.endsWith(".json") || name.endsWith(".xml")) return "fa-file-code";
    if (name.endsWith(".txt") || name.endsWith(".cfg") || name.endsWith(".properties") ||
        name.endsWith(".log")) return "fa-file-alt";
    return "fa-file";
}

function renderFiles(list, path) {
    curPath = path;
    var parts = path.split(/[/\\\\]/);
    var bcHtml = '<span class="crumb" onclick="loadDir(\'\')"><i class="fas fa-home"></i></span>';
    var buildPath = "";
    parts.forEach(function (p) {
        if (p) {
            buildPath += (buildPath ? "/" : "") + p;
            // 【安全修复】面包屑路径必须转义
            bcHtml += ' / <span class="crumb" onclick="loadDir(\'' + escapeHtml(buildPath) + '\')">' + escapeHtml(p) + "</span>";
        }
    });
    document.getElementById("file-crumb").innerHTML = bcHtml;

    list.sort(function (a, b) {
        if (a.isDir && !b.isDir) return -1;
        if (!a.isDir && b.isDir) return 1;
        return a.name.localeCompare(b.name);
    });

    var grid = document.getElementById("file-grid");
    var visible = list.filter(function (f) {
        return f.name !== "..";
    });
    if (visible.length === 0) {
        grid.innerHTML = '<div style="grid-column:1/-1;text-align:center;padding:2rem;color:var(--text-sub)">' +
            '<span data-i18n="no_files">' + t("no_files") + "</span></div>";
        updateMoveBtn();
        return;
    }

    var token = new URLSearchParams(location.search).get("token");
    var html = "";
    list.forEach(function (f) {
        if (f.name === "..") return;
        var icon = getFileIcon(f.name, f.isDir);
        var isImg = isImage(f.name);
        var relPath = (path ? path + "/" : "") + f.name;
        var clickAction = f.isDir
            ? "loadDir('" + relPath + "')"
            : (isImg
                ? "window.open('/download?file=" + encodeURIComponent(relPath) + "&token=" + token + "', '_blank')"
                : "reqEdit('" + relPath + "')");
        var delAction = "event.stopPropagation(); reqDeleteFile('" + relPath + "', '" + f.name + "')";
        var dlLink = "/download?file=" + encodeURIComponent(relPath) + "&token=" + token;
        var dropAttr = f.isDir
            ? ' ondrop="handleFolderDrop(event, \'' + f.name + '\')" ondragover="handleDragOver(event)" ondragleave="handleDragLeave(event)"'
            : "";
        var checked = selectedFiles.has(f.name) ? " checked" : "";
        var sizeMeta = f.isDir ? "-" : fmtSize(f.size);

        html += '<div class="file-item ' + (f.isDir ? "is-dir" : "") + '" draggable="true"' +
            ' ondragstart="handleDragStart(event, \'' + f.name + '\')"' +
            dropAttr +
            ' ontouchstart="handleTouchStart(event, \'' + f.name + '\')"' +
            ' ontouchmove="handleTouchMove(event)"' +
            ' ontouchend="handleTouchEnd(event)"' +
            ' onclick="' + clickAction + '"' +
            ' oncontextmenu="event.preventDefault(); showContextMenu(event, {name:\'' + f.name + "',isDir:" + f.isDir + ",path:'" + relPath + "',size:" + f.size + ",time:" + f.time + "})\">" +
            '<input type="checkbox" class="file-check" onchange="toggleSelect(\'' + f.name + "', event)\" onclick=\"event.stopPropagation()\"" + checked + ">" +
            '<i class="fas ' + icon + ' file-icon"></i>' +
            '<div class="file-name">' + escapeHtml(f.name) + "</div>" +
            '<div class="file-meta">' + sizeMeta + "</div>" +
            '<a href="' + dlLink + '" target="_blank" class="file-dl-btn" onclick="event.stopPropagation()">' +
            '<i class="fas fa-download"></i></a>' +
            '<i class="fas fa-trash" style="position:absolute;bottom:5px;right:5px;color:var(--danger);opacity:0.5;font-size:0.8rem;padding:5px"' +
            ' onclick="' + delAction + '"></i></div>';
    });

    grid.innerHTML = html;
    updateMoveBtn();
}

/* ========== 拖拽 & 触摸 ========== */

function handleDragStart(e, name) {
    e.stopPropagation();
    if (!selectedFiles.has(name)) {
        selectedFiles.clear();
        selectedFiles.add(name);
        updateMoveBtn();
    }
    e.dataTransfer.setData("text/plain", name);
    e.dataTransfer.effectAllowed = "move";
}

function handleDragOver(e) {
    e.preventDefault();
    e.currentTarget.classList.add("drag-over");
}

function handleDragLeave(e) {
    e.currentTarget.classList.remove("drag-over");
}

function handleFolderDrop(e, targetFolder) {
    e.preventDefault();
    e.stopPropagation();
    e.currentTarget.classList.remove("drag-over");
    reqMoveTo(targetFolder);
}

function handleTouchStart(e, name) {
    if (e.target.closest(".file-check") || e.target.closest(".file-dl-btn") || e.target.closest(".fa-trash")) return;
    dragTimer = setTimeout(function () {
        dragSrcItem = {name: name, x: e.touches[0].clientX, y: e.touches[0].clientY};
        if (!selectedFiles.has(name)) {
            selectedFiles.clear();
            selectedFiles.add(name);
            updateMoveBtn();
        }
        createDragGhost(e.touches[0].clientX, e.touches[0].clientY);
        navigator.vibrate && navigator.vibrate(50);
    }, 300);
}

function handleTouchMove(e) {
    if (dragSrcItem) {
        e.preventDefault();
        moveDragGhost(e.touches[0].clientX, e.touches[0].clientY);
    }
}

function handleTouchEnd(e) {
    clearTimeout(dragTimer);
    removeDragGhost();
    if (dragSrcItem) {
        var touch = e.changedTouches[0];
        var elem = document.elementFromPoint(touch.clientX, touch.clientY);
        if (elem) {
            var folderItem = elem.closest(".is-dir");
            if (folderItem) {
                var targetName = folderItem.querySelector(".file-name").innerText;
                if (targetName && targetName !== dragSrcItem.name) reqMoveTo(targetName);
            }
        }
    }
    dragSrcItem = null;
}

function createDragGhost(x, y) {
    removeDragGhost();
    dragGhost = document.createElement("div");
    dragGhost.style.cssText = "position:fixed;left:" + x + "px;top:" + y + "px;padding:10px;" +
        "background:var(--accent);color:white;border-radius:8px;pointer-events:none;z-index:10000;opacity:0.8";
    dragGhost.innerHTML = '<i class="fas fa-file"></i> ' + selectedFiles.size;
    document.body.appendChild(dragGhost);
}

function moveDragGhost(x, y) {
    if (dragGhost) {
        dragGhost.style.left = x + "px";
        dragGhost.style.top = y + "px";
    }
}

function removeDragGhost() {
    if (dragGhost) {
        dragGhost.remove();
        dragGhost = null;
    }
}

/* ========== 多选 & 移动 ========== */

function toggleSelect(name, e) {
    if (selectedFiles.has(name)) selectedFiles.delete(name); else selectedFiles.add(name);
    updateMoveBtn();
}

function toggleSelectAll() {
    var checkboxes = document.querySelectorAll(".file-check");
    if (checkboxes.length === 0) return;
    var allChecked = Array.from(checkboxes).every(function (c) {
        return c.checked;
    });
    checkboxes.forEach(function (c) {
        if (!allChecked && !c.checked) c.click();
        else if (allChecked && c.checked) c.click();
    });
}

function updateMoveBtn() {
    var btn = document.getElementById("btn-move");
    if (selectedFiles.size > 0) {
        btn.classList.add("active");
        btn.disabled = false;
    } else {
        btn.classList.remove("active");
        btn.disabled = true;
    }
}

async function reqMoveTo(targetFolder) {
    if (selectedFiles.size === 0) return;
    var payload = "#MOVE_FILES:" + targetFolder;
    selectedFiles.forEach(function (f) {
        payload += "|" + (curPath ? curPath + "/" : "") + f;
    });
    ws.send(payload);
    selectedFiles.clear();
    updateMoveBtn();
}

/* ========== 文件编辑 ========== */

function reqEdit(path) {
    currentEditPath = path;
    document.getElementById("editor-modal").classList.add("show");
    document.getElementById("editor-modal").style.display = "flex";
    document.getElementById("editor-loading").style.display = "flex";
    document.getElementById("editor-content").style.display = "none";
    document.getElementById("editor-filename").innerText = path;
    ws.send("#READ_FILE:" + path);
}

function confirmDownload(path) {
    closeEditor();
    openConfirmModal(t("msg_file_too_large")).then(function (yes) {
        if (yes) {
            var token = new URLSearchParams(location.search).get("token");
            window.open("/download?file=" + encodeURIComponent(path) + "&token=" + token, "_blank");
        }
    });
}

function showEditorContent(content) {
    document.getElementById("editor-loading").style.display = "none";
    var ta = document.getElementById("editor-content");
    ta.style.display = "block";
    ta.value = content;
}

function saveFile() {
    var content = document.getElementById("editor-content").value;
    ws.send("#SAVE_FILE:" + currentEditPath + "|" + content);
}

function closeEditor() {
    document.getElementById("editor-modal").classList.remove("show");
    setTimeout(function () {
        document.getElementById("editor-modal").style.display = "none";
    }, 300);
}

/* ========== 新建文件/文件夹 ========== */

function openCreateModal(isDir) {
    createModeDir = isDir;
    document.getElementById("create-modal-title").innerText = isDir ? t("modal_new_folder") : t("modal_new_file");
    document.getElementById("create-modal").style.display = "flex";
    document.getElementById("create-modal").classList.add("show");
    document.getElementById("new-file-name").value = "";
    document.getElementById("new-file-name").focus();
}

function confirmCreate() {
    var name = document.getElementById("new-file-name").value.trim();
    if (name) {
        ws.send((createModeDir ? "#CREATE_DIR:" : "#CREATE_FILE:") + curPath + "|" + name);
        document.getElementById("create-modal").classList.remove("show");
        setTimeout(function () {
            document.getElementById("create-modal").style.display = "none";
        }, 300);
    }
}

/* ========== 删除文件 ========== */

async function reqDeleteFile(path, name) {
    if (await openConfirmModal(t("confirm_del") + " (" + name + ")")) {
        ws.send("#DELETE_FILE:" + path);
    }
}

/* ========== 移动弹窗 ========== */

function openMoveSelector() {
    if (selectedFiles.size === 0) return;
    moveModalPath = "";
    fileRequestTarget = "modal";
    document.getElementById("move-modal").classList.add("show");
    document.getElementById("move-modal").style.display = "flex";
    loadMoveDir("");
}

function closeMoveModal() {
    document.getElementById("move-modal").classList.remove("show");
    setTimeout(function () {
        document.getElementById("move-modal").style.display = "none";
    }, 300);
    fileRequestTarget = "view";
}

function loadMoveDir(path) {
    moveModalPath = path;
    document.getElementById("move-current-path").innerText = path || "/";
    document.getElementById("move-list").innerHTML =
        '<div style="padding:1rem;text-align:center"><span data-i18n="loading">Loading...</span></div>';
    ws.send("#GET_FILES:" + path);
}

function moveGoUp() {
    if (!moveModalPath) return;
    var parts = moveModalPath.split("/");
    parts.pop();
    loadMoveDir(parts.join("/"));
}

function renderMoveList(list, path) {
    var div = document.getElementById("move-list");
    if (!list) {
        div.innerHTML = '<div style="padding:1rem;text-align:center;color:var(--text-sub)">' +
            '<span data-i18n="move_no_subs">' + t("move_no_subs") + "</span></div>";
        return;
    }
    var dirs = list.filter(function (f) {
        return f.isDir && f.name !== "..";
    })
        .sort(function (a, b) {
            return a.name.localeCompare(b.name);
        });
    if (dirs.length === 0) {
        div.innerHTML = '<div style="padding:1rem;text-align:center;color:var(--text-sub)">' +
            '<span data-i18n="move_no_subs">' + t("move_no_subs") + "</span></div>";
    } else {
        div.innerHTML = dirs.map(function (d) {
            // 【安全修复】目录名必须转义
            var escName = escapeHtml(d.name);
            return '<div class="move-item" onclick="loadMoveDir(\'' + (path ? path + "/" : "") + escName + '\')">' +
                '<i class="fas fa-folder" style="color:var(--warning)"></i> ' + escName + "</div>";
        }).join("");
    }
}

function confirmMove() {
    reqMoveTo(moveModalPath);
    closeMoveModal();
}

function createFolderInMoveMode() {
    openInputModal(t("modal_new_folder"), t("move_new_folder_ph")).then(function (name) {
        if (name) {
            ws.send("#CREATE_DIR:" + moveModalPath + "|" + name);
            setTimeout(function () {
                loadMoveDir(moveModalPath);
            }, 300);
        }
    });
}

/* ========== 右键菜单 ========== */

function showContextMenu(e, item) {
    var menu = document.getElementById("context-menu");
    menu.style.display = "flex";
    menu.style.top = e.pageY + "px";
    menu.style.left = e.pageX + "px";
    var w = window.innerWidth;
    var h = window.innerHeight;
    if (e.pageX + 180 > w) menu.style.left = (w - 190) + "px";
    if (e.pageY + 200 > h) menu.style.top = (h - 210) + "px";

    var html = "";
    var token = new URLSearchParams(location.search).get("token");

    if (item) {
        document.querySelectorAll(".file-item").forEach(function (el) {
            el.classList.remove("context-active");
        });
        var el = e.target.closest(".file-item");
        if (el) el.classList.add("context-active");

        // 【安全修复】右键菜单内容必须转义
        var escItemName = escapeHtml(item.name);
        var escItemPath = escapeHtml(item.path);

        if (item.isDir) {
            html += '<div class="context-item" onclick="loadDir(\'' + escItemPath + '\')">' +
                '<i class="fas fa-folder-open"></i> ' + t("ctx_open") + "</div>";
        } else {
            html += '<div class="context-item" onclick="reqEdit(\'' + escItemPath + '\')">' +
                '<i class="fas fa-edit"></i> ' + t("ctx_open") + "</div>";
            html += '<div class="context-item" onclick="window.open(\'/download?file=' +
                encodeURIComponent(item.path) + "&token=" + token + "')\">" +
                '<i class="fas fa-download"></i> ' + t("ctx_download") + "</div>";
        }

        html += '<div class="context-sep"></div>';
        html += '<div class="context-item" onclick="copyFile(\'' + escItemName + "', 'copy\')">" +
            '<i class="fas fa-copy"></i> ' + t("ctx_copy") + "</div>";
        html += '<div class="context-item" onclick="copyFile(\'' + escItemName + "', 'cut\')">" +
            '<i class="fas fa-cut"></i> ' + t("ctx_cut") + "</div>";

        html += '<div class="context-sep"></div>';
        html += '<div class="context-item" onclick="renameFile(\'' + escItemName + '\')">' +
            '<i class="fas fa-i-cursor"></i> ' + t("ctx_rename") + "</div>";
        html += '<div class="context-item" onclick="selectedFiles.clear();selectedFiles.add(\'' + escItemName + '\');openMoveSelector()">' +
            '<i class="fas fa-dolly"></i> ' + t("ctx_move") + "</div>";
        html += '<div class="context-item danger" onclick="reqDeleteFile(\'' + escItemPath + "','" + escItemName + '\')">' +
            '<i class="fas fa-trash"></i> ' + t("ctx_delete") + "</div>";

        html += '<div class="context-sep"></div>';
        html += '<div class="context-item" onclick="showProperties(\'' + escItemName + "'," + item.size + "," + item.isDir + ",'" + escItemPath + "'," + item.time + ')">' +
            '<i class="fas fa-info-circle"></i> ' + t("ctx_props") + "</div>";
    } else {
        html += '<div class="context-item" onclick="refreshFiles()">' +
            '<i class="fas fa-sync-alt"></i> ' + t("btn_refresh") + "</div>";
        if (clipboardFiles.size > 0) {
            html += '<div class="context-sep"></div>';
            html += '<div class="context-item" onclick="pasteFiles()">' +
                '<i class="fas fa-paste"></i> ' + t("ctx_paste") + " (" + clipboardFiles.size + ")</div>";
        }
    }
    menu.innerHTML = html;
}

/* ========== 剪贴板 ========== */

function copyFile(name, action) {
    clipboardFiles.clear();
    if (selectedFiles.has(name) && selectedFiles.size > 1) {
        selectedFiles.forEach(function (f) {
            clipboardFiles.add(f);
        });
    } else {
        clipboardFiles.add(name);
    }
    clipboardAction = action;
    showPasteFab();
}

function showPasteFab() {
    var fab = document.getElementById("paste-fab");
    fab.classList.add("show");
    document.getElementById("paste-count").innerText = clipboardFiles.size + " Files";
    document.getElementById("paste-action").innerText = clipboardAction === "copy" ? t("ctx_copy") : t("ctx_cut");
}

function clearClipboard() {
    clipboardFiles.clear();
    document.getElementById("paste-fab").classList.remove("show");
}

function pasteFiles() {
    if (clipboardFiles.size === 0) return;
    var cmd = clipboardAction === "cut" ? "#MOVE_FILES:" : "#COPY_FILES:";
    cmd += curPath;
    clipboardFiles.forEach(function (f) {
        cmd += "|" + (curPath ? curPath + "/" : "") + f;
    });
    ws.send(cmd);
    if (clipboardAction === "cut") clearClipboard();
    else showToast(t("msg_pasted"), "success");
    refreshFiles();
}

/* ========== 属性弹窗 ========== */

function showProperties(name, size, isDir, path, time) {
    var modal = document.getElementById("props-modal");
    var body = document.getElementById("props-body");
    modal.classList.add("show");
    modal.style.display = "flex";

    var html = "";
    if (selectedFiles.size > 1 && selectedFiles.has(name)) {
        var count = selectedFiles.size;
        var totalSize = 0, fCount = 0, dCount = 0;
        currentFileList.forEach(function (f) {
            if (selectedFiles.has(f.name)) {
                totalSize += f.size;
                if (f.isDir) dCount++; else fCount++;
            }
        });
        html += '<div class="prop-row"><span class="prop-k">' + t("prop_count") + "</span>" +
            '<span class="prop-v">' + count + "</span></div>" +
            '<div class="prop-row"><span class="prop-k">' + t("prop_files") + "</span>" +
            '<span class="prop-v">' + fCount + "</span></div>" +
            '<div class="prop-row"><span class="prop-k">' + t("prop_folders") + "</span>" +
            '<span class="prop-v">' + dCount + "</span></div>" +
            '<div class="prop-row"><span class="prop-k">' + t("prop_total_size") + "</span>" +
            '<span class="prop-v">' + fmtSize(totalSize) +
            '<br><span style="font-size:0.7rem;color:var(--text-sub)">(' + totalSize.toLocaleString() + " bytes)</span></span></div>";
    } else {
        var timeStr = "-";
        if (time) {
            var d = new Date(time);
            timeStr = d.toLocaleString();
        }
        html += '<div style="text-align:center;margin-bottom:1rem">' +
            '<i class="fas ' + getFileIcon(name, isDir) + '" style="font-size:3rem;color:var(--accent)"></i>' +
            // 【安全修复】属性弹窗内容必须转义
            '<div style="font-weight:bold;margin-top:10px;word-break:break-all">' + escapeHtml(name) + "</div></div>" +
            '<div class="prop-row"><span class="prop-k">' + t("prop_type") + "</span>" +
            '<span class="prop-v">' + (isDir ? "Folder" : "File") + "</span></div>" +
            '<div class="prop-row"><span class="prop-k">' + t("prop_path") + "</span>" +
            '<span class="prop-v">' + escapeHtml(path) + "</span></div>" +
            '<div class="prop-row"><span class="prop-k">' + t("prop_size") + "</span>" +
            '<span class="prop-v">' + (isDir ? "-" : (fmtSize(size) +
                '<br><span style="font-size:0.7rem;color:var(--text-sub)">( ' + size.toLocaleString() + " bytes)</span>")) + "</span></div>" +
            '<div class="prop-row"><span class="prop-k">' + t("prop_time") + "</span>" +
            '<span class="prop-v">' + escapeHtml(timeStr) + "</span></div>";
    }
    body.innerHTML = html;
}

/* ========== 文件重命名 ========== */

function renameFile(oldName) {
    openInputModal(t("ctx_rename"), oldName).then(function (v) {
        if (v && v !== oldName) {
            if (v.includes("/") || v.includes("\\")) {
                showToast(t("msg_name_invalid"), "danger");
            } else {
                ws.send("#RENAME_FILE:" + curPath + "|" + oldName + "|" + v);
                showToast(t("msg_renamed"), "success");
            }
        }
    });
}

/* ========== 文件选择器 (上传用) ========== */

function triggerFileSelect() {
    document.getElementById("upload-input-file").value = null;
    document.getElementById("upload-input-file").click();
}

function triggerFolderSelect() {
    document.getElementById("upload-input-folder").value = null;
    document.getElementById("upload-input-folder").click();
}
