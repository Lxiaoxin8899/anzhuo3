package com.example.smartdosing.web

import android.content.Context
import android.util.Log
import com.example.smartdosing.data.*
import com.example.smartdosing.data.repository.ConfigurationRecordPayload
import com.google.gson.Gson
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.html.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Web服务器管理类
 * 负责启动和管理Ktor web服务器
 */
class WebServerManager(private val context: Context) {

    private var server: NettyApplicationEngine? = null
    private val recipeRepository = DatabaseRecipeRepository.getInstance(context)
    private val templateRepository = TemplateRepository.getInstance()
    private val importManager = DatabaseRecipeImportManager.getInstance(context, recipeRepository)
    private val gson = Gson()
    private val taskStore = ConfigurationTaskStore(recipeRepository)
    private val recordStore = ConfigurationRecordStore()
    private val deviceStore = TaskDeviceStore()

    companion object {
        private const val TAG = "WebServerManager"
        private const val DEFAULT_PORT = 8080
    }

    /**
     * 启动web服务器
     */
    fun startServer(port: Int = DEFAULT_PORT): Boolean {
        return try {
            server = embeddedServer(Netty, port = port, host = "0.0.0.0") {
                configureServer()
            }
            server?.start(wait = false)
            Log.i(TAG, "Web服务器启动成功，端口: $port")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Web服务器启动失败", e)
            false
        }
    }

    /**
     * 停止web服务器
     */
    fun stopServer() {
        try {
            server?.stop(1000, 2000)
            server = null
            Log.i(TAG, "Web服务器已停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止web服务器失败", e)
        }
    }

    /**
     * 检查服务器是否运行
     */
    fun isServerRunning(): Boolean {
        return server?.environment?.connectors?.isNotEmpty() == true
    }

