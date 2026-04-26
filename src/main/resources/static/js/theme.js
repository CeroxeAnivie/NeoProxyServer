/**
 * theme.js — 主题管理
 *
 * 处理 light / dark / system 三种主题模式的切换与持久化。
 */

const systemThemeMedia = window.matchMedia("(prefers-color-scheme: dark)");

/**
 * 应用主题模式
 * @param {string} mode 'light' | 'dark' | 'system'
 */
function applyTheme(mode) {
    localStorage.setItem('theme', mode);

    // 更新侧边栏按钮高亮状态
    document.querySelectorAll('.ctrl-btn').forEach(b => {
        b.classList.remove('active');
        const onclick = b.getAttribute('onclick');
        if (onclick && onclick.includes(`'${mode}'`)) b.classList.add('active');
    });

    // 解析实际主题: system 模式需要判断系统偏好
    let resolved = mode;
    if (mode === 'system') resolved = systemThemeMedia.matches ? 'dark' : 'light';

    document.documentElement.setAttribute('data-theme', resolved);
}
