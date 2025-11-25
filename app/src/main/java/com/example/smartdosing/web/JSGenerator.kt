package com.example.smartdosing.web

import kotlinx.html.*

/**
 * JavaScript功能生成器
 */

/**
 * 生成主页JavaScript
 */
fun BODY.generateMainPageScript() {
    script {
        unsafe {
            +"""
const dashboardState = {
    tasks: [],
    devices: [],
    publishLog: []
};

document.addEventListener('DOMContentLoaded', () => {
    prefillQuickPublishForm();
    loadDashboardOverview();
    loadPublishRecipes();
    document.getElementById('reload-tasks-btn')?.addEventListener('click', loadDashboardOverview);
    document.getElementById('refresh-devices-btn')?.addEventListener('click', loadDashboardOverview);
    document.getElementById('refresh-device-target')?.addEventListener('click', event => {
        event.preventDefault();
        loadDashboardOverview();
    });
    document.getElementById('submit-publish')?.addEventListener('click', submitQuickPublish);
    document.getElementById('reset-publish')?.addEventListener('click', resetPublishForm);
    document.getElementById('open-publish-panel')?.addEventListener('click', () => {
        document.querySelector('.publish-panel')?.scrollIntoView({ behavior: 'smooth' });
    });
});

function prefillQuickPublishForm() {
    const cached = localStorage.getItem('quickPublishRecipe');
    if (!cached) return;
    try {
        const payload = JSON.parse(cached);
        selectPublishRecipe(payload);
    } catch (error) {
        console.warn('读取预填信息失败', error);
    } finally {
        localStorage.removeItem('quickPublishRecipe');
    }
}

const publishRecipeState = {
    all: [],
    selected: null
};

async function loadPublishRecipes() {
    try {
        const response = await fetch('/api/recipes');
        const data = await response.json();
        if (data.success) {
            publishRecipeState.all = data.data || [];
            setupPublishRecipeSuggestions();
        }
    } catch (error) {
        console.warn('加载配方列表失败', error);
    }
}

function setupPublishRecipeSuggestions() {
    const input = document.getElementById('publish-recipe');
    const panel = document.getElementById('recipe-suggestions');
    if (!input || !panel) return;

    input.addEventListener('input', () => {
        const keyword = input.value.trim().toLowerCase();
        if (!keyword) {
            panel.innerHTML = '';
            panel.classList.remove('active');
            publishRecipeState.selected = null;
            return;
        }
        const matches = publishRecipeState.all.filter(item =>
            item.name.toLowerCase().includes(keyword) ||
            (item.code || '').toLowerCase().includes(keyword)
        ).slice(0, 5);

        if (!matches.length) {
            panel.innerHTML = `<ul><li><span class="suggest-name">暂无匹配结果</span></li></ul>`;
            panel.classList.add('active');
            publishRecipeState.selected = null;
            return;
        }

        panel.innerHTML = `
            <ul>
                ${'$'}{matches.map(item => `
                    <li data-id="${'$'}{item.id}">
                        <span class="suggest-name">${'$'}{item.name}</span>
                        <span class="suggest-code">${'$'}{item.code || '未编号'} · ${'$'}{item.customer || '客户未指定'}</span>
                    </li>
                `).join('')}
            </ul>
        `;
        panel.classList.add('active');
        panel.querySelectorAll('li').forEach(li => {
            li.addEventListener('click', () => {
                const recipeId = li.dataset.id;
                const recipe = publishRecipeState.all.find(item => item.id === recipeId);
                if (recipe) {
                    selectPublishRecipe(recipe);
                    panel.classList.remove('active');
                    panel.innerHTML = '';
                }
            });
        });
    });

    document.addEventListener('click', event => {
        if (!panel.contains(event.target) && event.target !== input) {
            panel.classList.remove('active');
            panel.innerHTML = '';
        }
    });
}

function selectPublishRecipe(recipe) {
    const input = document.getElementById('publish-recipe');
    const titleInput = document.getElementById('publish-title');
    const prioritySelect = document.getElementById('publish-priority');
    if (input) input.value = recipe.code || recipe.name;
    if (titleInput && !titleInput.value) titleInput.value = recipe.title || recipe.name;
    if (prioritySelect && recipe.priority) prioritySelect.value = recipe.priority;
    publishRecipeState.selected = recipe;
}

async function loadDashboardOverview() {
    try {
        const response = await fetch('/api/tasks/overview');
        const data = await response.json();
        if (!data.success) {
            showNotification(data.message || '加载任务数据失败', 'error');
            return;
        }
        const overview = data.data;
        dashboardState.tasks = overview.tasks || [];
        dashboardState.devices = overview.devices || [];
        dashboardState.publishLog = overview.publishLog || [];

        document.getElementById('pending-task-count').textContent = overview.pendingCount ?? 0;
        document.getElementById('running-task-count').textContent = overview.runningCount ?? 0;
        document.getElementById('completed-task-count').textContent = overview.completedToday ?? 0;
        document.getElementById('device-heartbeat-window').textContent = overview.heartbeatWindow || '--';

        renderTaskList(dashboardState.tasks);
        renderDeviceList(dashboardState.devices);
        renderPublishLog(dashboardState.publishLog);
        renderCurrentDevice(dashboardState.devices);
    } catch (error) {
        console.error('加载概览失败', error);
        showNotification('网络异常，无法加载概览信息', 'error');
    }
}

function renderTaskList(tasks) {
    const list = document.getElementById('today-task-list');
    if (!list) return;
    list.innerHTML = '';
    if (!tasks.length) {
        const empty = document.createElement('div');
        empty.className = 'empty-placeholder';
        empty.textContent = '暂无任务，请先导入配方并使用“快速发布”。';
        list.appendChild(empty);
        return;
    }
    tasks.slice(0, 5).forEach(task => {
        const card = document.createElement('div');
        card.className = 'task-card';
        card.innerHTML = `
            <div class="task-title">${'$'}{task.title}</div>
            <div class="task-meta">
                <span>${'$'}{task.recipeName || task.recipeCode}</span>
                <span>${'$'}{task.customer || '未指定'}</span>
            </div>
            <div class="task-status task-status-${'$'}{task.status?.toLowerCase()}">${'$'}{renderStatusLabel(task.status)}</div>
        `;
        list.appendChild(card);
    });
}

function renderStatusLabel(status) {
    switch(status) {
        case 'READY': return '待发布';
        case 'PUBLISHED': return '已下发';
        case 'IN_PROGRESS': return '执行中';
        case 'COMPLETED': return '已完成';
        case 'DRAFT': return '草稿';
        case 'CANCELLED': return '已取消';
        default: return status || '未知';
    }
}

function renderDeviceList(devices) {
    const container = document.getElementById('device-list');
    if (!container) return;
    container.innerHTML = '';
    if (!devices.length) {
        const empty = document.createElement('div');
        empty.className = 'empty-placeholder';
        empty.textContent = '未检测到本机设备，请检查 Web 服务。';
        container.appendChild(empty);
        return;
    }
    devices.forEach(device => {
        const item = document.createElement('div');
        item.className = `device-card device-${'$'}{(device.status || 'offline').toLowerCase()}`;
        item.innerHTML = `
            <div class="device-name">${'$'}{device.name}</div>
            <div class="device-status">${'$'}{renderDeviceStatus(device.status)}</div>
            <div class="device-task">${'$'}{device.currentTaskName || '空闲'}</div>
            <div class="device-heartbeat">上次心跳：${'$'}{device.lastHeartbeat || '--'}</div>
        `;
        container.appendChild(item);
    });
}

function renderDeviceStatus(status) {
    switch(status) {
        case 'ONLINE': return '在线';
        case 'BUSY': return '执行中';
        case 'OFFLINE': return '离线';
        case 'ERROR': return '异常';
        default: return '未知';
    }
}

function renderCurrentDevice(devices) {
    const display = document.getElementById('current-device-name');
    const hiddenInput = document.getElementById('publish-device-id');
    const device = Array.isArray(devices) && devices.length ? devices[0] : null;
    if (hiddenInput) hiddenInput.value = device?.id || '';
    if (display) {
        display.textContent = device ? `${'$'}{device.name}（${'$'}{renderDeviceStatus(device.status)}）` : '未检测到设备，请刷新页面';
    }
}

function renderPublishLog(logItems) {
    const container = document.getElementById('publish-log-list');
    if (!container) return;
    container.innerHTML = '';
    if (!logItems.length) {
        const empty = document.createElement('div');
        empty.className = 'empty-placeholder';
        empty.textContent = '暂无发布记录';
        container.appendChild(empty);
        return;
    }
    logItems.slice(0, 5).forEach(item => {
        const row = document.createElement('div');
        row.className = 'publish-log-item';
        row.innerHTML = `
            <div class="log-time">${'$'}{item.time}</div>
            <div class="log-body">
                <div class="log-title">${'$'}{item.title}</div>
                <div class="log-desc">${'$'}{item.description}</div>
            </div>
        `;
        container.appendChild(row);
    });
}

async function submitQuickPublish() {
    const titleInput = document.getElementById('publish-title');
    const recipeInput = document.getElementById('publish-recipe');
    const priorityInput = document.getElementById('publish-priority');
    const deviceIdInput = document.getElementById('publish-device-id');

    const payload = {
        title: titleInput?.value?.trim(),
        recipeKeyword: publishRecipeState.selected?.code || recipeInput?.value?.trim(),
        priority: priorityInput?.value || 'NORMAL',
        deviceId: deviceIdInput?.value?.trim()
    };

    if (!publishRecipeState.selected && !payload.recipeKeyword) {
        showNotification('请先选择配方', 'warning');
        return;
    }
    if (!payload.title) {
        showNotification('请填写任务名称', 'warning');
        return;
    }
    if (!payload.deviceId) {
        showNotification('当前设备不可用，请重新检测或检查连接', 'warning');
        return;
    }

    try {
        const response = await fetch('/api/tasks/quick-publish', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const data = await response.json();
        if (data.success) {
            showNotification('任务发布成功', 'success');
            resetPublishForm();
            loadDashboardOverview();
        } else {
            showNotification(data.message || '发布失败', 'error');
        }
    } catch (error) {
        console.error('发布任务失败', error);
        showNotification('网络异常，发布失败', 'error');
    }
}

function resetPublishForm() {
    document.getElementById('publish-title')?.value = '';
    document.getElementById('publish-recipe')?.value = '';
    publishRecipeState.selected = null;
}

function showNotification(message, type = 'info') {
    const notification = document.createElement('div');
    notification.className = `notification notification-${'$'}{type}`;
    notification.textContent = message;
    document.body.appendChild(notification);
    requestAnimationFrame(() => notification.classList.add('active'));
    setTimeout(() => {
        notification.classList.remove('active');
        setTimeout(() => notification.remove(), 300);
    }, 3000);
}
            """
        }
    }
}