    /**
     * 配置Ktor服务器
     */
    private fun Application.configureServer() {
        // 安装插件
        install(ContentNegotiation) {
            gson {
                setPrettyPrinting()
                serializeNulls()
            }
        }

        install(CORS) {
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Delete)
            allowHeader(HttpHeaders.AccessControlAllowHeaders)
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.AccessControlAllowOrigin)
            anyHost()
        }

        install(StatusPages) {
            exception<Throwable> { call, cause ->
                Log.e(TAG, "服务器错误", cause)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<Unit>(
                        success = false,
                        message = "服务器内部错误: ${cause.localizedMessage}"
                    )
                )
            }
        }

        // 配置路由
        routing {
            configureStaticRoutes()
            configureApiRoutes()
        }
    }

    /**
     * 配置静态路由（HTML页面）
     */
    private fun Route.configureStaticRoutes() {
        get("/test-encoding") {
            val testHtml = """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>编码测试页面</title>
    <style>
        body { font-family: Arial, sans-serif; padding: 20px; }
        .test-item { margin: 20px 0; padding: 15px; border: 1px solid #ddd; }
        .success { background: #d4edda; }
        .error { background: #f8d7da; }
    </style>
</head>
<body>
    <h1>SmartDosing 编码测试页面</h1>
    <div class="test-item">
        <h2>测试1: 中文显示测试</h2>
        <p>如果你能看到下面这些字，说明编码正确：</p>
        <p style="font-size: 20px; font-weight: bold;">智能投料系统 - 配方管理 - 数据库集成</p>
        <p>测试字符：中文、English、数字123、符号！@#</p>
    </div>
    <div class="test-item">
        <h2>测试2: 特殊字符测试</h2>
        <p>常用中文：的了是在不我有人这个上们来他要说就那得能好也子知道得自己面前回事过因为多方后对想作种开手行实现长将成老么</p>
    </div>
    <div class="test-item">
        <h2>测试3: 表格测试</h2>
        <table border="1" style="border-collapse: collapse; width: 100%;">
            <tr>
                <th>配方编码</th>
                <th>配方名称</th>
                <th>分类</th>
                <th>状态</th>
            </tr>
            <tr>
                <td>RECIPE001</td>
                <td>苹果香精</td>
                <td>香精</td>
                <td>已启用</td>
            </tr>
        </table>
    </div>
    <div class="test-item">
        <h2>诊断信息</h2>
        <p>请截图这个页面发给开发者</p>
        <ul>
            <li>当前URL: <span id="current-url"></span></li>
            <li>浏览器: <span id="user-agent"></span></li>
            <li>页面编码: <span id="charset"></span></li>
        </ul>
    </div>
    <div class="test-item">
        <a href="/" style="padding: 10px 20px; background: #007bff; color: white; text-decoration: none; border-radius: 5px;">返回首页</a>
    </div>
    <script>
        document.getElementById('current-url').textContent = window.location.href;
        document.getElementById('user-agent').textContent = navigator.userAgent;
        document.getElementById('charset').textContent = document.characterSet || document.charset || '未知';
    </script>
</body>
</html>
            """.trimIndent()

            call.respondText(testHtml, ContentType.Text.Html.withCharset(Charsets.UTF_8))
        }

        suspend fun ApplicationCall.respondHtml(builder: HTML.() -> Unit) {
            val htmlContent = createHTML().html { builder() }
            respondText(htmlContent, ContentType.Text.Html.withCharset(Charsets.UTF_8))
        }

        get("/") { call.respondHtml { generateMainPage() } }
        get("/task-center") { call.respondHtml { generateTaskCenterPage() } }
        get("/recipes") { call.respondHtml { generateRecipesPage() } }
        get("/import") { call.respondHtml { generateImportPage() } }
        get("/stats") { call.respondHtml { generateStatsPage() } }
        get("/templates") { call.respondHtml { generateTemplatePage() } }
    }

    private fun Route.configureApiRoutes() {
        route("/api") {
            // 获取所有配方
            get("/recipes") {
                try {
                    val category = call.request.queryParameters["category"]
                    val search = call.request.queryParameters["search"]

                    val recipes = when {
                        !search.isNullOrBlank() -> recipeRepository.searchRecipes(search)
                        !category.isNullOrBlank() -> recipeRepository.getRecipesByCategory(category)
                        else -> recipeRepository.getAllRecipes()
                    }

                    call.respond(ApiResponse<List<Recipe>>(success = true, data = recipes))
                } catch (e: Exception) {
                    Log.e(TAG, "获取配方列表失败", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<List<Recipe>>(
                            success = false,
                            message = e.localizedMessage ?: "获取配方列表失败"
                        )
                    )
                }
            }

            // 获取单个配方
            get("/recipes/{id}") {
                try {
                    val id = call.parameters["id"] ?: throw IllegalArgumentException("配方ID不能为空")
                    val recipe = recipeRepository.getRecipeById(id)

                    if (recipe != null) {
                        call.respond(ApiResponse<Recipe>(success = true, data = recipe))
                    } else {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse<Recipe>(success = false, message = "配方不存在")
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "获取配方详情失败", e)
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Recipe>(success = false, message = e.message ?: "获取配方详情失败")
                    )
                }
            }

            // 创建新配方
            post("/recipes") {
                try {
                    val request = call.receive<RecipeImportRequest>()
                    val recipe = recipeRepository.addRecipe(request)

                    call.respond(
                        HttpStatusCode.Created,
                        ApiResponse<Recipe>(success = true, message = "配方创建成功", data = recipe)
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "创建配方失败", e)
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Recipe>(success = false, message = e.message ?: "创建配方失败")
                    )
                }
            }

            // 更新配方
            put("/recipes/{id}") {
                try {
                    val id = call.parameters["id"] ?: throw IllegalArgumentException("配方ID不能为空")
                    val request = call.receive<RecipeImportRequest>()
                    val recipe = recipeRepository.updateRecipe(id, request)

                    if (recipe != null) {
                        call.respond(ApiResponse<Recipe>(success = true, message = "配方更新成功", data = recipe))
                    } else {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse<Recipe>(success = false, message = "配方不存在")
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "更新配方失败", e)
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Recipe>(success = false, message = e.message ?: "更新配方失败")
                    )
                }
            }

            // 删除配方
            delete("/recipes/{id}") {
                try {
                    val id = call.parameters["id"] ?: throw IllegalArgumentException("配方ID不能为空")
                    val success = recipeRepository.deleteRecipe(id)

                    if (success) {
                        call.respond(ApiResponse<Unit>(success = true, message = "配方删除成功"))
                    } else {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse<Unit>(success = false, message = "配方不存在")
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "删除配方失败", e)
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(success = false, message = e.message ?: "删除配方失败")
                    )
                }
            }

            // 标记配方被使用
            post("/recipes/{id}/use") {
                try {
                    val id = call.parameters["id"] ?: throw IllegalArgumentException("配方ID不能为空")
                    val recipe = recipeRepository.markRecipeUsed(id)

                    if (recipe != null) {
                        call.respond(ApiResponse<Recipe>(success = true, message = "配方使用记录更新成功", data = recipe))
                    } else {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse<Recipe>(success = false, message = "配方不存在")
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "更新配方使用记录失败", e)
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Recipe>(success = false, message = e.message ?: "更新配方使用记录失败")
                    )
                }
            }

            // 获取配方统计
            get("/stats") {
                try {
                    val stats = recipeRepository.getRecipeStats()
                    call.respond(ApiResponse<RecipeStats>(success = true, data = stats))
                } catch (e: Exception) {
                    Log.e(TAG, "获取统计信息失败", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<RecipeStats>(success = false, message = "获取统计信息失败")
                    )
                }
            }

            // 模板管理
            get("/templates") {
                try {
                    val templates = templateRepository.getTemplates()
                    call.respond(ApiResponse<List<TemplateDefinition>>(success = true, data = templates))
                } catch (e: Exception) {
                    Log.e(TAG, "获取模板列表失败", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<List<TemplateDefinition>>(success = false, message = "获取模板列表失败")
                    )
                }
            }

            get("/templates/{id}") {
                try {
                    val id = call.parameters["id"] ?: throw IllegalArgumentException("模板ID不能为空")
                    val template = templateRepository.getTemplateById(id)
                        ?: return@get call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse<TemplateDefinition>(success = false, message = "模板不存在")
                        )
                    call.respond(ApiResponse(success = true, data = template))
                } catch (e: Exception) {
                    Log.e(TAG, "获取模板失败", e)
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<TemplateDefinition>(success = false, message = e.message ?: "获取模板失败")
                    )
                }
            }

            put("/templates/{id}") {
                try {
                    val id = call.parameters["id"] ?: throw IllegalArgumentException("模板ID不能为空")
                    val request = call.receive<TemplateUpdateRequest>()
                    val updated = templateRepository.updateTemplate(id, request)
                        ?: return@put call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse<TemplateDefinition>(success = false, message = "模板不存在")
                        )
                    call.respond(ApiResponse(success = true, message = "模板更新成功", data = updated))
                } catch (e: Exception) {
                    Log.e(TAG, "更新模板失败", e)
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<TemplateDefinition>(success = false, message = e.message ?: "更新模板失败")
                    )
                }
            }

            post("/templates/{id}/reset") {
                try {
                    val id = call.parameters["id"] ?: throw IllegalArgumentException("模板ID不能为空")
                    val template = templateRepository.resetTemplate(id)
                        ?: return@post call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse<TemplateDefinition>(success = false, message = "模板不存在")
                        )
                    call.respond(ApiResponse(success = true, message = "模板已重置", data = template))
                } catch (e: Exception) {
                    Log.e(TAG, "重置模板失败", e)
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<TemplateDefinition>(success = false, message = e.message ?: "重置模板失败")
                    )
                }
            }

            get("/templates/{id}/download") {
                try {
                    val id = call.parameters["id"] ?: throw IllegalArgumentException("模板ID不能为空")
                    val format = call.request.queryParameters["format"]?.lowercase(Locale.getDefault()) ?: "csv"
                    when (format) {
                        "csv" -> {
                            val result = templateRepository.generateCsvTemplate(id)
                                ?: return@get call.respond(
                                    HttpStatusCode.NotFound,
                                    ApiResponse<Unit>(success = false, message = "模板不存在")
                                )
                            val (fileName, bytes) = result
                            call.response.header(
                                HttpHeaders.ContentDisposition,
                                ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, fileName)
                                    .toString()
                            )
                            call.respondBytes(bytes, ContentType.Text.CSV)
                        }

                        "excel", "xlsx" -> {
                            val result = templateRepository.generateExcelTemplate(id)
                                ?: return@get call.respond(
                                    HttpStatusCode.NotFound,
                                    ApiResponse<Unit>(success = false, message = "模板不存在")
                                )
                            val (fileName, bytes) = result
                            call.response.header(
                                HttpHeaders.ContentDisposition,
                                ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, fileName)
                                    .toString()
                            )
                            call.respondBytes(
                                bytes,
                                ContentType.parse("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                            )
                        }

                        else -> {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                ApiResponse<Unit>(success = false, message = "不支持的格式")
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "下载模板失败", e)
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(success = false, message = e.message ?: "下载模板失败")
                    )
                }
            }

            // 研发任务
            route("/tasks") {
                // 任务列表：支持状态筛选与关键字搜索
                get {
                    val statusParam = call.request.queryParameters["status"]
                    val status = statusParam
                        ?.takeIf { it.isNotBlank() }
                        ?.uppercase(Locale.getDefault())
                        ?.let { runCatching { TaskStatus.valueOf(it) }.getOrNull() }
                    val search = call.request.queryParameters["search"]

                    val tasks = taskStore.getTasks(status, search)
                    call.respond(ApiResponse(success = true, data = tasks))
                }

                // 仪表盘概览（含设备与发布日志）
                get("/overview") {
                    val devices = deviceStore.listDevices()
                    val heartbeatWindow = deviceStore.heartbeatWindow()
                    val overview = taskStore.buildOverview(devices, heartbeatWindow)
                    call.respond(ApiResponse(success = true, data = overview))
                }

                // 快捷发布新任务
                post("/quick-publish") {
                    try {
                        val request = call.receive<QuickPublishRequest>()
                        if (request.title.isBlank()) {
                            return@post call.respond(
                                HttpStatusCode.BadRequest,
                                ApiResponse<Unit>(success = false, message = "任务名称不能为空")
                            )
                        }
                        if (request.recipeKeyword.isBlank()) {
                            return@post call.respond(
                                HttpStatusCode.BadRequest,
                                ApiResponse<Unit>(success = false, message = "请提供配方关键词")
                            )
                        }
                        val recipeKeyword = request.recipeKeyword.trim()
                        val recipe = recipeRepository.getRecipeById(recipeKeyword)
                            ?: recipeRepository.getRecipeByCode(recipeKeyword)
                            ?: recipeRepository.searchRecipes(recipeKeyword).firstOrNull()
                            ?: return@post call.respond(
                                HttpStatusCode.NotFound,
                                ApiResponse<Unit>(success = false, message = "未找到匹配的配方")
                            )

                        val manualDeviceIds = when {
                            !request.deviceId.isNullOrBlank() -> listOf(request.deviceId)
                            request.deviceIds.isNotEmpty() -> request.deviceIds
                            else -> deviceStore.primaryDeviceId()?.let { listOf(it) } ?: emptyList()
                        }
                        if (manualDeviceIds.isEmpty()) {
                            return@post call.respond(
                                HttpStatusCode.BadRequest,
                                ApiResponse<Unit>(success = false, message = "当前设备不可用，请检查本机服务")
                            )
                        }

                        val deviceNames = deviceStore.resolveDeviceNames(manualDeviceIds)
                        if (deviceNames.isEmpty()) {
                            return@post call.respond(
                                HttpStatusCode.BadRequest,
                                ApiResponse<Unit>(success = false, message = "未匹配到可用设备")
                            )
                        }

                        val operator = "Web任务中心"
                        val task = taskStore.createQuickTask(
                            recipe = recipe,
                            title = request.title,
                            priority = request.priority,
                            targetDevices = deviceNames,
                            operator = operator
                        )
                        deviceStore.markDevicesForTask(manualDeviceIds, task)
                        taskStore.recordPublishLog(
                            title = task.title.ifBlank { task.recipeName },
                            description = "推送至 ${deviceNames.joinToString()}",
                            operator = operator,
                            status = PublishResultStatus.SUCCEEDED
                        )

                        call.respond(
                            HttpStatusCode.Created,
                            ApiResponse(success = true, message = "任务发布成功", data = task)
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "快速发布任务失败", e)
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse<Unit>(success = false, message = e.message ?: "发布失败")
                        )
                    }
                }

                get("/{id}") {
                    val id = call.parameters["id"] ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<ConfigurationTask>(success = false, message = "任务ID不能为空")
                    )
                    val task = taskStore.getTask(id)
                        ?: return@get call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse<ConfigurationTask>(success = false, message = "任务不存在")
                        )
                    call.respond(ApiResponse(success = true, data = task))
                }

                patch("/{id}") {
                    try {
                        val id = call.parameters["id"] ?: throw IllegalArgumentException("任务ID不能为空")
                        val body = call.receive<TaskStatusUpdateRequest>()
                        val updated = taskStore.updateStatus(id, body.status)
                            ?: return@patch call.respond(
                                HttpStatusCode.NotFound,
                                ApiResponse<ConfigurationTask>(success = false, message = "任务不存在")
                            )
                        call.respond(ApiResponse(success = true, message = "任务更新成功", data = updated))
                    } catch (e: Exception) {
                        Log.e(TAG, "更新任务状态失败", e)
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse<ConfigurationTask>(success = false, message = e.message ?: "更新任务失败")
                        )
                    }
                }

            }

            // 发布日志历史
            get("/publish-log") {
                val logs = taskStore.getPublishLogs()
                call.respond(ApiResponse(success = true, data = logs))
            }

            // 配置记录
            route("/configuration-records") {
                get {
                    val filter = ConfigurationRecordFilterQuery.fromQuery(
                        customer = call.request.queryParameters["customer"],
                        salesOwner = call.request.queryParameters["salesOwner"],
                        operator = call.request.queryParameters["operator"],
                        status = call.request.queryParameters["status"],
                        sort = call.request.queryParameters["sort"] ?: "desc",
                        limit = call.request.queryParameters["limit"]?.toIntOrNull(),
                        offset = call.request.queryParameters["offset"]?.toIntOrNull()
                    )
                    val records = recordStore.getRecords(filter)
                    call.respond(ApiResponse(success = true, data = records))
                }

                get("/{id}") {
                    val id = call.parameters["id"] ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<ConfigurationRecord>(success = false, message = "记录ID不能为空")
                    )
                    val record = recordStore.getRecord(id)
                        ?: return@get call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse<ConfigurationRecord>(success = false, message = "记录不存在")
                        )
                    call.respond(ApiResponse(success = true, data = record))
                }

                post {
                    try {
                        val payload = call.receive<ConfigurationRecordPayload>()
                        val record = recordStore.createRecord(payload)
                        val relatedTaskId = payload.taskId.takeIf { it.isNotBlank() }
                        if (relatedTaskId != null) {
                            taskStore.updateStatus(relatedTaskId, TaskStatus.COMPLETED)
                        }
                        call.respond(
                            HttpStatusCode.Created,
                            ApiResponse(success = true, message = "配置记录已创建", data = record)
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "创建配置记录失败", e)
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse<ConfigurationRecord>(success = false, message = e.message ?: "创建失败")
                        )
                    }
                }

                patch("/{id}/status") {
                    try {
                        val id = call.parameters["id"] ?: throw IllegalArgumentException("记录ID不能为空")
                        val request = call.receive<RecordStatusUpdateRequest>()
                        val updated = recordStore.updateStatus(id, request.status, request.note)
                            ?: return@patch call.respond(
                                HttpStatusCode.NotFound,
                                ApiResponse<ConfigurationRecord>(success = false, message = "记录不存在")
                            )
                        call.respond(ApiResponse(success = true, message = "记录状态已更新", data = updated))
                    } catch (e: Exception) {
                        Log.e(TAG, "更新配置记录状态失败", e)
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse<ConfigurationRecord>(success = false, message = e.message ?: "更新失败")
                        )
                    }
                }
            }

            post("/import/recipes") {
                try {
                    Log.i(TAG, "[Import] 开始接收文件上传请求")
                    val multipart = call.receiveMultipart()
                    var fileBytes: ByteArray? = null
                    var fileName: String? = null

                    multipart.forEachPart { part ->
                        when (part) {
                            is PartData.FileItem -> {
                                fileName = part.originalFileName
                                Log.i(TAG, "[Import] 接收文件: $fileName")
                                fileBytes = part.streamProvider().readBytes()
                                Log.i(TAG, "[Import] 文件大小: ${fileBytes?.size} bytes")
                            }
                            else -> Unit
                        }
                        part.dispose()
                    }

                    val bytes = fileBytes ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<ImportSummary>(success = false, message = "请上传CSV或Excel文件")
                    )

                    val lowerName = fileName?.lowercase(Locale.getDefault()).orEmpty()
                    Log.i(TAG, "[Import] 开始解析文件类型: $lowerName")

                    val summary = when {
                        lowerName.endsWith(".xlsx") -> {
                            Log.i(TAG, "[Import] 使用Excel导入")
                            importManager.importExcel(bytes)
                        }
                        lowerName.endsWith(".csv") -> {
                            Log.i(TAG, "[Import] 使用CSV导入")
                            importManager.importCsvFile(bytes)
                        }
                        else -> return@post call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse<ImportSummary>(success = false, message = "仅支持CSV或Excel模板文件")
                        )
                    }

                    Log.i(TAG, "[Import] 导入完成: 总数=${summary.total}, 成功=${summary.success}, 失败=${summary.failed}")
                    if (summary.errors.isNotEmpty()) {
                        Log.w(TAG, "[Import] 错误列表: ${summary.errors.joinToString("; ")}")
                    }

                    val detailHint = if (summary.errors.isNotEmpty()) "，请查看错误详情" else ""
                    val message = when {
                        summary.success > 0 && summary.failed == 0 -> "成功导入${summary.success}条配方$detailHint"
                        summary.success > 0 && summary.failed > 0 -> "成功导入${summary.success}条，${summary.failed}条失败$detailHint"
                        else -> "未导入任何配方，请检查模板内容$detailHint"
                    }

                    call.respond(
                        ApiResponse(success = summary.success > 0 && summary.failed == 0, message = message, data = summary)
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "[Import] 文件导入失败", e)
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<ImportSummary>(success = false, message = e.message ?: "导入失败")
                    )
                }
            }
        }
    }
}

