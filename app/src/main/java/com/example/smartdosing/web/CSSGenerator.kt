package com.example.smartdosing.web

import kotlinx.html.*

/**
 * CSS样式生成
 */
fun generateCSS(): String = """
/* 基础样式重置 */
* {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
}

body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
    line-height: 1.6;
    color: #333;
    background-color: #f8f9fa;
}

/* 容器 */
.container {
    max-width: 1200px;
    margin: 0 auto;
    padding: 20px;
}

/* 导航栏 */
.navbar {
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    padding: 1rem 0;
    box-shadow: 0 2px 10px rgba(0,0,0,0.1);
}

.nav-container {
    max-width: 1200px;
    margin: 0 auto;
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0 20px;
}

.nav-brand {
    font-size: 1.5rem;
    font-weight: bold;
    color: white;
    text-decoration: none;
    transition: opacity 0.3s;
}

.nav-brand:hover {
    opacity: 0.8;
}

.nav-menu {
    display: flex;
    list-style: none;
    gap: 2rem;
}

.nav-menu a {
    color: white;
    text-decoration: none;
    padding: 0.5rem 1rem;
    border-radius: 8px;
    transition: all 0.3s;
}

.nav-menu a:hover {
    background-color: rgba(255,255,255,0.2);
    transform: translateY(-2px);
}

.nav-status {
    color: white;
    font-size: 0.9rem;
}

.status-indicator {
    padding: 0.3rem 0.8rem;
    background-color: rgba(40, 167, 69, 0.8);
    border-radius: 15px;
    font-size: 0.8rem;
}

/* 主页样式 */
.hero-section {
    text-align: center;
    padding: 3rem 0;
    background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
    border-radius: 15px;
    margin-bottom: 3rem;
    color: white;
}

.hero-title {
    font-size: 3rem;
    margin-bottom: 1rem;
    font-weight: 700;
}

.hero-subtitle {
    font-size: 1.2rem;
    opacity: 0.9;
}

.dashboard-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
    gap: 2rem;
    margin-bottom: 3rem;
}

.dashboard-card {
    background: white;
    padding: 2rem;
    border-radius: 15px;
    box-shadow: 0 5px 20px rgba(0,0,0,0.1);
    transition: all 0.3s;
    text-align: center;
}

.dashboard-card:hover {
    transform: translateY(-10px);
    box-shadow: 0 15px 40px rgba(0,0,0,0.15);
}

.dashboard-card h3 {
    margin-bottom: 1rem;
    color: #2c3e50;
    font-size: 1.4rem;
}

.dashboard-card p {
    margin-bottom: 1.5rem;
    color: #7f8c8d;
}

/* 首页任务 + 设备布局 */
.task-device-row {
    display: grid;
    grid-template-columns: 2fr 1fr;
    gap: 1.5rem;
    margin-bottom: 2rem;
}

.tasks-panel,
.device-panel,
.publish-panel {
    background: white;
    border-radius: 16px;
    padding: 1.5rem;
    box-shadow: 0 10px 30px rgba(0,0,0,0.08);
}

.panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 1rem;
}

.task-summary-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
    gap: 1rem;
    margin-bottom: 1rem;
}

.task-summary-card {
    background: #f7f9ff;
    border-radius: 12px;
    padding: 0.75rem 1rem;
}

.task-summary-card .label {
    display: block;
    font-size: 0.85rem;
    color: #6c7a92;
    margin-bottom: 0.25rem;
}

.task-summary-card .value {
    font-size: 1.6rem;
    font-weight: bold;
    color: #4759ff;
}

.task-list {
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
    min-height: 150px;
}

.task-card {
    border: 1px solid #eef1ff;
    border-radius: 12px;
    padding: 0.9rem 1rem;
    display: flex;
    flex-direction: column;
    gap: 0.4rem;
    background: #fff;
    transition: border-color 0.3s;
}

.task-card:hover {
    border-color: #667eea;
}

.task-card .task-title {
    font-weight: 600;
    color: #2f2f41;
}

.task-card .task-meta {
    display: flex;
    justify-content: space-between;
    font-size: 0.85rem;
    color: #7f8c8d;
}

.task-status {
    display: inline-block;
    padding: 0.2rem 0.6rem;
    border-radius: 999px;
    font-size: 0.75rem;
    font-weight: 600;
}

.task-status-ready,
.task-status-draft {
    background: rgba(102, 126, 234, 0.12);
    color: #4c5be7;
}

.task-status-published {
    background: rgba(255, 193, 7, 0.15);
    color: #d08700;
}

.task-status-in_progress {
    background: rgba(76, 175, 80, 0.15);
    color: #2f8a30;
}

.task-status-completed {
    background: rgba(40, 167, 69, 0.15);
    color: #1f8e3a;
}

.task-status-cancelled {
    background: rgba(220, 53, 69, 0.15);
    color: #c53030;
}

.device-list {
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
}

.device-card {
    border: 1px solid #e9ecef;
    border-radius: 12px;
    padding: 1rem;
    display: grid;
    grid-template-columns: 1fr;
    gap: 0.35rem;
    background: #fafbfe;
}

.device-card .device-name {
    font-weight: 600;
    color: #2c3e50;
}

.device-card .device-status {
    font-size: 0.9rem;
    color: #7f8c8d;
}

.device-card .device-task {
    font-size: 0.85rem;
    color: #4c5be7;
}

.device-card .device-heartbeat {
    font-size: 0.8rem;
    color: #95aac9;
}

.device-card.device-online {
    border-color: rgba(76, 175, 80, 0.2);
}

.device-card.device-busy {
    border-color: rgba(255, 193, 7, 0.3);
}

.device-card.device-error {
    border-color: rgba(220, 53, 69, 0.3);
}

.publish-panel h3 {
    margin-bottom: 1rem;
}

.device-target {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0.8rem 1rem;
    border: 1px dashed #dfe4ff;
    border-radius: 12px;
    background: #f9faff;
    gap: 0.75rem;
    color: #2c3e50;
}

.device-target button {
    white-space: nowrap;
}

.device-target span {
    font-weight: 600;
    color: #4c5be7;
}

.input-with-btn {
    display: flex;
    gap: 0.5rem;
}

.input-with-btn input {
    flex: 1;
}

.helper-text {
    display: block;
    font-size: 0.8rem;
    color: #95aac9;
    margin-top: 0.3rem;
}

.recipe-suggestion-panel {
    position: relative;
}

.recipe-suggestion-panel ul {
    position: absolute;
    top: 0.4rem;
    width: 100%;
    background: white;
    border: 1px solid #dfe4ff;
    border-radius: 12px;
    box-shadow: 0 12px 30px rgba(15, 23, 42, 0.12);
    list-style: none;
    padding: 0.5rem 0;
    margin: 0;
    z-index: 100;
    max-height: 240px;
    overflow-y: auto;
}

.recipe-suggestion-panel ul {
    display: none;
}

.recipe-suggestion-panel.active ul {
    display: block;
}

.recipe-suggestion-panel li {
    padding: 0.5rem 1rem;
    display: flex;
    flex-direction: column;
    gap: 0.1rem;
    cursor: pointer;
}

.recipe-suggestion-panel li:hover {
    background: #f1f5ff;
}

.recipe-suggestion-panel .suggest-code {
    font-size: 0.8rem;
    color: #94a3b8;
}

.recipe-suggestion-panel .suggest-name {
    font-weight: 600;
    color: #1f2937;
}

.publish-log-panel {
    margin-top: 1.5rem;
}

.publish-log-list {
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
}

.publish-log-item {
    display: grid;
    grid-template-columns: 80px 1fr;
    gap: 0.75rem;
    padding: 0.75rem 1rem;
    border: 1px solid #eff2fb;
    border-radius: 12px;
    background: #fff;
}

.publish-log-item .log-time {
    font-weight: 600;
    color: #4c5be7;
}

.publish-log-item .log-title {
    font-weight: 600;
    color: #2c3e50;
    margin-bottom: 0.2rem;
}

.publish-log-item .log-desc {
    font-size: 0.9rem;
    color: #7f8c8d;
}

.empty-placeholder {
    padding: 1.5rem;
    text-align: center;
    border: 1px dashed #dfe4ff;
    border-radius: 12px;
    color: #95aac9;
    background: #f8f9ff;
}

.notification {
    position: fixed;
    top: 20px;
    right: 20px;
    background: #2c3e50;
    color: white;
    padding: 0.75rem 1rem;
    border-radius: 10px;
    opacity: 0;
    transform: translateY(-10px);
    transition: all 0.3s;
    z-index: 2000;
}

.notification.active {
    opacity: 1;
    transform: translateY(0);
}

.notification-success { background: #28a745; }
.notification-error { background: #dc3545; }
.notification-warning { background: #ffc107; color: #2c3e50; }

/* 按钮样式 */
.btn {
    display: inline-block;
    padding: 0.75rem 1.5rem;
    border: none;
    border-radius: 8px;
    text-decoration: none;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.3s;
    font-size: 0.95rem;
}

.btn-primary {
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    color: white;
}

.btn-primary:hover {
    transform: translateY(-2px);
    box-shadow: 0 5px 15px rgba(102, 126, 234, 0.4);
}

.btn-secondary {
    background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
    color: white;
}

.btn-secondary:hover {
    transform: translateY(-2px);
    box-shadow: 0 5px 15px rgba(240, 147, 251, 0.4);
}

.btn-info {
    background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
    color: white;
}

.btn-warning {
    background: linear-gradient(135deg, #fa709a 0%, #fee140 100%);
    color: white;
}

.btn-success {
    background: linear-gradient(135deg, #a8edea 0%, #fed6e3 100%);
    color: #333;
}

.btn-outline {
    background: transparent;
    color: #667eea;
    border: 2px solid #667eea;
}

.btn-outline:hover {
    background: #667eea;
    color: white;
}

.btn-link {
    background: transparent;
    border: none;
    color: #667eea;
    cursor: pointer;
    font-weight: 500;
    padding: 0;
}

.btn-link:hover {
    text-decoration: underline;
}

/* 快速统计 */
.quick-stats {
    background: white;
    padding: 2rem;
    border-radius: 15px;
    box-shadow: 0 5px 20px rgba(0,0,0,0.1);
}

.quick-stats h3 {
    margin-bottom: 1.5rem;
    text-align: center;
    color: #2c3e50;
}

.stats-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
    gap: 2rem;
}

.stat-item {
    text-align: center;
}

.stat-number {
    font-size: 2.5rem;
    font-weight: bold;
    color: #667eea;
    margin-bottom: 0.5rem;
}

.stat-label {
    color: #7f8c8d;
    font-size: 0.9rem;
}

/* 页面头部 */
.page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 2rem;
    padding: 2rem;
    background: white;
    border-radius: 15px;
    box-shadow: 0 5px 20px rgba(0,0,0,0.1);
}

.page-header h2 {
    color: #2c3e50;
    font-size: 2rem;
}

.header-actions {
    display: flex;
    gap: 1rem;
    align-items: center;
}

.search-input,
.category-filter,
.form-control {
    padding: 0.75rem;
    border: 2px solid #e9ecef;
    border-radius: 8px;
    font-size: 0.95rem;
    transition: all 0.3s;
}

.search-input:focus,
.category-filter:focus,
.form-control:focus {
    border-color: #667eea;
    outline: none;
    box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

/* 任务中心布局 */
.task-center .page-header {
    background: linear-gradient(120deg, rgba(102,126,234,0.12), rgba(118,75,162,0.12));
}

.task-center .header-actions {
    flex-wrap: wrap;
    justify-content: flex-end;
}

.status-filter {
    padding: 0.65rem 0.9rem;
    border: 2px solid #dfe4ff;
    border-radius: 10px;
    background: white;
}

.task-center-layout {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
    gap: 1.5rem;
    margin-bottom: 3rem;
}

.kanban-board {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
    gap: 1rem;
}

.kanban-column {
    background: white;
    border-radius: 16px;
    padding: 1rem;
    box-shadow: 0 8px 24px rgba(15, 23, 42, 0.08);
    min-height: 320px;
}

.kanban-column h3 {
    margin-bottom: 0.8rem;
    color: #2c3e50;
}

.kanban-list {
    display: flex;
    flex-direction: column;
    gap: 0.8rem;
    min-height: 220px;
}

.kanban-card {
    border: 1px solid #eff2fb;
    border-radius: 12px;
    padding: 0.9rem;
    background: #fdfdff;
    display: flex;
    flex-direction: column;
    gap: 0.4rem;
    box-shadow: 0 1px 4px rgba(0,0,0,0.03);
}

.kanban-card .card-title {
    font-weight: 600;
    color: #2c3e50;
}

.kanban-card .card-meta {
    font-size: 0.9rem;
    color: #7f8c8d;
}

.kanban-card .card-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-top: 0.4rem;
}

.kanban-empty {
    padding: 1rem;
    text-align: center;
    color: #95aac9;
    border: 1px dashed #d5dcff;
    border-radius: 10px;
}

.task-detail-panel,
.publish-log-panel {
    background: white;
    border-radius: 16px;
    padding: 1.25rem;
    box-shadow: 0 10px 30px rgba(15, 23, 42, 0.08);
}

.task-detail-body {
    margin-top: 1rem;
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
}

.detail-row {
    display: flex;
    justify-content: space-between;
    border-bottom: 1px dashed #eef1ff;
    padding-bottom: 0.35rem;
    font-size: 0.95rem;
    color: #2c3e50;
}

.detail-row span:first-child {
    color: #95aac9;
}

.skeleton {
    height: 120px;
    background: linear-gradient(90deg, #f4f6fb 25%, #f0f2ff 37%, #f4f6fb 63%);
    background-size: 400% 100%;
    animation: shimmer 1.4s ease infinite;
    border-radius: 12px;
}

@keyframes shimmer {
    0% { background-position: -468px 0; }
    100% { background-position: 468px 0; }
}

.recipes-page .page-header p {
    color: #6c7a92;
    margin-top: 0.35rem;
}

.filter-panel {
    background: white;
    border-radius: 16px;
    padding: 1.5rem;
    box-shadow: 0 10px 30px rgba(15, 23, 42, 0.08);
    margin-bottom: 1.5rem;
}

.filter-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
    gap: 1rem;
}

.checkbox-group {
    display: flex;
    align-items: flex-end;
}

.checkbox-label {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    font-weight: 500;
    color: #475569;
}

.filter-actions {
    margin-top: 1rem;
    display: flex;
    gap: 0.75rem;
    justify-content: flex-end;
}

.recipes-summary {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 1.5rem;
    color: #475569;
    font-weight: 500;
}

.recipes-summary .summary-hint {
    font-size: 0.9rem;
    color: #94a3b8;
}

/* 配方网格 */
.recipes-container {
    background: white;
    border-radius: 15px;
    padding: 2rem;
    box-shadow: 0 5px 20px rgba(0,0,0,0.1);
}

.recipes-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
    gap: 1.5rem;
}

.recipe-card {
    border: 2px solid #e9ecef;
    border-radius: 12px;
    padding: 1.5rem;
    transition: all 0.3s;
    background: #fff;
}

.recipe-card:hover {
    border-color: #667eea;
    transform: translateY(-5px);
    box-shadow: 0 10px 25px rgba(0,0,0,0.1);
}

.recipe-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    margin-bottom: 1rem;
}

.recipe-title {
    font-size: 1.2rem;
    font-weight: bold;
    color: #2c3e50;
    margin-bottom: 0.5rem;
}

.recipe-category {
    background: #667eea;
    color: white;
    padding: 0.3rem 0.8rem;
    border-radius: 15px;
    font-size: 0.8rem;
}

.recipe-info {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 0.5rem;
    margin-bottom: 1rem;
    font-size: 0.9rem;
    color: #7f8c8d;
}

.recipe-actions {
    display: flex;
    gap: 0.5rem;
    justify-content: flex-end;
}

.recipe-actions .btn {
    font-size: 0.8rem;
    padding: 0.5rem 1rem;
}

.empty-state {
    text-align: center;
    padding: 3rem;
    border: 1px dashed #cbd5f5;
    border-radius: 14px;
    background: #f8faff;
    color: #94a3b8;
}

/* 表单样式 */
.import-container {
    display: flex;
    flex-direction: column;
    gap: 2rem;
}

.import-card {
    background: white;
    padding: 2.5rem;
    border-radius: 20px;
    box-shadow: 0 30px 60px rgba(15, 23, 42, 0.08);
}

.recipe-form {
    padding: 0;
    box-shadow: none;
}

.section-header {
    margin-bottom: 1.5rem;
}

.section-header h3 {
    margin: 0;
    font-size: 1.4rem;
    color: #2f2f41;
}

.section-header p {
    margin: 0.3rem 0 0;
    color: #6c7a92;
}

.form-group {
    margin-bottom: 1.5rem;
}

.form-row {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
    gap: 1rem;
}

.form-group label {
    display: block;
    margin-bottom: 0.5rem;
    color: #2c3e50;
    font-weight: 500;
}

.materials-section {
    margin: 2rem 0;
}

.materials-header {
    display: grid;
    grid-template-columns: 2fr 1fr 1fr 1fr 100px;
    padding: 0.6rem 1rem;
    margin-bottom: 0.5rem;
    background: #f4f6fb;
    border-radius: 10px;
    color: #6c7a92;
    font-size: 0.9rem;
}

.material-item {
    display: grid;
    grid-template-columns: 2fr 1fr 1fr 1fr auto;
    gap: 1rem;
    align-items: end;
    padding: 1rem;
    border: 1px solid #e9ecef;
    border-radius: 12px;
    margin-bottom: 1rem;
    background: #f8f9fa;
}

.form-actions {
    display: flex;
    gap: 1rem;
    justify-content: flex-end;
    margin-top: 2rem;
    padding-top: 2rem;
    border-top: 1px solid #e9ecef;
}

/* 批量导入区域 */
.batch-import-section {
    background: white;
    padding: 2rem;
    border-radius: 15px;
    box-shadow: 0 5px 20px rgba(0,0,0,0.1);
}

.import-methods {
    display: flex;
    gap: 1rem;
    margin-bottom: 1rem;
}

.import-textarea {
    width: 100%;
    padding: 1rem;
    border: 2px solid #e9ecef;
    border-radius: 8px;
    font-family: monospace;
    resize: vertical;
}

.file-import {
    margin-top: 1.5rem;
    padding: 1.5rem;
    border: 1px dashed #c5d3ff;
    border-radius: 12px;
    background: #f8faff;
}

.file-import h5 {
    margin-bottom: 0.5rem;
}

.file-import .form-control {
    margin: 1rem 0;
}

.import-result {
    margin-top: 1rem;
    font-size: 0.95rem;
    color: #2c3e50;
}

.import-summary-card {
    background: white;
    border: 1px solid #e9ecef;
    border-radius: 10px;
    padding: 1rem 1.25rem;
}

.import-result ul {
    margin-top: 0.5rem;
    padding-left: 1.2rem;
    color: #e74c3c;
}

.import-result li {
    line-height: 1.6;
}

/* 统计页面 */
.stats-overview {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
    gap: 1.5rem;
    margin-bottom: 3rem;
}

.stat-card {
    background: white;
    padding: 2rem;
    border-radius: 15px;
    box-shadow: 0 5px 20px rgba(0,0,0,0.1);
    text-align: center;
}

.stat-card h3 {
    font-size: 2.5rem;
    color: #667eea;
    margin-bottom: 0.5rem;
}

.charts-section {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
    gap: 2rem;
    margin-bottom: 3rem;
}

.chart-container {
    background: white;
    padding: 2rem;
    border-radius: 15px;
    box-shadow: 0 5px 20px rgba(0,0,0,0.1);
}

.recent-section {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
    gap: 2rem;
}

.recent-recipes,
.popular-recipes {
    background: white;
    padding: 2rem;
    border-radius: 15px;
    box-shadow: 0 5px 20px rgba(0,0,0,0.1);
}

/* 模板管理 */
.template-info-card {
    background: white;
    padding: 1.5rem 2rem;
    border-radius: 15px;
    box-shadow: 0 5px 20px rgba(0,0,0,0.1);
    margin-bottom: 2rem;
}

.template-info-card ul {
    margin-top: 1rem;
    padding-left: 1.5rem;
    color: #4a4a4a;
}

.template-grid {
    display: flex;
    flex-direction: column;
    gap: 2rem;
}

.template-card {
    background: white;
    padding: 2rem;
    border-radius: 16px;
    box-shadow: 0 8px 24px rgba(0,0,0,0.08);
    border: 1px solid #e9ecef;
    animation: fadeIn 0.4s ease;
}

.template-card-header {
    display: flex;
    justify-content: space-between;
    gap: 1rem;
    margin-bottom: 1.5rem;
}

.template-card-header h3 {
    margin-bottom: 0.25rem;
}

.template-version {
    font-size: 0.85rem;
    color: #7f8c8d;
    white-space: nowrap;
}

.template-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 0.75rem;
    margin-bottom: 1.5rem;
}

.field-table-wrapper {
    overflow-x: auto;
}

.field-table {
    width: 100%;
    border-collapse: collapse;
}

.field-table th,
.field-table td {
    border: 1px solid #e9ecef;
    padding: 0.75rem;
    text-align: left;
    vertical-align: middle;
}

.field-table th {
    background: #f8f9fa;
    font-size: 0.9rem;
    color: #4a4a4a;
}

.field-input {
    width: 100%;
    padding: 0.5rem;
    border: 1px solid #dfe4ea;
    border-radius: 6px;
    font-size: 0.9rem;
}

.field-input:disabled {
    background: #f2f2f2;
    color: #7f8c8d;
}

.template-field-actions {
    display: flex;
    gap: 0.5rem;
}

/* 模态框 */
.modal {
    display: none;
    position: fixed;
    z-index: 1000;
    left: 0;
    top: 0;
    width: 100%;
    height: 100%;
    background-color: rgba(0,0,0,0.5);
}

.modal-content {
    background-color: white;
    margin: 5% auto;
    padding: 2rem;
    border-radius: 15px;
    width: 90%;
    max-width: 800px;
    max-height: 80vh;
    overflow-y: auto;
}

.modal-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 1rem;
    padding-bottom: 1rem;
    border-bottom: 1px solid #e9ecef;
}

.close {
    color: #aaa;
    font-size: 28px;
    font-weight: bold;
    cursor: pointer;
    transition: color 0.3s;
}

.close:hover {
    color: #333;
}

/* 页脚 */
.footer {
    background: #2c3e50;
    color: white;
    padding: 2rem 0;
    margin-top: 4rem;
}

.footer-content {
    max-width: 1200px;
    margin: 0 auto;
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0 20px;
}

.footer-links {
    display: flex;
    gap: 2rem;
}

.footer-links a {
    color: white;
    text-decoration: none;
    opacity: 0.8;
    transition: opacity 0.3s;
}

.footer-links a:hover {
    opacity: 1;
}

/* 响应式设计 */
@media (max-width: 768px) {
    .container {
        padding: 10px;
    }

    .nav-container {
        flex-direction: column;
        gap: 1rem;
    }

    .nav-menu {
        flex-direction: column;
        gap: 0.5rem;
    }

    .page-header {
        flex-direction: column;
        gap: 1rem;
        text-align: center;
    }

    .header-actions {
        flex-wrap: wrap;
        justify-content: center;
    }

    .hero-title {
        font-size: 2rem;
    }

    .dashboard-grid,
    .recipes-grid {
        grid-template-columns: 1fr;
    }

    .task-device-row,
    .task-center-layout,
    .kanban-board {
        grid-template-columns: 1fr;
    }

    .material-item {
        grid-template-columns: 1fr;
        gap: 0.5rem;
    }

    .form-actions {
        flex-direction: column;
    }

    .stats-grid {
        grid-template-columns: 1fr;
    }
}

/* 加载指示器 */
.loading {
    text-align: center;
    padding: 2rem;
    color: #7f8c8d;
    font-style: italic;
}

/* 工具提示 */
.tooltip {
    position: relative;
    display: inline-block;
    cursor: help;
}

.tooltip::after {
    content: attr(data-tooltip);
    position: absolute;
    bottom: 100%;
    left: 50%;
    transform: translateX(-50%);
    background: #333;
    color: white;
    padding: 0.5rem;
    border-radius: 4px;
    font-size: 0.8rem;
    white-space: nowrap;
    opacity: 0;
    visibility: hidden;
    transition: all 0.3s;
}

.tooltip:hover::after {
    opacity: 1;
    visibility: visible;
}

/* 动画 */
@keyframes fadeIn {
    from { opacity: 0; transform: translateY(20px); }
    to { opacity: 1; transform: translateY(0); }
}

.fade-in {
    animation: fadeIn 0.5s ease-out;
}

/* 深色主题支持 */
@media (prefers-color-scheme: dark) {
    body {
        background-color: #1a1a1a;
        color: #e9ecef;
    }

    .dashboard-card,
    .recipe-form,
    .recipes-container,
    .batch-import-section,
    .stat-card,
    .chart-container,
    .recent-recipes,
    .popular-recipes,
    .template-card,
    .template-info-card,
    .modal-content,
    .page-header,
    .quick-stats {
        background: #2c2c2c;
        color: #e9ecef;
    }

    .recipe-card {
        background: #2c2c2c;
        border-color: #4a4a4a;
    }

    .search-input,
    .category-filter,
    .form-control,
    .import-textarea {
        background: #2c2c2c;
        border-color: #4a4a4a;
        color: #e9ecef;
    }

    .material-item {
        background: #1a1a1a;
        border-color: #4a4a4a;
    }

    .field-table th,
    .field-table td {
        border-color: #4a4a4a;
    }

    .field-input {
        background: #1f1f1f;
        color: #e9ecef;
        border-color: #4a4a4a;
    }

    .field-input:disabled {
        background: #2c2c2c;
        color: #a5a5a5;
    }
}
"""

/**
 * 生成配方模态框
 */
fun BODY.generateRecipeModal() {
    div("modal") {
        id = "recipe-modal"
        div("modal-content") {
            div("modal-header") {
                h3 { id = "modal-title"; +"配方详情" }
                span("close") {
                    id = "modal-close"
                    +"\u00d7"
                }
            }
            div("modal-body") {
                id = "modal-body"
                // 内容将通过JavaScript动态填充
            }
        }
    }
}
