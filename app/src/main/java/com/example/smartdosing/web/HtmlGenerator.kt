package com.example.smartdosing.web

import kotlinx.html.*

/**
 * HTML页面生成器扩展函数
 */

/**
 * 生成主页
 */
fun HTML.generateMainPage() {
    head {
        title("SmartDosing 研发任务调度中心")
        generateCommonHead()
    }
    body {
        generateNavbar()

        div("container") {
            div("hero-section") {
                h1("hero-title") { +"研发任务闭环管理" }
                p("hero-subtitle") { +"从任务发布、设备执行到配置记录，全流程都在同一面板完成" }
                div("hero-actions") {
                    a(href = "/task-center", classes = "btn btn-primary") { +"进入任务中心" }
                    a(href = "/recipes", classes = "btn btn-outline") { +"管理配方资源" }
                }
            }

            div("dashboard-grid") {
                div("dashboard-card") {
                    h3 { +"任务中心" }
                    p { +"查看任务状态、审批草稿、快速发布到设备。" }
                    a(href = "/task-center", classes = "btn btn-primary") { +"打开任务面板" }
                }
                div("dashboard-card") {
                    h3 { +"任务发布" }
                    p { +"选择配方与设备，一键下发研发配置指令。" }
                    button(classes = "btn btn-secondary publish-entry") {
                        id = "open-publish-panel"
                        +"创建任务"
                    }
                }
                div("dashboard-card") {
                    h3 { +"设备面板" }
                    p { +"实时掌握在线设备、当前任务与心跳状态。" }
                    button(classes = "btn btn-info") {
                        id = "refresh-devices-btn"
                        +"刷新设备"
                    }
                }
                div("dashboard-card") {
                    h3 { +"配置记录" }
                    p { +"历史配置全量可查，支持再次配置与纠错。" }
                    a(href = "/stats", classes = "btn btn-outline") { +"进入记录看板" }
                }
            }

            div("task-device-row") {
                div("tasks-panel") {
                    div("panel-header") {
                        h3 { +"今日任务" }
                        button(classes = "btn btn-tertiary") {
                            id = "reload-tasks-btn"
                            +"刷新"
                        }
                    }
                    div("task-summary-grid") {
                        div("task-summary-card") {
                            span("label") { +"待发布" }
                            span("value") { id = "pending-task-count"; +"0" }
                        }
                        div("task-summary-card") {
                            span("label") { +"进行中" }
                            span("value") { id = "running-task-count"; +"0" }
                        }
                        div("task-summary-card") {
                            span("label") { +"今日完成" }
                            span("value") { id = "completed-task-count"; +"0" }
                        }
                    }
                    div("task-list") {
                        id = "today-task-list"
                        div("empty-placeholder") { +"暂无任务，点击“创建任务”以快速下发研发指令。" }
                    }
                }

                    div("device-panel") {
                        div("panel-header") {
                            h3 { +"在线设备" }
                            span("panel-hint") {
                                +"最近心跳 "
                                span {
                                    id = "device-heartbeat-window"
                                    +"--"
                                }
                            }
                        }
                        div("device-list") { id = "device-list" }
                    }
            }

            div("publish-panel") {
                h3 { +"快速发布任务" }
                form {
                    div("form-row") {
                        div("form-group") {
                            label { htmlFor = "publish-title"; +"任务名称" }
                            input(type = InputType.text, classes = "form-control") {
                                id = "publish-title"
                                placeholder = "例如：薄荷雾化调试"
                            }
                        }
                        div("form-group") {
                            label { htmlFor = "publish-priority"; +"优先级" }
                            select("form-control") {
                                id = "publish-priority"
                                option { value = "NORMAL"; +"标准" }
                                option { value = "HIGH"; +"高" }
                                option { value = "URGENT"; +"加急" }
                            }
                        }
                    }
                    div("form-group") {
                        label { htmlFor = "publish-recipe"; +"选择配方" }
                        div("input-with-btn") {
                            input(type = InputType.text, classes = "form-control") {
                                id = "publish-recipe"
                                placeholder = "输入配方编码或名称"
                                attributes["autocomplete"] = "off"
                            }
                            button(type = ButtonType.button, classes = "btn btn-outline") {
                                id = "open-recipe-dialog"
                                +"浏览"
                            }
                        }
                        span("helper-text") { +"配方来自“导入配方”页面，如尚未导入请先下载模板并上传Excel。" }
                        div("recipe-suggestion-panel") {
                            id = "recipe-suggestions"
                        }
                    }
                    div("form-group") {
                        label { htmlFor = "publish-device-id"; +"目标设备" }
                        div("device-target") {
                            span {
                                id = "current-device-name"
                                +"检测当前设备..."
                            }
                            button(type = ButtonType.button, classes = "btn btn-outline") {
                                id = "refresh-device-target"
                                +"重新检测"
                            }
                        }
                        input(type = InputType.hidden) {
                            id = "publish-device-id"
                        }
                        span("helper-text") { +"Web 端与本机设备一对一绑定，无需选择其他设备。" }
                    }
                    div("form-actions") {
                        button(type = ButtonType.button, classes = "btn btn-secondary") {
                            id = "reset-publish"
                            +"重置"
                        }
                        button(type = ButtonType.button, classes = "btn btn-primary") {
                            id = "submit-publish"
                            +"发布任务"
                        }
                    }
                }
            }
        }

        generateCommonFooter()
        generateMainPageScript()
    }
}