/**
 * 研发任务内存仓库：负责任务列表、快速发布及日志统计
 */
private class ConfigurationTaskStore(
    private val recipeRepository: DatabaseRecipeRepository
) {
    private val mutex = Mutex()
    private val tasks = mutableListOf<ConfigurationTask>()
    private val publishLogs = TaskPublishLogSampleData.recent().toMutableList()
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dbTaskPrefix = "TASK-DB-"

    suspend fun getTasks(status: TaskStatus?, search: String?): List<ConfigurationTask> = mutex.withLock {
        syncDatabaseTasks()
        var result = tasks.toList()
        status?.let { target -> result = result.filter { it.status == target } }
        search?.takeIf { it.isNotBlank() }?.let { keyword ->
            val query = keyword.lowercase(Locale.getDefault())
            result = result.filter { task ->
                listOf(
                    task.title,
                    task.recipeName,
                    task.recipeCode,
                    task.customer,
                    task.salesOwner,
                    task.requestedBy,
                    task.perfumer
                ).any { it.contains(query, ignoreCase = true) }
            }
        }
        result
    }

    suspend fun getTask(taskId: String): ConfigurationTask? = mutex.withLock {
        syncDatabaseTasks()
        tasks.find { it.id == taskId }
    }

    suspend fun updateStatus(taskId: String, status: TaskStatus): ConfigurationTask? = mutex.withLock {
        val index = tasks.indexOfFirst { it.id == taskId }
        if (index < 0) return@withLock null
        val now = timestampFormat.format(Date())
        val updated = tasks[index].copy(
            status = status,
            statusUpdatedAt = now
        )
        tasks[index] = updated
        updated
    }

    private suspend fun syncDatabaseTasks() {
        val recipes = recipeRepository.getAllRecipes()
        val dbIds = recipes.map { dbTaskPrefix + it.id }.toSet()
        tasks.removeAll { it.id.startsWith(dbTaskPrefix) && it.id !in dbIds }
        recipes.forEach { recipe ->
            val taskId = dbTaskPrefix + recipe.id
            val newTask = recipe.toTask(taskId)
            val index = tasks.indexOfFirst { it.id == taskId }
            if (index >= 0) {
                tasks[index] = newTask
            } else {
                tasks.add(newTask)
            }
        }
    }

    suspend fun createQuickTask(
        recipe: Recipe,
        title: String,
        priority: TaskPriority,
        targetDevices: List<String>,
        operator: String
    ): ConfigurationTask = mutex.withLock {
        val now = timestampFormat.format(Date())
        val displayTitle = title.ifBlank { recipe.name }
        val quantity = if (recipe.totalWeight > 0) recipe.totalWeight else recipe.materials.sumOf { it.weight }
        val unit = recipe.materials.firstOrNull()?.unit ?: "g"
        val task = ConfigurationTask(
            id = "TASK-${System.currentTimeMillis()}",
            title = displayTitle,
            recipeId = recipe.id,
            recipeName = recipe.name,
            recipeCode = recipe.code,
            quantity = quantity,
            unit = unit,
            priority = priority,
            requestedBy = operator,
            perfumer = recipe.perfumer,
            customer = recipe.customer,
            salesOwner = recipe.salesOwner.ifBlank { operator },
            status = TaskStatus.PUBLISHED,
            deadline = recipe.batchNo.takeIf { it.isNotBlank() } ?: "本周排程",
            createdAt = now,
            publishedAt = now,
            statusUpdatedAt = now,
            targetDevices = targetDevices,
            note = recipe.description,
            tags = if (recipe.tags.isNotEmpty()) recipe.tags.take(3) else listOf("快速发布")
        )
        tasks.add(0, task)
        task
    }

    suspend fun recordPublishLog(
        title: String,
        description: String,
        operator: String,
        status: PublishResultStatus
    ) = mutex.withLock {
        val entry = TaskPublishLogEntry(
            id = "LOG-${System.currentTimeMillis()}",
            time = clockFormat.format(Date()),
            title = title,
            description = description,
            operator = operator,
            status = status
        )
        publishLogs.add(0, entry)
        if (publishLogs.size > 30) {
            publishLogs.removeAt(publishLogs.lastIndex)
        }
    }

    suspend fun getPublishLogs(limit: Int? = null): List<TaskPublishLogEntry> = mutex.withLock {
        val snapshot = publishLogs.toList()
        limit?.let { return@withLock snapshot.take(it) }
        snapshot
    }

    suspend fun buildOverview(devices: List<TaskDeviceInfo>, heartbeatWindow: String): TaskOverviewSnapshot =
        mutex.withLock {
            val pendingStatuses = setOf(TaskStatus.DRAFT, TaskStatus.READY)
            val runningStatuses = setOf(TaskStatus.PUBLISHED, TaskStatus.IN_PROGRESS)
            val pending = tasks.count { it.status in pendingStatuses }
            val running = tasks.count { it.status in runningStatuses }
            val completed = tasks.count { it.status == TaskStatus.COMPLETED }
            TaskOverviewSnapshot(
                pendingCount = pending,
                runningCount = running,
                completedToday = completed,
                heartbeatWindow = heartbeatWindow,
                tasks = tasks.take(6),
                devices = devices,
                publishLog = publishLogs.take(6)
            )
        }
}