fun BODY.generateTaskCenterPageScript() {
    script {
        unsafe {
            +"""
const taskCenterState = {
    filterStatus: '',
    searchText: ''
};

document.addEventListener('DOMContentLoaded', () => {
    document.getElementById('task-status-filter')?.addEventListener('change', event => {
        taskCenterState.filterStatus = event.target.value;
        loadKanbanBoard();
    });
    document.getElementById('task-search-input')?.addEventListener('input', event => {
        taskCenterState.searchText = event.target.value.trim();
        debounce(loadKanbanBoard, 300)();
    });
    document.getElementById('create-task-btn')?.addEventListener('click', () => {
        showNotification('任务创建入口即将开放，请通过移动端或API创建', 'info');
    });

    loadKanbanBoard();
    loadPublishLog();
});

async function loadKanbanBoard() {
    const params = new URLSearchParams();
    if (taskCenterState.filterStatus) params.append('status', taskCenterState.filterStatus);
    if (taskCenterState.searchText) params.append('search', taskCenterState.searchText);

    try {
        const response = await fetch(`/api/tasks?${'$'}{params.toString()}`);
        const data = await response.json();
        if (!data.success) {
            showNotification(data.message || '加载任务失败', 'error');
            return;
        }
        renderKanbanBoard(data.data || []);
    } catch (error) {
        console.error('加载看板失败', error);
        showNotification('网络异常，无法加载任务', 'error');
    }
}

function renderKanbanBoard(tasks) {
    const readyList = document.getElementById('kanban-ready');
    const progressList = document.getElementById('kanban-progress');
    const doneList = document.getElementById('kanban-done');
    [readyList, progressList, doneList].forEach(list => { if (list) list.innerHTML = ''; });

    const ready = tasks.filter(t => t.status === 'READY' || t.status === 'DRAFT');
    const running = tasks.filter(t => t.status === 'PUBLISHED' || t.status === 'IN_PROGRESS');
    const completed = tasks.filter(t => t.status === 'COMPLETED');

    ready.forEach(task => appendKanbanCard(readyList, task));
    running.forEach(task => appendKanbanCard(progressList, task));
    completed.forEach(task => appendKanbanCard(doneList, task));

    if (!ready.length) appendEmptyCard(readyList, '暂无待发布任务');
    if (!running.length) appendEmptyCard(progressList, '暂无进行中任务');
    if (!completed.length) appendEmptyCard(doneList, '今日暂无完成任务');
}

function appendKanbanCard(container, task) {
    if (!container) return;
    const card = document.createElement('div');
    card.className = 'kanban-card';
    card.innerHTML = `
        <div class="card-title">${'$'}{task.title}</div>
        <div class="card-meta">${'$'}{task.recipeName || task.recipeCode}</div>
        <div class="card-footer">
            <span>${'$'}{renderStatusLabel(task.status)}</span>
            <button class="btn btn-link" data-task="${'$'}{task.id}">详情</button>
        </div>
    `;
    card.querySelector('button')?.addEventListener('click', () => loadTaskDetail(task.id));
    container.appendChild(card);
}

function appendEmptyCard(container, message) {
    if (!container) return;
    const empty = document.createElement('div');
    empty.className = 'kanban-empty';
    empty.textContent = message;
    container.appendChild(empty);
}

async function loadTaskDetail(taskId) {
    const detailBody = document.getElementById('task-detail-body');
    const emptyPlaceholder = document.getElementById('task-detail-empty');
    if (emptyPlaceholder) emptyPlaceholder.style.display = 'none';
    if (detailBody) detailBody.innerHTML = '<div class="skeleton">加载中...</div>';

    try {
        const response = await fetch(`/api/tasks/${'$'}{taskId}`);
        const data = await response.json();
        if (!data.success) {
            showNotification(data.message || '加载任务详情失败', 'error');
            return;
        }
        renderTaskDetail(data.data);
    } catch (error) {
        console.error('加载任务详情失败', error);
        showNotification('网络异常，无法加载详情', 'error');
    }
}

function renderTaskDetail(task) {
    const detailBody = document.getElementById('task-detail-body');
    if (!detailBody) return;
    detailBody.innerHTML = `
        <div class="detail-row"><span>任务名称</span><span>${'$'}{task.title}</span></div>
        <div class="detail-row"><span>关联配方</span><span>${'$'}{task.recipeName || task.recipeCode}</span></div>
        <div class="detail-row"><span>客户/项目</span><span>${'$'}{task.customer || '未指定'}</span></div>
        <div class="detail-row"><span>业务负责人</span><span>${'$'}{task.salesOwner || '未指定'}</span></div>
        <div class="detail-row"><span>调香师</span><span>${'$'}{task.requestedBy || task.perfumer || '未指定'}</span></div>
        <div class="detail-row"><span>状态</span><span>${'$'}{renderStatusLabel(task.status)}</span></div>
        <div class="detail-row"><span>目标设备</span><span>${'$'}{(task.targetDevices || []).join(', ') || '未指派'}</span></div>
        <div class="detail-row"><span>备注</span><span>${'$'}{task.note || '—'}</span></div>
    `;
}

async function loadPublishLog() {
    const container = document.getElementById('publish-log-list');
    if (!container) return;
    container.innerHTML = '<div class="skeleton">加载中...</div>';
    try {
        const response = await fetch('/api/publish-log');
        const data = await response.json();
        if (!data.success) {
            container.innerHTML = '';
            showNotification(data.message || '加载发布日志失败', 'error');
            return;
        }
        container.innerHTML = '';
        const items = data.data || [];
        if (!items.length) {
            appendEmptyCard(container, '暂无发布记录');
            return;
        }
        items.forEach(entry => {
            const row = document.createElement('div');
            row.className = 'publish-log-item';
            row.innerHTML = `
                <div class="log-time">${'$'}{entry.time}</div>
                <div class="log-body">
                    <div class="log-title">${'$'}{entry.title}</div>
                    <div class="log-desc">${'$'}{entry.description}</div>
                </div>
            `;
            container.appendChild(row);
        });
    } catch (error) {
        console.error('加载发布日志失败', error);
        container.innerHTML = '';
        appendEmptyCard(container, '日志加载失败');
    }
}

function debounce(fn, delay) {
    let timer = null;
    return function(...args) {
        clearTimeout(timer);
        timer = setTimeout(() => fn.apply(this, args), delay);
    };
}
            """
        }
    }
}