fun HTML.generateTaskCenterPage() {
    head {
        title("任务中心 - SmartDosing")
        generateCommonHead()
    }
    body {
        generateNavbar()

        div("container task-center") {
            div("page-header") {
                div {
                    h2 { +"任务中心" }
                    p { +"统一调度研发任务、管理设备执行，实时跟踪工单状态" }
                }
                div("header-actions") {
                    select("status-filter") {
                        id = "task-status-filter"
                        option { value = ""; +"全部状态" }
                        option { value = "DRAFT"; +"草稿" }
                        option { value = "READY"; +"待发布" }
                        option { value = "PUBLISHED"; +"已发布" }
                        option { value = "IN_PROGRESS"; +"执行中" }
                        option { value = "COMPLETED"; +"已完成" }
                    }
                    input(type = InputType.text, classes = "search-input") {
                        id = "task-search-input"
                        placeholder = "搜任务/配方/客户..."
                    }
                    button(classes = "btn btn-primary") {
                        id = "create-task-btn"
                        +"+ 创建任务"
                    }
                }
            }

            div("task-center-layout") {
                div("kanban-board") {
                    div("kanban-column") {
                        h3 { +"待发布" }
                        div("kanban-list") { id = "kanban-ready" }
                    }
                    div("kanban-column") {
                        h3 { +"进行中" }
                        div("kanban-list") { id = "kanban-progress" }
                    }
                    div("kanban-column") {
                        h3 { +"已完成" }
                        div("kanban-list") { id = "kanban-done" }
                    }
                }

                div("task-detail-panel") {
                    h3 { +"任务详情" }
                    div("empty-placeholder") {
                        id = "task-detail-empty"
                        +"选择一条任务查看详细参数与发布日志"
                    }
                    div("task-detail-body") {
                        id = "task-detail-body"
                    }
                }

                div("publish-log-panel") {
                    h3 { +"发布日志" }
                    div("publish-log-list") { id = "publish-log-list" }
                }
            }
        }

        generateCommonFooter()
        generateTaskCenterPageScript()
    }
}