private fun Recipe.toTask(taskId: String): ConfigurationTask {
    val taskPriority = when (priority) {
        RecipePriority.URGENT -> TaskPriority.URGENT
        RecipePriority.HIGH -> TaskPriority.HIGH
        RecipePriority.LOW -> TaskPriority.LOW
        else -> TaskPriority.NORMAL
    }
    return ConfigurationTask(
        id = taskId,
        title = name,
        recipeId = id,
        recipeName = name,
        recipeCode = code,
        quantity = totalWeight,
        unit = "g",
        priority = taskPriority,
        requestedBy = perfumer,
        perfumer = perfumer,
        customer = customer,
        salesOwner = salesOwner,
        status = TaskStatus.READY,
        deadline = batchNo.ifBlank { "待安排" },
        createdAt = createTime,
        statusUpdatedAt = updateTime,
        targetDevices = listOf("本机投料设备"),
        note = description,
        tags = if (tags.isEmpty()) listOf("标准模板") else tags
    )
}

/**
 * 设备状态仓库：模拟设备心跳与任务占用
 */
private class TaskDeviceStore {
    private val mutex = Mutex()
    private val devices = TaskDeviceSampleData.devices().toMutableList()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    suspend fun listDevices(): List<TaskDeviceInfo> = mutex.withLock { devices.toList() }