fun BODY.generateRecipesPageScript() {
    script {
        unsafe {
            +"""
const recipeFilters = {
    search: '',
    category: '全部',
    priority: '',
    tag: '',
    recentOnly: false
};

let allRecipes = [];
let filteredRecipes = [];

document.addEventListener('DOMContentLoaded', () => {
    loadRecipes();
    document.getElementById('recipes-search')?.addEventListener('input', event => {
        recipeFilters.search = event.target.value.trim().toLowerCase();
        applyRecipeFilters();
    });
    document.getElementById('recipes-category')?.addEventListener('change', event => {
        recipeFilters.category = event.target.value;
        applyRecipeFilters();
    });
    document.getElementById('recipes-priority')?.addEventListener('change', event => {
        recipeFilters.priority = event.target.value;
        applyRecipeFilters();
    });
    document.getElementById('recipes-tag')?.addEventListener('input', event => {
        recipeFilters.tag = event.target.value.trim().toLowerCase();
        applyRecipeFilters();
    });
    document.getElementById('recipes-recent')?.addEventListener('change', event => {
        recipeFilters.recentOnly = event.target.checked;
        applyRecipeFilters();
    });
    document.getElementById('recipes-reset')?.addEventListener('click', resetRecipeFilters);
    document.getElementById('recipes-refresh')?.addEventListener('click', loadRecipes);
});

function loadRecipes() {
    const loadingIndicator = document.getElementById('loading-indicator');
    if (loadingIndicator) loadingIndicator.style.display = 'block';
    fetch('/api/recipes')
        .then(res => res.json())
        .then(data => {
            if (data.success) {
                allRecipes = data.data || [];
                applyRecipeFilters();
            } else {
                showNotification('加载配方失败：' + (data.message || '未知错误'), 'error');
            }
        })
        .catch(error => {
            console.error('加载配方失败', error);
            showNotification('网络错误，请检查连接', 'error');
        })
        .finally(() => {
            if (loadingIndicator) loadingIndicator.style.display = 'none';
        });
}

function applyRecipeFilters() {
    filteredRecipes = allRecipes.filter(recipe => {
        const keyword = recipeFilters.search;
        const matchesSearch = !keyword ||
            recipe.name.toLowerCase().includes(keyword) ||
            (recipe.code || '').toLowerCase().includes(keyword) ||
            (recipe.description || '').toLowerCase().includes(keyword) ||
            (recipe.materials || []).some(material => material.name.toLowerCase().includes(keyword));
        const matchesCategory = recipeFilters.category === '全部' || recipe.category === recipeFilters.category;
        const matchesPriority = !recipeFilters.priority || (recipe.priority || 'NORMAL') === recipeFilters.priority;
        const matchesTag = !recipeFilters.tag || (recipe.tags || []).some(tag => tag.toLowerCase().includes(recipeFilters.tag));
        const matchesRecent = !recipeFilters.recentOnly || !!recipe.lastUsed;
        return matchesSearch && matchesCategory && matchesPriority && matchesTag && matchesRecent;
    });
    updateRecipeCount();
    renderRecipes();
}

function resetRecipeFilters() {
    recipeFilters.search = '';
    recipeFilters.category = '全部';
    recipeFilters.priority = '';
    recipeFilters.tag = '';
    recipeFilters.recentOnly = false;
    const searchInput = document.getElementById('recipes-search');
    const categorySelect = document.getElementById('recipes-category');
    const prioritySelect = document.getElementById('recipes-priority');
    const tagInput = document.getElementById('recipes-tag');
    const recentCheckbox = document.getElementById('recipes-recent');
    if (searchInput) searchInput.value = '';
    if (categorySelect) categorySelect.value = '全部';
    if (prioritySelect) prioritySelect.value = '';
    if (tagInput) tagInput.value = '';
    if (recentCheckbox) recentCheckbox.checked = false;
    applyRecipeFilters();
}

function updateRecipeCount() {
    const counter = document.getElementById('recipes-count');
    if (counter) counter.textContent = `${'$'}{filteredRecipes.length} 个配方`;
}

function renderRecipes() {
    const recipesList = document.getElementById('recipes-list');
    if (!recipesList) return;
    if (!filteredRecipes.length) {
        recipesList.innerHTML = `
            <div class="empty-state">
                <h3>暂无配方</h3>
                <p>请使用“导入配方”按钮上传标准模板，或调整筛选条件。</p>
            </div>
        `;
        return;
    }
    recipesList.innerHTML = filteredRecipes.map(recipe => buildRecipeCard(recipe)).join('');
    bindPrefillButtons();
}

function buildRecipeCard(recipe) {
    const tags = (recipe.tags || []).length
        ? (recipe.tags || []).map(tag => `<span class="tag-chip">${'$'}{tag}</span>`).join('')
        : '<span class="tag-chip">未打标签</span>';
    const priorityLevel = (recipe.priority || 'NORMAL').toUpperCase();
    return `
        <div class="recipe-card">
            <div class="recipe-header">
                <div>
                    <div class="recipe-title">${'$'}{recipe.name}</div>
                    <div class="recipe-code">${'$'}{recipe.code || '未编号'}</div>
                </div>
                <span class="priority-badge" data-level="${'$'}{priorityLevel}">${'$'}{renderPriorityText(priorityLevel)}</span>
            </div>
            <div class="recipe-meta">
                <span>${'$'}{recipe.customer || '客户未指定'}</span>
                <span>${'$'}{recipe.salesOwner || '负责人未指定'}</span>
            </div>
            <div class="recipe-info">
                <div>材料：${'$'}{(recipe.materials || []).length} 项</div>
                <div>总量：${'$'}{recipe.totalWeight} g</div>
                <div>最近使用：${'$'}{recipe.lastUsed || '未使用'}</div>
                <div>优先级：${'$'}{renderPriorityText(priorityLevel)}</div>
            </div>
            <div class="recipe-tags">${'$'}{tags}</div>
            <p style="color:#7f8c8d; margin:0.5rem 0 0;">${'$'}{recipe.description || '暂无说明'}</p>
            <div class="recipe-actions">
                <button class="btn btn-secondary prefill-publish-btn" data-recipe-id="${'$'}{recipe.id}">填入快速发布</button>
                <button class="btn btn-info" onclick="viewRecipeDetails('${'$'}{recipe.id}')">查看详情</button>
                <button class="btn btn-primary" onclick="useRecipe('${'$'}{recipe.id}')">标记使用</button>
                <button class="btn btn-outline" onclick="deleteRecipe('${'$'}{recipe.id}')">删除</button>
            </div>
        </div>
    `;
}

function bindPrefillButtons() {
    document.querySelectorAll('.prefill-publish-btn').forEach(button => {
        button.addEventListener('click', () => {
            const recipeId = button.dataset.recipeId;
            const recipe = allRecipes.find(item => item.id === recipeId);
            if (!recipe) return;
            const payload = {
                code: recipe.code || '',
                title: recipe.name,
                priority: (recipe.priority || 'NORMAL').toUpperCase()
            };
            localStorage.setItem('quickPublishRecipe', JSON.stringify(payload));
            window.location.href = '/';
        });
    });
}

function renderPriorityText(priority) {
    switch(priority) {
        case 'URGENT': return '加急';
        case 'HIGH': return '高';
        case 'LOW': return '低';
        default: return '标准';
    }
}

async function viewRecipeDetails(recipeId) {
    try {
        const response = await fetch(`/api/recipes/${'$'}{recipeId}`);
        const data = await response.json();
        if (data.success) {
            showRecipeModal(data.data);
        } else {
            showNotification('获取配方详情失败', 'error');
        }
    } catch (error) {
        console.error('获取配方详情失败', error);
        showNotification('网络错误', 'error');
    }
}

function showRecipeModal(recipe) {
    const modal = document.getElementById('recipe-modal');
    const modalTitle = document.getElementById('modal-title');
    const modalBody = document.getElementById('modal-body');
    modalTitle.textContent = recipe.name;
    const tags = (recipe.tags || []).length ? (recipe.tags || []).join('、') : '未设置';
    modalBody.innerHTML = `
        <div class="modal-detail">
            <div style="display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0.75rem;">
                <div><strong>任务编号：</strong>${'$'}{recipe.code || '未编号'}</div>
                <div><strong>优先级：</strong>${'$'}{renderPriorityText((recipe.priority || 'NORMAL').toUpperCase())}</div>
                <div><strong>客户/项目：</strong>${'$'}{recipe.customer || '未指定'}</div>
                <div><strong>业务负责人：</strong>${'$'}{recipe.salesOwner || '未指定'}</div>
                <div><strong>调香师：</strong>${'$'}{recipe.perfumer || '未指定'}</div>
                <div><strong>标签：</strong>${'$'}{tags}</div>
            </div>
            <p style="margin-top:1rem;"><strong>备注：</strong>${'$'}{recipe.description || '暂无说明'}</p>
        </div>
        <h4 style="margin:1.5rem 0 1rem;">配方材料</h4>
        <div style="overflow-x:auto;">
            <table style="width:100%;border-collapse:collapse;">
                <thead>
                    <tr style="background:#f8f9fa;">
                        <th style="padding:12px;border:1px solid #dee2e6;text-align:left;">序号</th>
                        <th style="padding:12px;border:1px solid #dee2e6;text-align:left;">材料名称</th>
                        <th style="padding:12px;border:1px solid #dee2e6;text-align:left;">编码</th>
                        <th style="padding:12px;border:1px solid #dee2e6;text-align:left;">重量</th>
                        <th style="padding:12px;border:1px solid #dee2e6;text-align:left;">单位</th>
                        <th style="padding:12px;border:1px solid #dee2e6;text-align:left;">备注</th>
                    </tr>
                </thead>
                <tbody>
                    ${'$'}{(recipe.materials || []).map(material => `
                        <tr>
                            <td style="padding:12px;border:1px solid #dee2e6;">${'$'}{material.sequence}</td>
                            <td style="padding:12px;border:1px solid #dee2e6;">${'$'}{material.name}</td>
                            <td style="padding:12px;border:1px solid #dee2e6;">${'$'}{material.code || '-'}</td>
                            <td style="padding:12px;border:1px solid #dee2e6;">${'$'}{material.weight}</td>
                            <td style="padding:12px;border:1px solid #dee2e6;">${'$'}{material.unit}</td>
                            <td style="padding:12px;border:1px solid #dee2e6;">${'$'}{material.notes || '-'}</td>
                        </tr>
                    `).join('')}
                </tbody>
            </table>
        </div>
        <div style="margin-top:1.5rem;display:flex;gap:0.75rem;justify-content:flex-end;">
            <button class="btn btn-secondary" onclick="prefillFromModal('${'$'}{recipe.id}')">填入快速发布</button>
            <button class="btn btn-outline" onclick="closeModal();">关闭</button>
        </div>
    `;
    modal.style.display = 'block';
}

function prefillFromModal(recipeId) {
    const recipe = allRecipes.find(item => item.id === recipeId);
    if (!recipe) return;
    const payload = {
        code: recipe.code || '',
        title: recipe.name,
        priority: (recipe.priority || 'NORMAL').toUpperCase()
    };
    localStorage.setItem('quickPublishRecipe', JSON.stringify(payload));
    window.location.href = '/';
}

function closeModal() {
    document.getElementById('recipe-modal').style.display = 'none';
}

async function useRecipe(recipeId) {
    try {
        const response = await fetch(`/api/recipes/${'$'}{recipeId}/use`, { method: 'POST' });
        const data = await response.json();
        if (data.success) {
            showNotification('已记录使用次数', 'success');
            loadRecipes();
        } else {
            showNotification('标记失败：' + data.message, 'error');
        }
    } catch (error) {
        console.error('标记失败', error);
        showNotification('网络错误', 'error');
    }
}

function editRecipe(recipeId) {
    window.location.href = `/import?edit=${'$'}{recipeId}`;
}

async function deleteRecipe(recipeId) {
    if (!confirm('确定要删除这个配方吗？此操作不可撤销。')) return;
    try {
        const response = await fetch(`/api/recipes/${'$'}{recipeId}`, { method: 'DELETE' });
        const data = await response.json();
        if (data.success) {
            showNotification('配方删除成功', 'success');
            loadRecipes();
        } else {
            showNotification('删除失败：' + data.message, 'error');
        }
    } catch (error) {
        console.error('删除配方失败', error);
        showNotification('网络错误', 'error');
    }
}

function showNotification(message, type = 'info') {
    const notification = document.createElement('div');
    notification.className = `notification notification-${'$'}{type}`;
    notification.textContent = message;
    notification.style.cssText = `
        position: fixed;
        top: 20px;
        right: 20px;
        padding: 15px 25px;
        border-radius: 8px;
        color: white;
        font-weight: 500;
        z-index: 2000;
    `;
    document.body.appendChild(notification);
    setTimeout(() => notification.remove(), 3000);
}
            """
        }
    }
}

