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
        title("SmartDosing 智能投料系统 - 管理后台")
        generateCommonHead()
    }
    body {
        generateNavbar()

        div("container") {
            div("hero-section") {
                h1("hero-title") { +"智能投料系统管理后台" }
                p("hero-subtitle") { +"轻松管理配方，精确控制投料" }
            }

            div("dashboard-grid") {
                div("dashboard-card") {
                    h3 { +"配方管理" }
                    p { +"查看、编辑和管理所有配方" }
                    a(href = "/recipes", classes = "btn btn-primary") { +"进入配方管理" }
                }

                div("dashboard-card") {
                    h3 { +"导入配方" }
                    p { +"批量导入或创建新配方" }
                    a(href = "/import", classes = "btn btn-secondary") { +"导入配方" }
                }

                div("dashboard-card") {
                    h3 { +"统计分析" }
                    p { +"查看配方使用统计和分析" }
                    a(href = "/stats", classes = "btn btn-info") { +"查看统计" }
                }

                div("dashboard-card") {
                    h3 { +"设备状态" }
                    p { +"监控投料设备运行状态" }
                    button(classes = "btn btn-warning") {
                        id = "device-status-btn"
                        +"检查设备"
                    }
                }
            }

            div("quick-stats") {
                h3 { +"快速统计" }
                div("stats-grid") {
                    div("stat-item") {
                        div("stat-number") { id = "total-recipes"; +"0" }
                        div("stat-label") { +"总配方数" }
                    }
                    div("stat-item") {
                        div("stat-number") { id = "recent-used"; +"0" }
                        div("stat-label") { +"今日使用" }
                    }
                    div("stat-item") {
                        div("stat-number") { id = "categories-count"; +"0" }
                        div("stat-label") { +"配方分类" }
                    }
                }
            }
        }

        generateCommonFooter()
        generateMainPageScript()
    }
}

/**
 * 生成配方管理页面
 */
fun HTML.generateRecipesPage() {
    head {
        title("配方管理 - SmartDosing")
        generateCommonHead()
    }
    body {
        generateNavbar()

        div("container") {
            div("page-header") {
                h2 { +"配方管理" }
                div("header-actions") {
                    input(type = InputType.text, classes = "search-input") {
                        id = "search-input"
                        placeholder = "搜索配方..."
                    }
                    select("category-filter") {
                        id = "category-filter"
                        option { value = "全部"; +"全部分类" }
                        option { value = "香精"; +"香精" }
                        option { value = "酸类"; +"酸类" }
                        option { value = "甜味剂"; +"甜味剂" }
                        option { value = "其他"; +"其他" }
                    }
                    a(href = "/import", classes = "btn btn-primary") { +"+ 新建配方" }
                }
            }

            div("recipes-container") {
                div("loading") {
                    id = "loading-indicator"
                    +"正在加载配方..."
                }
                div("recipes-grid") {
                    id = "recipes-list"
                    // 配方列表将通过JavaScript动态填充
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
        title("???? - SmartDosing")
        generateCommonHead()
    }
    body {
        generateNavbar()

        div("container") {
            div("page-header") {
                h2 { +"????" }
                p { +"??????????????" }
            }

            div("import-container") {
                div("import-card") {
                    div("section-header") {
                        h3 { +"??????" }
                        p { +"????????????????????" }
                    }

                    form(classes = "recipe-form") {
                        id = "recipe-form"

                        div("form-group") {
                            label { htmlFor = "recipe-name"; +"???? *" }
                            input(type = InputType.text, classes = "form-control") {
                                id = "recipe-name"
                                required = true
                                placeholder = "???????"
                            }
                        }

                        div("form-row") {
                            div("form-group") {
                                label { htmlFor = "recipe-category"; +"???? *" }
                                select("form-control") {
                                    id = "recipe-category"
                                    required = true
                                    option { value = ""; +"?????" }
                                    option { value = "??"; +"??" }
                                    option { value = "??"; +"??" }
                                    option { value = "???"; +"???" }
                                    option { value = "??"; +"??" }
                                }
                            }
                        }

                        div("form-group") {
                            label { htmlFor = "recipe-description"; +"????" }
                            textArea(classes = "form-control") {
                                id = "recipe-description"
                                placeholder = "???????????"
                                rows = "3"
                            }
                        }

                        div("materials-section") {
                            h4 { +"????" }
                            div("materials-header") {
                                span { +"????" }
                                span { +"??" }
                                span { +"??" }
                                span { +"??" }
                                span { +"??" }
                            }
                            div("materials-list") {
                                id = "materials-list"
                            }
                            button(type = ButtonType.button, classes = "btn btn-outline") {
                                id = "add-material-btn"
                                +"+ ????"
                            }
                        }

                        div("form-actions") {
                            button(type = ButtonType.button, classes = "btn btn-secondary") {
                                id = "preview-btn"
                                +"????"
                            }
                            button(type = ButtonType.submit, classes = "btn btn-primary") {
                                id = "submit-btn"
                                +"????"
                            }
                            a(href = "/recipes", classes = "btn btn-outline") { +"??" }
                        }
                    }
                }

                div("import-card") {
                    div("section-header") {
                        h3 { +"????" }
                        p { +"?? JSON ????CSV ???? Excel/CSV ???????" }
                    }

                    div("import-methods") {
                        button(type = ButtonType.button, classes = "btn btn-info") {
                            id = "json-import-btn"
                            +"JSON????"
                        }
                        button(type = ButtonType.button, classes = "btn btn-info") {
                            id = "csv-import-btn"
                            +"CSV????"
                        }
                    }
                    textArea(classes = "import-textarea") {
                        id = "import-data"
                        placeholder = "??JSON?CSV???????..."
                        rows = "10"
                        style = "display: none;"
                    }
                    button(type = ButtonType.button, classes = "btn btn-success") {
                        id = "batch-submit-btn"
                        style = "display: none;"
                        +"????"
                    }

                    div("file-import") {
                        h5 { +"??????" }
                        p {
                            +"?? .xlsx / .csv ???Excel ?????????????????????????????????CSV ?????????"
                        }
                        input(type = InputType.file, classes = "form-control") {
                            id = "import-file"
                            accept = ".xlsx,.csv"
                        }
                        button(type = ButtonType.button, classes = "btn btn-primary") {
                            id = "file-upload-btn"
                            +"?????"
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
        content = "SmartDosing智能投料系统管理后台"
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