    suspend fun primaryDeviceId(): String? = mutex.withLock { devices.firstOrNull()?.id }

    suspend fun resolveDeviceNames(deviceIds: List<String>): List<String> = mutex.withLock {
        deviceIds.mapNotNull { id -> devices.find { it.id == id }?.name }
    }

    suspend fun markDevicesForTask(deviceIds: List<String>, task: ConfigurationTask) = mutex.withLock {
        val now = timeFormat.format(Date())
        deviceIds.forEach { id ->
            val index = devices.indexOfFirst { it.id == id }
            if (index < 0) return@forEach
            val device = devices[index]
            devices[index] = device.copy(
                status = DeviceStatus.BUSY,
                currentTaskId = task.id,
                currentTaskName = task.title.ifBlank { task.recipeName },
                lastHeartbeat = now
            )
        }
    }

    suspend fun heartbeatWindow(): String = mutex.withLock {
        val times = devices.mapNotNull { it.lastHeartbeat }
            .filter { it.isNotBlank() && it != "--" }
        if (times.isEmpty()) "--" else "${times.minOrNull()} - ${times.maxOrNull()}"
    }
}

/**
 * 配置记录内存仓库
 */
private class ConfigurationRecordStore {
    private val mutex = Mutex()
    private val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val records = ConfigurationRecordSampleData.records().toMutableList()