fun BODY.generateImportPageScript() {
    script {
        unsafe {
            +"""
// 配方导入页JavaScript
let materialCount = 0;

document.addEventListener('DOMContentLoaded', function() {
    // 初始化一个材料输入项
    addMaterialInput();

    // 绑定事件
    document.getElementById('add-material-btn').addEventListener('click', addMaterialInput);
    document.getElementById('recipe-form').addEventListener('submit', handleFormSubmit);
    document.getElementById('preview-btn').addEventListener('click', previewRecipe);

    // 批量导入按钮
    document.getElementById('json-import-btn').addEventListener('click', () => showBatchImport('json'));
    document.getElementById('csv-import-btn').addEventListener('click', () => showBatchImport('csv'));
    document.getElementById('batch-submit-btn').addEventListener('click', handleBatchImport);
    const fileUploadBtn = document.getElementById('file-upload-btn');
    if (fileUploadBtn) {
        fileUploadBtn.addEventListener('click', uploadFileImport);
    }

    // 检查是否是编辑模式
    const urlParams = new URLSearchParams(window.location.search);
    const editId = urlParams.get('edit');
    if (editId) {
        loadRecipeForEdit(editId);
    }
});

// 添加材料输入项
function addMaterialInput() {
    materialCount++;
    const materialsContainer = document.getElementById('materials-list');

    const materialDiv = document.createElement('div');
    materialDiv.className = 'material-item';
    materialDiv.id = `material-${'$'}{materialCount}`;

    materialDiv.innerHTML = `
        <div class="form-group">
            <label>材料名称 *</label>
            <input type="text" class="form-control" name="material-name" required placeholder="请输入材料名称">
        </div>
        <div class="form-group">
            <label>重量 *</label>
            <input type="number" class="form-control" name="material-weight" required min="0" step="0.1" placeholder="0.0">
        </div>
        <div class="form-group">
            <label>单位</label>
            <select class="form-control" name="material-unit">
                <option value="g">克(g)</option>
                <option value="kg">千克(kg)</option>
                <option value="ml">毫升(ml)</option>
                <option value="l">升(l)</option>
                <option value="个">个</option>
            </select>
        </div>
        <div class="form-group">
            <label>序号</label>
            <input type="number" class="form-control" name="material-sequence" value="${'$'}{materialCount}" min="1">
        </div>
        <div style="display: flex; align-items: end;">
            <button type="button" class="btn btn-outline" onclick="removeMaterialInput('material-${'$'}{materialCount}')">删除</button>
        </div>
    `;

    materialsContainer.appendChild(materialDiv);
}

// 删除材料输入项
function removeMaterialInput(materialId) {
    const materialDiv = document.getElementById(materialId);
    if (materialDiv && document.querySelectorAll('.material-item').length > 1) {
        materialDiv.remove();
    } else {
        showNotification('至少需要保留一个材料', 'warning');
    }
}

// 处理表单提交
async function handleFormSubmit(event) {
    event.preventDefault();

    const submitBtn = document.getElementById('submit-btn');
    submitBtn.textContent = '保存中...';
    submitBtn.disabled = true;

    try {
        const formData = collectFormData();

        if (!validateFormData(formData)) {
            return;
        }

        const response = await fetch('/api/recipes', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(formData)
        });

        const data = await response.json();

        if (data.success) {
            showNotification('配方保存成功', 'success');
            setTimeout(() => {
                window.location.href = '/recipes';
            }, 1500);
        } else {
            showNotification('保存失败: ' + data.message, 'error');
        }
    } catch (error) {
        console.error('保存配方失败:', error);
        showNotification('网络错误，请重试', 'error');
    } finally {
        submitBtn.textContent = '保存配方';
        submitBtn.disabled = false;
    }
}

// 收集表单数据
function collectFormData() {
    const form = document.getElementById('recipe-form');
    const materials = [];

    // 收集材料数据
    document.querySelectorAll('.material-item').forEach(item => {
        const name = item.querySelector('[name="material-name"]').value;
        const weight = parseFloat(item.querySelector('[name="material-weight"]').value);
        const unit = item.querySelector('[name="material-unit"]').value;
        const sequence = parseInt(item.querySelector('[name="material-sequence"]').value);

        if (name && !isNaN(weight)) {
            materials.push({
                name: name.trim(),
                weight: weight,
                unit: unit,
                sequence: sequence,
                notes: ''
            });
        }
    });

    return {
        name: form.querySelector('#recipe-name').value.trim(),
        category: form.querySelector('#recipe-category').value,
        customer: form.querySelector('#recipe-customer').value.trim(),
        batchNo: form.querySelector('#recipe-design-time').value.trim(),
        description: form.querySelector('#recipe-description').value.trim(),
        materials: materials
    };
}

// 验证表单数据
function validateFormData(data) {
    if (!data.name) {
        showNotification('请输入配方名称', 'warning');
        return false;
    }

    if (!data.category) {
        showNotification('请选择配方分类', 'warning');
        return false;
    }

    // 分类仅允许烟油/辅料，确保与导入模板同步
    const allowedCategories = ['烟油', '辅料'];
    if (!allowedCategories.includes(data.category)) {
        showNotification('配方分类仅支持“烟油”或“辅料”', 'warning');
        return false;
    }

    if (!data.customer) {
        showNotification('请输入客户名称', 'warning');
        return false;
    }

    if (!data.batchNo) {
        showNotification('请选择配方设计时间', 'warning');
        return false;
    }

    if (data.materials.length === 0) {
        showNotification('请添加至少一种材料', 'warning');
        return false;
    }

    return true;
}

// 预览配方
function previewRecipe() {
    const formData = collectFormData();

    if (!validateFormData(formData)) {
        return;
    }

    const totalWeight = formData.materials.reduce((sum, material) => sum + material.weight, 0);

    const previewHtml = `
        <div style="background: white; padding: 2rem; border-radius: 15px; box-shadow: 0 5px 20px rgba(0,0,0,0.1); margin-top: 2rem;">
            <h3>配方预览</h3>
            <div style="margin-bottom: 2rem;">
                <div style="display: grid; grid-template-columns: repeat(2, 1fr); gap: 1rem; margin-bottom: 1rem;">
                    <div><strong>配方名称:</strong> ${'$'}{formData.name}</div>
                    <div><strong>分类:</strong> ${'$'}{formData.category}</div>
                    <div><strong>客户:</strong> ${'$'}{formData.customer}</div>
                    <div><strong>设计时间:</strong> ${'$'}{formData.batchNo}</div>
                    <div><strong>总重量:</strong> ${'$'}{totalWeight.toFixed(1)}g</div>
                    <div><strong>材料数量:</strong> ${'$'}{formData.materials.length}</div>
                </div>
                <div style="margin-bottom: 1rem;">
                    <strong>描述:</strong> ${'$'}{formData.description || '暂无描述'}
                </div>
            </div>

            <h4 style="margin-bottom: 1rem;">材料清单</h4>
            <table style="width: 100%; border-collapse: collapse;">
                <thead>
                    <tr style="background: #f8f9fa;">
                        <th style="padding: 12px; border: 1px solid #dee2e6; text-align: left;">序号</th>
                        <th style="padding: 12px; border: 1px solid #dee2e6; text-align: left;">材料名称</th>
                        <th style="padding: 12px; border: 1px solid #dee2e6; text-align: left;">重量</th>
                        <th style="padding: 12px; border: 1px solid #dee2e6; text-align: left;">比例</th>
                    </tr>
                </thead>
                <tbody>
                    ${'$'}{formData.materials
                        .sort((a, b) => a.sequence - b.sequence)
                        .map(material => {
                            const percentage = ((material.weight / totalWeight) * 100).toFixed(1);
                            return `
                                <tr>
                                    <td style="padding: 12px; border: 1px solid #dee2e6;">${'$'}{material.sequence}</td>
                                    <td style="padding: 12px; border: 1px solid #dee2e6;">${'$'}{material.name}</td>
                                    <td style="padding: 12px; border: 1px solid #dee2e6;">${'$'}{material.weight}${'$'}{material.unit}</td>
                                    <td style="padding: 12px; border: 1px solid #dee2e6;">${'$'}{percentage}%</td>
                                </tr>
                            `;
                        }).join('')}
                </tbody>
            </table>

            <div style="margin-top: 2rem; text-align: center;">
                <button class="btn btn-outline" onclick="this.parentElement.parentElement.remove()">关闭预览</button>
            </div>
        </div>
    `;

    // 移除已存在的预览
    const existingPreview = document.querySelector('.recipe-preview');
    if (existingPreview) {
        existingPreview.remove();
    }

    // 添加新预览
    const previewDiv = document.createElement('div');
    previewDiv.className = 'recipe-preview';
    previewDiv.innerHTML = previewHtml;
    document.querySelector('.import-container').appendChild(previewDiv);

    // 滚动到预览位置
    previewDiv.scrollIntoView({ behavior: 'smooth' });
}

// 显示批量导入区域
function showBatchImport(type) {
    const importData = document.getElementById('import-data');
    const batchSubmitBtn = document.getElementById('batch-submit-btn');

    importData.style.display = 'block';
    batchSubmitBtn.style.display = 'block';

    if (type === 'json') {
        importData.placeholder = `JSON格式示例：
[
  {
    "name": "苹果香精配方",
    "category": "香精",
    "description": "经典苹果香味配方",
    "materials": [
      {"name": "苹果香精", "weight": 50, "unit": "g", "sequence": 1},
      {"name": "乙基麦芽酚", "weight": 10, "unit": "g", "sequence": 2}
    ]
  }
]`;
    } else if (type === 'csv') {
        importData.placeholder = `CSV格式示例（每行一个配方，材料用分号分隔）：
配方名称,分类,描述,材料1名称:重量:单位:序号,材料2名称:重量:单位:序号,...
苹果香精配方,香精,经典苹果香味,苹果香精:50:g:1,乙基麦芽酚:10:g:2
柠檬酸配方,酸类,标准柠檬酸调味,柠檬酸:80:g:1,柠檬香精:15:g:2`;
    }

    batchSubmitBtn.setAttribute('data-type', type);
}

// 处理批量导入
async function handleBatchImport() {
    const importData = document.getElementById('import-data');
    const batchSubmitBtn = document.getElementById('batch-submit-btn');
    const type = batchSubmitBtn.getAttribute('data-type');
    const data = importData.value.trim();

    if (!data) {
        showNotification('请输入要导入的数据', 'warning');
        return;
    }

    batchSubmitBtn.textContent = '导入中...';
    batchSubmitBtn.disabled = true;

    try {
        let recipes = [];

        if (type === 'json') {
            recipes = JSON.parse(data);
        } else if (type === 'csv') {
            recipes = parseCSVData(data);
        }

        if (!Array.isArray(recipes)) {
            throw new Error('数据格式错误');
        }

        // 逐个导入配方
        let successCount = 0;
        let errorCount = 0;

        for (const recipe of recipes) {
            try {
                const response = await fetch('/api/recipes', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify(recipe)
                });

                if (response.ok) {
                    successCount++;
                } else {
                    errorCount++;
                }
            } catch (error) {
                errorCount++;
            }
        }

        showNotification(`导入完成！成功: ${'$'}{successCount}，失败: ${'$'}{errorCount}`,
            errorCount === 0 ? 'success' : 'warning');

        if (successCount > 0) {
            setTimeout(() => {
                window.location.href = '/recipes';
            }, 2000);
        }

    } catch (error) {
        console.error('批量导入失败:', error);
        showNotification('数据格式错误，请检查输入', 'error');
    } finally {
        batchSubmitBtn.textContent = '批量导入';
        batchSubmitBtn.disabled = false;
    }
}

// 上传模板文件导入
async function uploadFileImport() {
    const fileInput = document.getElementById('import-file');
    if (!fileInput || fileInput.files.length === 0) {
        showNotification('请先选择模板文件', 'warning');
        return;
    }

    const file = fileInput.files[0];
    const uploadBtn = document.getElementById('file-upload-btn');
    const formData = new FormData();
    formData.append('file', file);

    uploadBtn.textContent = '导入中...';
    uploadBtn.disabled = true;

    try {
        console.log('[Import] 开始上传文件:', file.name, file.size, 'bytes');

        // 添加30秒超时
        const controller = new AbortController();
        const timeoutId = setTimeout(() => {
            controller.abort();
            console.error('[Import] 请求超时');
        }, 30000);

        const response = await fetch('/api/import/recipes', {
            method: 'POST',
            body: formData,
            signal: controller.signal
        });

        clearTimeout(timeoutId);
        console.log('[Import] 收到响应, status:', response.status);

        const data = await response.json();
        console.log('[Import] 解析响应数据:', data);

        if (data.data) {
            renderImportSummary(data.data);
        }

        if (data.success) {
            showNotification(data.message || '导入成功', 'success');
            setTimeout(() => {
                window.location.href = '/recipes';
            }, 2000);
        } else {
            showNotification(data.message || '导入完成但存在错误，请检查结果', data.data && data.data.success > 0 ? 'warning' : 'error');
        }
    } catch (error) {
        console.error('[Import] 文件导入失败:', error);
        if (error.name === 'AbortError') {
            showNotification('上传超时，请检查网络连接或文件大小', 'error');
        } else if (error instanceof TypeError) {
            showNotification('网络错误，请检查服务器是否正常运行', 'error');
        } else {
            showNotification('文件上传失败：' + (error.message || '未知错误'), 'error');
        }
    } finally {
        uploadBtn.textContent = '上传并导入';
        uploadBtn.disabled = false;
        fileInput.value = '';
    }
}

function renderImportSummary(summary) {
    const container = document.getElementById('import-result');
    if (!container) return;
    const errors = Array.isArray(summary.errors) ? summary.errors : [];
    const errorHtml = errors.length
        ? `<div class="import-errors"><p>错误明细：</p><ul>${'$'}{errors.map(err => `<li>${'$'}{escapeHtml(err)}</li>`).join('')}</ul></div>`
        : '';
    container.innerHTML = `
        <div class="import-summary-card">
            <p>总记录：${'$'}{summary.total}，成功：${'$'}{summary.success}，失败：${'$'}{summary.failed}</p>
            ${'$'}{errorHtml}
        </div>
    `;
}

function escapeHtml(value) {
    if (value === null || value === undefined) return '';
    return value.toString()
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

// 解析CSV数据
function parseCSVData(csvData) {
    const lines = csvData.split('\n').filter(line => line.trim());
    const recipes = [];

    for (let i = 1; i < lines.length; i++) { // 跳过标题行
        const line = lines[i].trim();
        if (!line) continue;

        const parts = line.split(',');
        if (parts.length < 4) continue;

        const materials = [];
        for (let j = 3; j < parts.length; j++) {
            const materialData = parts[j].split(':');
            if (materialData.length >= 3) {
                materials.push({
                    name: materialData[0],
                    weight: parseFloat(materialData[1]),
                    unit: materialData[2] || 'g',
                    sequence: parseInt(materialData[3]) || (j - 2),
                    notes: ''
                });
            }
        }

        if (materials.length > 0) {
            recipes.push({
                name: parts[0],
                category: parts[1],
                description: parts[2],
                materials: materials
            });
        }
    }

    return recipes;
}

// 加载配方用于编辑
async function loadRecipeForEdit(recipeId) {
    try {
        const response = await fetch(`/api/recipes/${'$'}{recipeId}`);
        const data = await response.json();

        if (data.success) {
            const recipe = data.data;

            // 填充表单
            document.getElementById('recipe-name').value = recipe.name;
            document.getElementById('recipe-category').value = recipe.category;
            document.getElementById('recipe-customer').value = recipe.customer || '';
            document.getElementById('recipe-design-time').value = recipe.batchNo || '';
            document.getElementById('recipe-description').value = recipe.description;

            // 清空现有材料
            document.getElementById('materials-list').innerHTML = '';
            materialCount = 0;

            // 添加配方材料
            recipe.materials.forEach(material => {
                addMaterialInput();
                const materialDiv = document.querySelector(`#material-${'$'}{materialCount}`);
                materialDiv.querySelector('[name="material-name"]').value = material.name;
                materialDiv.querySelector('[name="material-weight"]').value = material.weight;
                materialDiv.querySelector('[name="material-unit"]').value = material.unit;
                materialDiv.querySelector('[name="material-sequence"]').value = material.sequence;
            });

            // 修改表单提交为更新
            document.getElementById('recipe-form').onsubmit = async function(event) {
                event.preventDefault();
                await updateRecipe(recipeId);
            };

            document.getElementById('submit-btn').textContent = '更新配方';
        }
    } catch (error) {
        console.error('加载配方失败:', error);
        showNotification('加载配方失败', 'error');
    }
}

// 更新配方
async function updateRecipe(recipeId) {
    const submitBtn = document.getElementById('submit-btn');
    submitBtn.textContent = '更新中...';
    submitBtn.disabled = true;

    try {
        const formData = collectFormData();

        if (!validateFormData(formData)) {
            return;
        }

        const response = await fetch(`/api/recipes/${'$'}{recipeId}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(formData)
        });

        const data = await response.json();

        if (data.success) {
            showNotification('配方更新成功', 'success');
            setTimeout(() => {
                window.location.href = '/recipes';
            }, 1500);
        } else {
            showNotification('更新失败: ' + data.message, 'error');
        }
    } catch (error) {
        console.error('更新配方失败:', error);
        showNotification('网络错误，请重试', 'error');
    } finally {
        submitBtn.textContent = '更新配方';
        submitBtn.disabled = false;
    }
}

// 通用通知函数
function showNotification(message, type = 'info') {
    const notification = document.createElement('div');
    notification.className = `notification notification-${'$'}{type}`;
    notification.textContent = message;
    notification.style.cssText = `
        position: fixed;
        top: 20px;
        right: 20px;
        padding: 15px 25px;
        border-radius: 8px;
        color: white;
        font-weight: 500;
        z-index: 10000;
        opacity: 0;
        transform: translateX(100%);
        transition: all 0.3s ease;
    `;

    switch(type) {
        case 'success':
            notification.style.background = 'linear-gradient(135deg, #28a745, #20c997)';
            break;
        case 'error':
            notification.style.background = 'linear-gradient(135deg, #dc3545, #e74c3c)';
            break;
        case 'warning':
            notification.style.background = 'linear-gradient(135deg, #ffc107, #fd7e14)';
            break;
        default:
            notification.style.background = 'linear-gradient(135deg, #17a2b8, #007bff)';
    }

    document.body.appendChild(notification);

    requestAnimationFrame(() => {
        notification.style.opacity = '1';
        notification.style.transform = 'translateX(0)';
    });

    setTimeout(() => {
        notification.style.opacity = '0';
        notification.style.transform = 'translateX(100%)';
        setTimeout(() => notification.remove(), 300);
    }, 3000);
}
            """
        }
    }
}