fun HTML.generateRecipesPage() {
    head {
        title("配方管理 - SmartDosing")
        generateCommonHead()
    }
    body {
        generateNavbar()

        div("container recipes-page") {
            div("page-header") {
                div {
                    h2 { +"配方管理" }
                    p { +"查看、筛选和发布研发配方，所有数据均来自最新导入的标准模板" }
                }
                div("header-actions") {
                    a(href = "/api/templates/standard_recipe_template/download?format=excel", classes = "btn btn-outline") {
                        +"下载标准模板"
                    }
                    a(href = "/import", classes = "btn btn-primary") { +"+ 导入配方" }
                }
            }

            div("filter-panel") {
                div("filter-grid") {
                    div("form-group") {
                        label { htmlFor = "recipes-search"; +"关键字" }
                        input(type = InputType.text, classes = "form-control") {
                            id = "recipes-search"
                            placeholder = "搜索名称 / 编码 / 材料..."
                        }
                    }
                    div("form-group") {
                        label { htmlFor = "recipes-category"; +"配方分类" }
                        select("form-control") {
                            id = "recipes-category"
                            option { value = "全部"; +"全部" }
                            option { value = "烟油"; +"烟油" }
                            option { value = "辅料"; +"辅料" }
                        }
                    }
                    div("form-group") {
                        label { htmlFor = "recipes-priority"; +"任务优先级" }
                        select("form-control") {
                            id = "recipes-priority"
                            option { value = ""; +"全部" }
                            option { value = "URGENT"; +"加急" }
                            option { value = "HIGH"; +"高" }
                            option { value = "NORMAL"; +"标准" }
                            option { value = "LOW"; +"低" }
                        }
                    }
                    div("form-group") {
                        label { htmlFor = "recipes-tag"; +"标签" }
                        input(type = InputType.text, classes = "form-control") {
                            id = "recipes-tag"
                            placeholder = "输入标签关键字"
                        }
                    }
                    div("form-group checkbox-group") {
                        label(classes = "checkbox-label") {
                            input(type = InputType.checkBox) {
                                id = "recipes-recent"
                            }
                            +" 仅显示最近使用"
                        }
                    }
                }
                div("filter-actions") {
                    button(type = ButtonType.button, classes = "btn btn-secondary") {
                        id = "recipes-reset"
                        +"重置筛选"
                    }
                    button(type = ButtonType.button, classes = "btn btn-outline") {
                        id = "recipes-refresh"
                        +"刷新数据"
                    }
                }
            }

            div("recipes-container") {
                div("recipes-summary") {
                    span {
                        id = "recipes-count"
                        +"0 个配方"
                    }
                    span("summary-hint") {
                        +"如需新增，请点击“导入配方”或在模板页自定义字段。"
                    }
                }
                div("loading") {
                    id = "loading-indicator"
                    +"正在加载配方..."
                }
                div("recipes-grid") {
                    id = "recipes-list"
                }
            }
        }

        // 配方详情模态框
        generateRecipeModal()

        generateCommonFooter()
        generateRecipesPageScript()
    }
}

/**
 * 生成配方导入页面
 */