    suspend fun getRecords(filter: ConfigurationRecordFilterQuery): List<ConfigurationRecord> = mutex.withLock {
        var result = records.toList()
        filter.customer?.takeIf { it.isNotBlank() }?.let { customer ->
            result = result.filter { it.customer == customer }
        }
        filter.salesOwner?.takeIf { it.isNotBlank() }?.let { sales ->
            result = result.filter { it.salesOwner == sales }
        }
        filter.operator?.takeIf { it.isNotBlank() }?.let { operator ->
            result = result.filter { it.operator == operator }
        }
        filter.status?.let { status ->
            result = result.filter { it.resultStatus == status }
        }
        result = if (filter.sortAscending) {
            result.sortedBy { it.updatedAt }
        } else {
            result.sortedByDescending { it.updatedAt }
        }
        val start = filter.offset ?: 0
        val end = minOf(result.size, start + (filter.limit ?: result.size))
        if (start >= result.size) emptyList() else result.subList(start, end)
    }

    suspend fun getRecord(id: String): ConfigurationRecord? = mutex.withLock {
        records.find { it.id == id }
    }

    suspend fun createRecord(payload: ConfigurationRecordPayload): ConfigurationRecord = mutex.withLock {
        val timestamp = formatter.format(Date())
        val record = ConfigurationRecord(
            id = payload.recordId?.takeIf { it.isNotBlank() } ?: "CR-${UUID.randomUUID()}",
            taskId = payload.taskId.ifBlank { "TASK-${System.currentTimeMillis()}" },
            recipeId = payload.recipeId.ifBlank { payload.recipeCode },
            recipeName = payload.recipeName,
            recipeCode = payload.recipeCode,
            category = payload.category.ifBlank { "研发配置" },
            operator = payload.operator,
            quantity = payload.quantity,
            unit = payload.unit,
            actualQuantity = payload.actualQuantity,
            customer = payload.customer,
            salesOwner = payload.salesOwner,
            resultStatus = payload.resultStatus,
            updatedAt = timestamp,
            tags = payload.tags.takeIf { it.isNotEmpty() } ?: listOf("研发配置"),
            note = payload.note
        )
        records.add(0, record)
        record
    }