/**
 * 生成统计页JavaScript
 */
fun BODY.generateStatsPageScript() {
    script {
        unsafe {
            +"""
// 统计页面JavaScript
document.addEventListener('DOMContentLoaded', function() {
    loadStatistics();
});

// 加载统计数据
async function loadStatistics() {
    try {
        const response = await fetch('/api/stats');
        const data = await response.json();

        if (data.success) {
            const stats = data.data;
            renderStatistics(stats);
        } else {
            showNotification('加载统计数据失败', 'error');
        }
    } catch (error) {
        console.error('加载统计数据失败:', error);
        showNotification('网络错误，请检查连接', 'error');
    }
}

// 渲染统计数据
function renderStatistics(stats) {
    // 更新概览统计
    document.getElementById('total-recipes-stat').textContent = stats.totalRecipes;
    document.getElementById('total-categories-stat').textContent = Object.keys(stats.categoryCounts).length;
    document.getElementById('most-used-count').textContent = stats.mostUsed.length > 0 ?
        Math.max(...stats.mostUsed.map(r => r.usageCount)) : 0;
    document.getElementById('recent-usage').textContent = stats.recentlyUsed.length;

    // 渲染分类分布
    renderCategoryDistribution(stats.categoryCounts);

    // 渲染使用频率排行
    renderUsageRanking(stats.mostUsed);

    // 渲染最近使用的配方
    renderRecentRecipes(stats.recentlyUsed);

    // 渲染最受欢迎的配方
    renderPopularRecipes(stats.mostUsed);
}

// 渲染分类分布图
function renderCategoryDistribution(categoryCounts) {
    const container = document.getElementById('category-distribution');

    if (Object.keys(categoryCounts).length === 0) {
        container.innerHTML = '<p style="text-align: center; color: #7f8c8d;">暂无数据</p>';
        return;
    }

    const total = Object.values(categoryCounts).reduce((sum, count) => sum + count, 0);
    const colors = ['#667eea', '#f093fb', '#4facfe', '#fa709a', '#a8edea'];

    container.innerHTML = Object.entries(categoryCounts).map(([category, count], index) => {
        const percentage = ((count / total) * 100).toFixed(1);
        const color = colors[index % colors.length];

        return `
            <div style="display: flex; align-items: center; margin-bottom: 1rem;">
                <div style="width: 20px; height: 20px; background: ${'$'}{color}; border-radius: 4px; margin-right: 1rem;"></div>
                <div style="flex: 1;">
                    <div style="display: flex; justify-content: space-between; margin-bottom: 0.25rem;">
                        <span>${'$'}{category}</span>
                        <span>${'$'}{count} (${'$'}{percentage}%)</span>
                    </div>
                    <div style="background: #e9ecef; height: 8px; border-radius: 4px; overflow: hidden;">
                        <div style="background: ${'$'}{color}; height: 100%; width: ${'$'}{percentage}%; transition: width 0.5s ease;"></div>
                    </div>
                </div>
            </div>
        `;
    }).join('');
}

// 渲染使用频率排行
function renderUsageRanking(mostUsed) {
    const container = document.getElementById('usage-ranking');

    if (mostUsed.length === 0) {
        container.innerHTML = '<p style="text-align: center; color: #7f8c8d;">暂无使用记录</p>';
        return;
    }

    const maxUsage = Math.max(...mostUsed.map(r => r.usageCount));
    const colors = ['#667eea', '#f093fb', '#4facfe', '#fa709a', '#a8edea'];

    container.innerHTML = mostUsed.slice(0, 10).map((recipe, index) => {
        const percentage = maxUsage > 0 ? ((recipe.usageCount / maxUsage) * 100) : 0;
        const color = colors[index % colors.length];

        return `
            <div style="margin-bottom: 1.5rem;">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.5rem;">
                    <span style="font-weight: 500;">${'$'}{index + 1}. ${'$'}{recipe.name}</span>
                    <span style="color: ${'$'}{color}; font-weight: bold;">${'$'}{recipe.usageCount}次</span>
                </div>
                <div style="background: #e9ecef; height: 12px; border-radius: 6px; overflow: hidden;">
                    <div style="background: linear-gradient(90deg, ${'$'}{color}, ${'$'}{color}88); height: 100%; width: ${'$'}{percentage}%; transition: width 0.5s ease;"></div>
                </div>
                <div style="font-size: 0.8rem; color: #7f8c8d; margin-top: 0.25rem;">${'$'}{recipe.category} · 最后使用: ${'$'}{recipe.lastUsed || '未使用'}</div>
            </div>
        `;
    }).join('');
}

// 渲染最近使用的配方
function renderRecentRecipes(recentRecipes) {
    const container = document.getElementById('recent-recipes-list');

    if (recentRecipes.length === 0) {
        container.innerHTML = '<p style="text-align: center; color: #7f8c8d;">暂无最近使用记录</p>';
        return;
    }

    container.innerHTML = recentRecipes.slice(0, 8).map(recipe => `
        <div style="padding: 1rem; border: 1px solid #e9ecef; border-radius: 8px; margin-bottom: 1rem; transition: all 0.3s;"
             onmouseover="this.style.borderColor='#667eea'" onmouseout="this.style.borderColor='#e9ecef'">
            <div style="display: flex; justify-content: space-between; align-items: start; margin-bottom: 0.5rem;">
                <div>
                    <h5 style="margin: 0 0 0.25rem 0; color: #2c3e50;">${'$'}{recipe.name}</h5>
                    <span style="background: #667eea; color: white; padding: 0.2rem 0.6rem; border-radius: 12px; font-size: 0.8rem;">${'$'}{recipe.category}</span>
                </div>
                <span style="font-size: 0.8rem; color: #7f8c8d;">${'$'}{recipe.usageCount}次使用</span>
            </div>
            <div style="font-size: 0.9rem; color: #7f8c8d;">
                最后使用: ${'$'}{recipe.lastUsed} · 总重量: ${'$'}{recipe.totalWeight}g
            </div>
        </div>
    `).join('');
}

// 渲染最受欢迎的配方
function renderPopularRecipes(mostUsed) {
    const container = document.getElementById('popular-recipes-list');

    if (mostUsed.length === 0) {
        container.innerHTML = '<p style="text-align: center; color: #7f8c8d;">暂无使用记录</p>';
        return;
    }

    const medals = ['🥇', '🥈', '🥉'];

    container.innerHTML = mostUsed.slice(0, 8).map((recipe, index) => {
        const medal = index < 3 ? medals[index] : `${'$'}{index + 1}`;

        return `
            <div style="padding: 1rem; border: 1px solid #e9ecef; border-radius: 8px; margin-bottom: 1rem; transition: all 0.3s;"
                 onmouseover="this.style.borderColor='#667eea'" onmouseout="this.style.borderColor='#e9ecef'">
                <div style="display: flex; align-items: center; margin-bottom: 0.5rem;">
                    <span style="font-size: 1.5rem; margin-right: 0.75rem;">${'$'}{medal}</span>
                    <div style="flex: 1;">
                        <h5 style="margin: 0 0 0.25rem 0; color: #2c3e50;">${'$'}{recipe.name}</h5>
                        <span style="background: #f093fb; color: white; padding: 0.2rem 0.6rem; border-radius: 12px; font-size: 0.8rem;">${'$'}{recipe.category}</span>
                    </div>
                    <div style="text-align: right;">
                        <div style="font-weight: bold; color: #667eea;">${'$'}{recipe.usageCount}次</div>
                        <div style="font-size: 0.8rem; color: #7f8c8d;">使用</div>
                    </div>
                </div>
                <div style="font-size: 0.9rem; color: #7f8c8d;">
                    ${'$'}{recipe.materials.length}种材料 · 总重量: ${'$'}{recipe.totalWeight}g
                </div>
            </div>
        `;
    }).join('');
}

// 通用通知函数
function showNotification(message, type = 'info') {
    const notification = document.createElement('div');
    notification.className = `notification notification-${'$'}{type}`;
    notification.textContent = message;
    notification.style.cssText = `
        position: fixed;
        top: 20px;
        right: 20px;
        padding: 15px 25px;
        border-radius: 8px;
        color: white;
        font-weight: 500;
        z-index: 10000;
        opacity: 0;
        transform: translateX(100%);
        transition: all 0.3s ease;
    `;

    switch(type) {
        case 'success':
            notification.style.background = 'linear-gradient(135deg, #28a745, #20c997)';
            break;
        case 'error':
            notification.style.background = 'linear-gradient(135deg, #dc3545, #e74c3c)';
            break;
        case 'warning':
            notification.style.background = 'linear-gradient(135deg, #ffc107, #fd7e14)';
            break;
        default:
            notification.style.background = 'linear-gradient(135deg, #17a2b8, #007bff)';
    }

    document.body.appendChild(notification);

    requestAnimationFrame(() => {
        notification.style.opacity = '1';
        notification.style.transform = 'translateX(0)';
    });

    setTimeout(() => {
        notification.style.opacity = '0';
        notification.style.transform = 'translateX(100%)';
        setTimeout(() => notification.remove(), 300);
    }, 3000);
}
            """
        }
    }
}