fun HTML.generateImportPage() {
    head {
        title("导入配方 - SmartDosing")
        generateCommonHead()
    }
    body {
        generateNavbar()

        div("container") {
            div("page-header") {
                h2 { +"导入配方" }
                p { +"通过官方模板批量导入配方数据，字段与任务中心完全一致" }
            }

            div("import-container") {
                div("import-card") {
                    div("section-header") {
                        h3 { +"手动录入（调试用）" }
                        p { +"仅用于快速校验字段，正式流程请使用模板导入" }
                    }

                    form(classes = "recipe-form") {
                        id = "recipe-form"

                        div("form-group") {
                            label { htmlFor = "recipe-name"; +"配方名称 *" }
                            input(type = InputType.text, classes = "form-control") {
                                id = "recipe-name"
                                required = true
                                placeholder = "请输入配方名称"
                            }
                        }

                        div("form-row") {
                            div("form-group") {
                                label { htmlFor = "recipe-category"; +"配方分类 *" }
                                select("form-control") {
                                    id = "recipe-category"
                                    required = true
                                    option { value = ""; +"请选择分类" }
                                    option { value = "烟油"; +"烟油" }
                                    option { value = "辅料"; +"辅料" }
                                }
                            }
                            div("form-group") {
                                label { htmlFor = "recipe-customer"; +"客户名称 *" }
                                input(type = InputType.text, classes = "form-control") {
                                    id = "recipe-customer"
                                    required = true
                                    placeholder = "请输入客户或项目名称"
                                }
                            }
                        }

                        div("form-row") {
                            div("form-group") {
                                label { htmlFor = "recipe-design-time"; +"配方设计时间 *" }
                                input(type = InputType.date, classes = "form-control") {
                                    id = "recipe-design-time"
                                    required = true
                                }
                            }
                        }

                        div("form-group") {
                            label { htmlFor = "recipe-description"; +"配方说明" }
                            textArea(classes = "form-control") {
                                id = "recipe-description"
                                placeholder = "可记录配方用途、注意事项等"
                                rows = "3"
                            }
                        }

                        div("materials-section") {
                            h4 { +"材料明细" }
                            div("materials-header") {
                                span { +"材料名称" }
                                span { +"材料编码" }
                                span { +"重量" }
                                span { +"单位" }
                                span { +"序号" }
                            }
                            div("materials-list") {
                                id = "materials-list"
                            }
                            button(type = ButtonType.button, classes = "btn btn-outline") {
                                id = "add-material-btn"
                                +"+ 添加材料"
                            }
                        }

                        div("form-actions") {
                            button(type = ButtonType.button, classes = "btn btn-secondary") {
                                id = "preview-btn"
                                +"预览"
                            }
                            button(type = ButtonType.submit, classes = "btn btn-primary") {
                                id = "submit-btn"
                                +"保存配方"
                            }
                            a(href = "/recipes", classes = "btn btn-outline") { +"返回" }
                        }
                    }
                }

                div("import-card") {
                    div("section-header") {
                        h3 { +"批量导入" }
                        p {
                            +"支持粘贴 JSON/CSV 文本或上传 Excel/CSV 模板。模板字段已与任务中心对齐，可在 "
                            a(href = "/templates") { +"模板管理" }
                            +" 页面下载最新版本。"
                        }
                    }

                    div("import-methods") {
                        button(type = ButtonType.button, classes = "btn btn-info") {
                            id = "json-import-btn"
                            +"JSON 导入"
                        }
                        button(type = ButtonType.button, classes = "btn btn-info") {
                            id = "csv-import-btn"
                            +"CSV 导入"
                        }
                    }
                    textArea(classes = "import-textarea") {
                        id = "import-data"
                        placeholder = "在此粘贴 JSON 或 CSV 文本，每行一条记录..."
                        rows = "10"
                        style = "display: none;"
                    }
                    button(type = ButtonType.button, classes = "btn btn-success") {
                        id = "batch-submit-btn"
                        style = "display: none;"
                        +"提交文本导入"
                    }

                    div("file-import") {
                        h5 { +"上传模板文件" }
                        p {
                            +"支持 .xlsx / .csv，推荐使用“标准模板”。Excel 模板可扩展多列，CSV 模板仅保留标准字段。"
                        }
                        input(type = InputType.file, classes = "form-control") {
                            id = "import-file"
                            accept = ".xlsx,.csv"
                        }
                        button(type = ButtonType.button, classes = "btn btn-primary") {
                            id = "file-upload-btn"
                            +"上传并导入"
                        }
                        div {
                            id = "import-result"
                            classes = setOf("import-result")
                        }
                    }
                }
            }
        }

        generateCommonFooter()
        generateImportPageScript()
    }
}
fun HTML.generateStatsPage() {
    head {
        title("统计分析 - SmartDosing")
        generateCommonHead()
    }
    body {
        generateNavbar()

        div("container") {
            div("page-header") {
                h2 { +"统计分析" }
                p { +"配方使用情况和系统统计" }
            }

            div("stats-dashboard") {
                div("stats-overview") {
                    div("stat-card") {
                        h3 { id = "total-recipes-stat"; +"0" }
                        p { +"总配方数量" }
                    }
                    div("stat-card") {
                        h3 { id = "total-categories-stat"; +"0" }
                        p { +"配方分类" }
                    }
                    div("stat-card") {
                        h3 { id = "most-used-count"; +"0" }
                        p { +"最高使用次数" }
                    }
                    div("stat-card") {
                        h3 { id = "recent-usage"; +"0" }
                        p { +"近期使用配方" }
                    }
                }

                div("charts-section") {
                    div("chart-container") {
                        h4 { +"分类分布" }
                        div("category-chart") {
                            id = "category-distribution"
                        }
                    }

                    div("chart-container") {
                        h4 { +"使用频率排行" }
                        div("usage-chart") {
                            id = "usage-ranking"
                        }
                    }
                }

                div("recent-section") {
                    div("recent-recipes") {
                        h4 { +"最近使用的配方" }
                        div("recent-list") {
                            id = "recent-recipes-list"
                        }
                    }

                    div("popular-recipes") {
                        h4 { +"最受欢迎的配方" }
                        div("popular-list") {
                            id = "popular-recipes-list"
                        }
                    }
                }
            }
        }

        generateCommonFooter()
        generateStatsPageScript()
    }
}