    suspend fun updateStatus(id: String, status: ConfigurationRecordStatus, note: String?): ConfigurationRecord? =
        mutex.withLock {
            val index = records.indexOfFirst { it.id == id }
            if (index < 0) return@withLock null
            val updated = records[index].copy(
                resultStatus = status,
                note = note ?: records[index].note,
                updatedAt = formatter.format(Date())
            )
            records[index] = updated
            updated
        }
}

private data class ConfigurationRecordFilterQuery(
    val customer: String?,
    val salesOwner: String?,
    val operator: String?,
    val status: ConfigurationRecordStatus?,
    val sortAscending: Boolean,
    val limit: Int?,
    val offset: Int?
) {
    companion object {
        fun fromQuery(
            customer: String?,
            salesOwner: String?,
            operator: String?,
            status: String?,
            sort: String,
            limit: Int?,
            offset: Int?
        ): ConfigurationRecordFilterQuery {
            val parsedStatus = status
                ?.takeIf { it.isNotBlank() }
                ?.uppercase(Locale.getDefault())
                ?.let { value -> runCatching { ConfigurationRecordStatus.valueOf(value) }.getOrNull() }
            val sortAscending = sort.equals("asc", ignoreCase = true)
            return ConfigurationRecordFilterQuery(
                customer = customer,
                salesOwner = salesOwner,
                operator = operator,
                status = parsedStatus,
                sortAscending = sortAscending,
                limit = limit,
                offset = offset
            )
        }
    }
}

private data class TaskStatusUpdateRequest(
    val status: TaskStatus
)

private data class QuickPublishRequest(
    val title: String,
    val recipeKeyword: String,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val deviceId: String? = null,
    val deviceIds: List<String> = emptyList()
)

private data class RecordStatusUpdateRequest(
    val status: ConfigurationRecordStatus,
    val note: String? = null
)