/**
 * 生成模板管理页JavaScript
 */
fun BODY.generateTemplatePageScript() {
    script {
        unsafe {
            +"""
// 模板管理页 JavaScript
let templateDefinitions = [];

document.addEventListener('DOMContentLoaded', function() {
    loadTemplates();
    document.getElementById('refresh-templates-btn').addEventListener('click', loadTemplates);
});

async function loadTemplates() {
    const loading = document.getElementById('template-loading');
    if (loading) loading.style.display = 'block';
    try {
        const response = await fetch('/api/templates');
        const data = await response.json();
        if (data.success) {
            templateDefinitions = data.data || [];
            renderTemplates();
        } else {
            showNotification('加载模板失败: ' + data.message, 'error');
        }
    } catch (error) {
        console.error('加载模板失败:', error);
        showNotification('网络错误，无法加载模板', 'error');
    } finally {
        if (loading) loading.style.display = 'none';
    }
}

function renderTemplates() {
    const grid = document.getElementById('template-grid');
    if (!grid) return;

    if (!templateDefinitions.length) {
        grid.innerHTML = `
            <div class="template-card" style="text-align:center;">
                <p>暂无模板定义</p>
            </div>
        `;
        return;
    }

    grid.innerHTML = templateDefinitions.map(template => buildTemplateCard(template)).join('');
}

function buildTemplateCard(template) {
    const formats = (template.supportedFormats || []).join(' / ');
    const rows = (template.fields || []).map(field => buildFieldRow(template.id, field)).join('');
    const safeName = escapeHtml(template.name || '');
    const safeDesc = escapeHtml(template.description || '未添加描述');
    const safeFormats = escapeHtml(formats || 'CSV / EXCEL');
    const safeVersion = escapeHtml(`v${'$'}{template.version} · 更新于 ${'$'}{template.updatedAt}`);

    return `
        <div class="template-card" id="template-${'$'}{template.id}">
            <div class="template-card-header">
                <div>
                    <h3>${'$'}{safeName}</h3>
                    <p style="color:#7f8c8d;">${'$'}{safeDesc}</p>
                    <small style="color:#999;">支持格式：${'$'}{safeFormats}</small>
                </div>
                <span class="template-version">${'$'}{safeVersion}</span>
            </div>
            <div class="template-actions">
                <button class="btn btn-info" onclick="downloadTemplateFile('${'$'}{template.id}', 'csv')">下载CSV模板</button>
                <button class="btn btn-secondary" onclick="downloadTemplateFile('${'$'}{template.id}', 'excel')">下载Excel模板</button>
                <button class="btn btn-primary" onclick="saveTemplate('${'$'}{template.id}')">保存模板</button>
                <button class="btn btn-outline" onclick="resetTemplate('${'$'}{template.id}')">恢复默认</button>
            </div>
            <div class="field-table-wrapper">
                <table class="field-table">
                    <thead>
                        <tr>
                            <th style="width:14%;">字段标识</th>
                            <th style="width:18%;">列标题</th>
                            <th style="width:24%;">字段说明</th>
                            <th style="width:14%;">示例值</th>
                            <th style="width:10%;">必填</th>
                            <th style="width:10%;">操作</th>
                        </tr>
                    </thead>
                    <tbody id="template-${'$'}{template.id}-body">
                        ${'$'}{rows}
                    </tbody>
                </table>
                <div style="margin-top:1rem;">
                    <button class="btn btn-outline" onclick="addFieldRow('${'$'}{template.id}')">+ 添加字段</button>
                </div>
            </div>
        </div>
    `;
}

function buildFieldRow(templateId, field) {
    const safeKey = escapeHtml(field.key || '');
    const safeLabel = escapeHtml(field.label || '');
    const safeDesc = escapeHtml(field.description || '');
    const safeExample = escapeHtml(field.example || '');
    return `
        <tr data-template="${'$'}{templateId}" data-field-id="${'$'}{field.id}" data-field-order="${'$'}{field.order}">
            <td>
                <input class="field-input" data-field="key" value="${'$'}{safeKey}" placeholder="英文字母/下划线">
            </td>
            <td>
                <input class="field-input" data-field="label" value="${'$'}{safeLabel}" placeholder="列标题">
            </td>
            <td>
                <input class="field-input" data-field="description" value="${'$'}{safeDesc}" placeholder="字段说明">
            </td>
            <td>
                <input class="field-input" data-field="example" value="${'$'}{safeExample}" placeholder="示例">
            </td>
            <td style="text-align:center;">
                <input type="checkbox" data-field="required" ${'$'}{field.required ? 'checked' : ''}>
            </td>
            <td>
                <div class="template-field-actions">
                    <button class="btn btn-outline" onclick="moveField(this, -1)">上移</button>
                    <button class="btn btn-outline" onclick="moveField(this, 1)">下移</button>
                    <button class="btn btn-warning" onclick="removeFieldRow(this)">删除</button>
                </div>
            </td>
        </tr>
    `;
}

function addFieldRow(templateId) {
    const tbody = document.getElementById(`template-${'$'}{templateId}-body`);
    if (!tbody) return;
    const tempId = `temp-${'$'}{Date.now()}`;
    const rowHtml = buildFieldRow(templateId, {
        id: tempId,
        key: '',
        label: '',
        description: '',
        example: '',
        required: true,
        order: tbody.children.length + 1
    });
    tbody.insertAdjacentHTML('beforeend', rowHtml);
}

function removeFieldRow(btn) {
    const row = btn.closest('tr');
    if (!row) return;
    const tbody = row.parentElement;
    if (tbody.children.length <= 1) {
        showNotification('至少需要保留一个字段', 'warning');
        return;
    }
    row.remove();
}

function moveField(btn, direction) {
    const row = btn.closest('tr');
    if (!row) return;
    const tbody = row.parentElement;
    const index = Array.from(tbody.children).indexOf(row);
    const targetIndex = index + direction;
    if (targetIndex < 0 || targetIndex >= tbody.children.length) {
        return;
    }
    if (direction < 0) {
        tbody.insertBefore(row, tbody.children[targetIndex]);
    } else {
        tbody.insertBefore(row, tbody.children[targetIndex].nextSibling);
    }
}

async function saveTemplate(templateId) {
    try {
        const payload = collectTemplatePayload(templateId);
        const response = await fetch(`/api/templates/${'$'}{encodeURIComponent(templateId)}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const data = await response.json();
        if (data.success) {
            showNotification('模板保存成功', 'success');
            await loadTemplates();
        } else {
            showNotification('保存失败: ' + data.message, 'error');
        }
    } catch (error) {
        console.error('保存模板失败:', error);
        showNotification('保存失败，请检查输入', 'error');
    }
}

async function resetTemplate(templateId) {
    if (!confirm('确定恢复默认模板吗？自定义内容将被覆盖。')) {
        return;
    }
    try {
        const response = await fetch(`/api/templates/${'$'}{encodeURIComponent(templateId)}/reset`, { method: 'POST' });
        const data = await response.json();
        if (data.success) {
            showNotification('模板已恢复默认', 'success');
            await loadTemplates();
        } else {
            showNotification('恢复失败: ' + data.message, 'error');
        }
    } catch (error) {
        console.error('恢复模板失败:', error);
        showNotification('网络错误', 'error');
    }
}

function collectTemplatePayload(templateId) {
    const rows = document.querySelectorAll(`#template-${'$'}{templateId}-body tr`);
    if (!rows.length) {
        throw new Error('请至少保留一个字段');
    }
    const fields = Array.from(rows).map((row, index) => {
        const keyInput = row.querySelector('[data-field="key"]');
        const labelInput = row.querySelector('[data-field="label"]');
        const descInput = row.querySelector('[data-field="description"]');
        const exampleInput = row.querySelector('[data-field="example"]');
        const requiredInput = row.querySelector('[data-field="required"]');

        const key = keyInput.value.trim();
        const label = labelInput.value.trim();

        if (!key) {
            throw new Error('字段标识不能为空');
        }
        if (!label) {
            throw new Error('列标题不能为空');
        }

        return {
            id: row.dataset.fieldId && !row.dataset.fieldId.startsWith('temp-') ? row.dataset.fieldId : null,
            key,
            label,
            description: descInput.value.trim(),
            example: exampleInput.value.trim(),
            required: requiredInput.checked,
            order: index + 1
        };
    });

    return {
        fields
    };
}

function downloadTemplateFile(templateId, format) {
    const encodedId = encodeURIComponent(templateId);
    const encodedFormat = encodeURIComponent(format);
    window.open(`/api/templates/${'$'}{encodedId}/download?format=${'$'}{encodedFormat}`, '_blank');
}

function escapeHtml(value) {
    if (value === null || value === undefined) return '';
    return value
        .toString()
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function showNotification(message, type = 'info') {
    const notification = document.createElement('div');
    notification.className = `notification notification-${'$'}{type}`;
    notification.textContent = message;
    notification.style.cssText = `
        position: fixed;
        top: 20px;
        right: 20px;
        padding: 15px 25px;
        border-radius: 8px;
        color: white;
        font-weight: 500;
        z-index: 10000;
        opacity: 0;
        transform: translateX(100%);
        transition: all 0.3s ease;
    `;

    switch(type) {
        case 'success':
            notification.style.background = 'linear-gradient(135deg, #28a745, #20c997)';
            break;
        case 'error':
            notification.style.background = 'linear-gradient(135deg, #dc3545, #e74c3c)';
            break;
        case 'warning':
            notification.style.background = 'linear-gradient(135deg, #ffc107, #fd7e14)';
            break;
        default:
            notification.style.background = 'linear-gradient(135deg, #17a2b8, #007bff)';
    }

    document.body.appendChild(notification);
    requestAnimationFrame(() => {
        notification.style.opacity = '1';
        notification.style.transform = 'translateX(0)';
    });

    setTimeout(() => {
        notification.style.opacity = '0';
        notification.style.transform = 'translateX(100%)';
        setTimeout(() => notification.remove(), 300);
    }, 3000);
}
            """
        }
    }
}