/**
 * 生成通用头部
 */
fun HEAD.generateCommonHead() {
    meta { charset = "UTF-8" }
    meta {
        name = "viewport"
        content = "width=device-width, initial-scale=1.0"
    }
    meta {
        name = "description"
        content = "SmartDosing 无线传输服务"
    }

    // 引入样式
    style {
        unsafe {
            +generateCSS()
        }
    }
}

/**
 * 生成导航栏
 */
fun BODY.generateNavbar() {
    nav("navbar") {
        div("nav-container") {
            a(href = "/", classes = "nav-brand") {
                +"SmartDosing"
            }
            ul("nav-menu") {
                li { a(href = "/") { +"首页" } }
                li { a(href = "/task-center") { +"任务中心" } }
                li { a(href = "/recipes") { +"配方管理" } }
                li { a(href = "/import") { +"导入配方" } }
                li { a(href = "/stats") { +"统计分析" } }
                li { a(href = "/templates") { +"模板管理" } }
            }
            div("nav-status") {
                span("status-indicator") {
                    id = "server-status"
                    +"服务器运行中"
                }
            }
        }
    }
}

/**
 * 生成通用页脚
 */
fun BODY.generateCommonFooter() {
    footer("footer") {
        div("footer-content") {
            p { +"© 2024 SmartDosing 智能投料系统. 版权所有." }
            div("footer-links") {
                a(href = "#") { +"关于我们" }
                a(href = "#") { +"技术支持" }
                a(href = "#") { +"使用说明" }
            }
        }
    }
}

/**
 * 生成模板管理页面
 */
fun HTML.generateTemplatePage() {
    head {
        title("模板管理 - SmartDosing")
        generateCommonHead()
    }
    body {
        generateNavbar()

        div("container") {
            div("page-header") {
                div {
                    h2 { +"标准模板管理" }
                    p { +"下载Excel/CSV标准模板，并自定义导入字段。" }
                }
                div("header-actions") {
                    button(classes = "btn btn-info") {
                        id = "refresh-templates-btn"
                        +"刷新"
                    }
                }
            }

            div("template-info-card") {
                h3 { +"模板使用建议" }
                ul {
                    li { +"下载Excel或CSV模板，在第一行保留表头，按顺序填写数据。" }
                    li { +"Excel模板包含“配方信息”和“材料明细”两个工作表，请保持表头名称和顺序不变；CSV模板仍使用单行结构。" }
                    li { +"材料明细表中的“配方编码”用于关联主表数据，请确保与配方信息表中的编码一致。" }
                    li { +"可自定义列标题、示例值以及是否必填，保存后即时生效。" }
                    li { +"如需恢复官方模板，可点击“恢复默认”按钮。" }
                }
            }

            div("template-grid") {
                id = "template-grid"
                div("loading") {
                    id = "template-loading"
                    +"正在加载模板..."
                }
            }
        }

        generateCommonFooter()
        generateTemplatePageScript()
    }
}
