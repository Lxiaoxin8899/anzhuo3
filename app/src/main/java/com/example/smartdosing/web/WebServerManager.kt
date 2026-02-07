package com.example.smartdosing.web

import android.content.Context
import android.util.Log
import com.example.smartdosing.data.*
import com.example.smartdosing.data.transfer.RecipeSyncCommand
import com.example.smartdosing.data.transfer.RecipeSyncOperation
import com.example.smartdosing.data.transfer.RecipeSyncResult
import com.example.smartdosing.data.transfer.toImportRequest
import com.example.smartdosing.data.transfer.IncomingTaskRequest
import com.example.smartdosing.data.transfer.TaskReceiveResponse
import com.example.smartdosing.data.repository.ConfigurationRecordPayload
import com.example.smartdosing.database.entities.AuthorizedSenderEntity
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import com.example.smartdosing.web.transfer.LanTransferProposalAdapter
import com.example.smartdosing.web.transfer.UnsupportedSchemaException

/**
 * 无线传输服务器管理类
 * 负责启动和管理Ktor 无线传输服务器
 */
class WebServerManager(private val context: Context) {

    private var server: NettyApplicationEngine? = null
    private val recipeRepository = DatabaseRecipeRepository.getInstance(context)
    private val templateRepository = TemplateRepository.getInstance()
    private val importManager = DatabaseRecipeImportManager.getInstance(context, recipeRepository)
    private val gson = Gson()
    private val lanAdapter = LanTransferProposalAdapter(gson)
    private val taskStore = ConfigurationTaskStore(recipeRepository)
    private val recordStore = ConfigurationRecordStore(context, gson)
    private val deviceStore = TaskDeviceStore()

    // 设备通信相关
    private val appContext: Context = context.applicationContext

    // API Key 认证（首次启动时自动生成，持久化到 SharedPreferences）
    val apiKey: String by lazy {
        val prefs = context.getSharedPreferences("web_server_prefs", Context.MODE_PRIVATE)
        prefs.getString("api_key", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("api_key", it).apply()
            Log.i(TAG, "已生成新的 API Key")
        }
    }

    companion object {
        private const val TAG = "WebServerManager"
        private const val DEFAULT_PORT = 8080
        private const val API_KEY_HEADER = "X-API-Key"
        private const val MAX_UPLOAD_SIZE = 10L * 1024 * 1024 // 10MB
    }

    /**
     * 启动无线传输服务器
     */
    fun startServer(port: Int = DEFAULT_PORT): Boolean {
        return try {
            server = embeddedServer(Netty, port = port, host = "0.0.0.0") {
                configureServer()
            }
            server?.start(wait = false)
            Log.i(TAG, "无线传输服务器启动成功，端口: $port")
            true
        } catch (e: Exception) {
            Log.e(TAG, "无线传输服务器启动失败", e)
            false
        }
    }

    /**
     * 停止无线传输服务器
     */
    fun stopServer() {
        try {
            server?.stop(1000, 2000)
            server = null
            Log.i(TAG, "无线传输服务器已停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止无线传输服务器失败", e)
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
            allowHeader(API_KEY_HEADER)
            anyHost() // 局域网场景，IP 不固定，保留 anyHost
        }

        install(StatusPages) {
            exception<Throwable> { call, cause ->
                Log.e(TAG, "服务器错误", cause)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<Unit>(
                        success = false,
                        message = "服务器内部错误，请稍后重试"
                    )
                )
            }
        }

        // API Key 认证拦截器：/api 路径下的请求需要携带有效的 API Key
        intercept(ApplicationCallPipeline.Plugins) {
            val path = call.request.path()
            // 静态页面和根路径不需要认证
            if (!path.startsWith("/api")) return@intercept
            val requestKey = call.request.headers[API_KEY_HEADER]
                ?: call.request.queryParameters["api_key"]
            if (requestKey != apiKey) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ApiResponse<Unit>(success = false, message = "未授权：请提供有效的 API Key")
                )
                finish()
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
        suspend fun ApplicationCall.respondHtml(builder: HTML.() -> Unit) {
            val htmlContent = createHTML().html { builder() }
            // 在 </head> 前注入 API Key 脚本，让前端 fetch 自动携带认证头
            val apiKeyScript = """
<script>
(function() {
    const API_KEY = '${apiKey}';
    const _fetch = window.fetch;
    window.fetch = function(url, opts) {
        opts = opts || {};
        opts.headers = opts.headers || {};
        if (opts.headers instanceof Headers) {
            opts.headers.set('X-API-Key', API_KEY);
        } else {
            opts.headers['X-API-Key'] = API_KEY;
        }
        return _fetch.call(this, url, opts);
    };
})();
</script>"""
            val injected = htmlContent.replace("</head>", "$apiKeyScript\n</head>")
            respondText(injected, ContentType.Text.Html.withCharset(Charsets.UTF_8))
        }

        suspend fun ApplicationCall.respondWebUiOffline(path: String) {
            val offlineHtml = """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>可视化页面已下线</title>
    <style>
        body {
            margin: 0;
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
            background: #f5f7fb;
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'PingFang SC', sans-serif;
            color: #1f2937;
        }
        .card {
            width: min(560px, 92vw);
            background: #fff;
            border-radius: 16px;
            box-shadow: 0 12px 30px rgba(15, 23, 42, 0.12);
            padding: 28px;
        }
        h1 {
            margin: 0 0 12px;
            font-size: 24px;
        }
        p {
            margin: 8px 0;
            line-height: 1.7;
            color: #4b5563;
        }
        code {
            background: #eef2ff;
            border-radius: 6px;
            padding: 2px 6px;
            color: #3730a3;
        }
    </style>
</head>
<body>
    <div class="card">
        <h1>可视化页面已软下线</h1>
        <p>当前路径：<code>${path}</code></p>
        <p>系统已切换为“无线传输”模式，可视化页面不再提供访问。</p>
        <p>任务收发与设备联通能力仍可通过 <code>/api/transfer</code> 与 <code>/api/device</code> 正常使用。</p>
    </div>
</body>
</html>
            """.trimIndent()
            respondText(
                text = offlineHtml,
                contentType = ContentType.Text.Html.withCharset(Charsets.UTF_8),
                status = HttpStatusCode.Gone
            )
        }

        get("/") { call.respondWebUiOffline("/") }
        get("/task-center") { call.respondWebUiOffline("/task-center") }
        get("/recipes") { call.respondWebUiOffline("/recipes") }
        get("/import") { call.respondWebUiOffline("/import") }
        get("/stats") { call.respondWebUiOffline("/stats") }
        get("/templates") { call.respondWebUiOffline("/templates") }
        get("/test-encoding") { call.respondWebUiOffline("/test-encoding") }
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

                        val operator = "无线传输服务"
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

            // 设备通信 API - 用于局域网任务传输
            route("/device") {
                // 获取本机设备信息
                get("/info") {
                    try {
                        val deviceIdentity = com.example.smartdosing.data.device.DeviceUIDManager.getDeviceIdentity(appContext)
                        call.respond(ApiResponse(
                            success = true,
                            data = mapOf(
                                "uid" to deviceIdentity.uid,
                                "deviceName" to deviceIdentity.deviceName,
                                "ipAddress" to deviceIdentity.ipAddress,
                                "port" to deviceIdentity.port,
                                "appVersion" to deviceIdentity.appVersion,
                                "status" to deviceIdentity.status.name
                            )
                        ))
                    } catch (e: Exception) {
                        Log.e(TAG, "获取设备信息失败", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ApiResponse<Unit>(success = false, message = "获取设备信息失败")
                        )
                    }
                }

                // 心跳检测
                post("/ping") {
                    try {
                        val deviceIdentity = com.example.smartdosing.data.device.DeviceUIDManager.getDeviceIdentity(appContext)
                        call.respond(ApiResponse(
                            success = true,
                            message = "pong",
                            data = mapOf(
                                "uid" to deviceIdentity.uid,
                                "deviceName" to deviceIdentity.deviceName,
                                "status" to deviceIdentity.status.name,
                                "timestamp" to System.currentTimeMillis()
                            )
                        ))
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ApiResponse<Unit>(success = false, message = "心跳检测失败")
                        )
                    }
                }

                // 获取已授权的发送端列表
                get("/senders") {
                    try {
                        val taskReceiver = com.example.smartdosing.data.transfer.TaskReceiver.getInstance(appContext)
                        val database = com.example.smartdosing.database.SmartDosingDatabase.getDatabase(appContext)
                        val senders = database.deviceDao().getActiveSenders()
                        call.respond(ApiResponse(success = true, data = senders))
                    } catch (e: Exception) {
                        Log.e(TAG, "获取发送端列表失败", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ApiResponse<Unit>(success = false, message = "获取发送端列表失败")
                        )
                    }
                }
            }

            // 任务传输 API
            route("/transfer") {
                // 接收任务（核心接口）
                post("/task") {
                    val rawBody = call.receiveText()
                    val taskReceiver = com.example.smartdosing.data.transfer.TaskReceiver.getInstance(appContext)
                    val request = try {
                        lanAdapter.tryConvert(rawBody) ?: gson.fromJson(rawBody, IncomingTaskRequest::class.java)
                    } catch (e: UnsupportedSchemaException) {
                        Log.w(TAG, "协议版本不受支持: ${e.version}")
                        val deviceIdentity = com.example.smartdosing.data.device.DeviceUIDManager.getDeviceIdentity(appContext)
                        val response = TaskReceiveResponse(
                            success = false,
                            message = e.message ?: "协议版本不受支持",
                            transferId = LanTransferProposalAdapter.extractTransferId(rawBody).orEmpty(),
                            receiverUID = deviceIdentity.uid,
                            receiverName = deviceIdentity.deviceName,
                            schemaVersion = e.version,
                            errorCode = "UNSUPPORTED_VERSION"
                        )
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse(success = false, message = response.message, data = response)
                        )
                        return@post
                    } catch (e: JsonSyntaxException) {
                        Log.w(TAG, "任务请求 JSON 解析失败", e)
                        val deviceIdentity = com.example.smartdosing.data.device.DeviceUIDManager.getDeviceIdentity(appContext)
                        val response = TaskReceiveResponse(
                            success = false,
                            message = "任务解析失败: ${e.message}",
                            transferId = LanTransferProposalAdapter.extractTransferId(rawBody).orEmpty(),
                            receiverUID = deviceIdentity.uid,
                            receiverName = deviceIdentity.deviceName,
                            errorCode = "FIELD_INVALID"
                        )
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse(success = false, message = response.message, data = response)
                        )
                        return@post
                    } catch (e: IllegalArgumentException) {
                        Log.w(TAG, "任务请求字段校验失败: ${e.message}")
                        val deviceIdentity = com.example.smartdosing.data.device.DeviceUIDManager.getDeviceIdentity(appContext)
                        val response = TaskReceiveResponse(
                            success = false,
                            message = e.message ?: "任务内容非法",
                            transferId = LanTransferProposalAdapter.extractTransferId(rawBody).orEmpty(),
                            receiverUID = deviceIdentity.uid,
                            receiverName = deviceIdentity.deviceName,
                            errorCode = "FIELD_INVALID"
                        )
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse(success = false, message = response.message, data = response)
                        )
                        return@post
                    } catch (e: Exception) {
                        Log.e(TAG, "任务解析异常", e)
                        val deviceIdentity = com.example.smartdosing.data.device.DeviceUIDManager.getDeviceIdentity(appContext)
                        val response = TaskReceiveResponse(
                            success = false,
                            message = "任务解析异常: ${e.message}",
                            transferId = LanTransferProposalAdapter.extractTransferId(rawBody).orEmpty(),
                            receiverUID = deviceIdentity.uid,
                            receiverName = deviceIdentity.deviceName,
                            errorCode = "UNKNOWN_ERROR"
                        )
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse(success = false, message = response.message, data = response)
                        )
                        return@post
                    }

                    try {
                        Log.i(TAG, "收到任务传输请求: transferId=${request.transferId}, from=${request.senderName}")
                        val response = taskReceiver.receiveTask(request)

                        if (response.success) {
                            call.respond(HttpStatusCode.Created, ApiResponse(success = true, message = response.message, data = response))
                        } else {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse(success = false, message = response.message, data = response))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "接收任务失败", e)
                        val deviceIdentity = com.example.smartdosing.data.device.DeviceUIDManager.getDeviceIdentity(appContext)
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse(
                                success = false,
                                message = "接收任务失败: ${e.message}",
                                data = TaskReceiveResponse(
                                    success = false,
                                    message = e.message ?: "未知错误",
                                    transferId = request.transferId,
                                    receiverUID = deviceIdentity.uid,
                                    receiverName = deviceIdentity.deviceName,
                                    schemaVersion = request.schemaVersion,
                                    errorCode = "UNKNOWN_ERROR"
                                )
                            )
                        )
                    }
                }

                // UID 配方同步
                post("/recipe-sync") {
                    try {
                        val request = call.receive<RecipeSyncCommand>()
                        Log.i(TAG, "收到配方同步请求: transferId=${request.transferId}, target=${request.targetUID}")

                        val deviceIdentity = com.example.smartdosing.data.device.DeviceUIDManager.getDeviceIdentity(appContext)
                        if (!request.targetUID.equals(deviceIdentity.uid, ignoreCase = true)) {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                ApiResponse<Unit>(success = false, message = "目标UID不匹配，拒绝同步")
                            )
                            return@post
                        }

                        val importRequest = request.recipe.toImportRequest()
                        val existing = request.recipe.code
                            .takeIf { it.isNotBlank() }
                            ?.let { recipeRepository.getRecipeByCode(it) }

                        val (syncedRecipe, operation) = when {
                            existing != null && request.overwrite -> {
                                val updated = recipeRepository.updateRecipe(existing.id, importRequest)
                                    ?: throw IllegalStateException("更新配方失败")
                                updated to RecipeSyncOperation.UPDATED
                            }
                            existing != null -> {
                                call.respond(
                                    HttpStatusCode.Conflict,
                                    ApiResponse<Unit>(success = false, message = "配方已存在，如需覆盖请将 overwrite 设为 true")
                                )
                                return@post
                            }
                            else -> {
                                val created = recipeRepository.addRecipe(importRequest)
                                created to RecipeSyncOperation.CREATED
                            }
                        }

                        val database = com.example.smartdosing.database.SmartDosingDatabase.getDatabase(appContext)
                        val deviceDao = database.deviceDao()
                        val now = System.currentTimeMillis()
                        val isAuthorized = deviceDao.isSenderAuthorized(request.senderUID)
                        if (!isAuthorized) {
                            val newSender = AuthorizedSenderEntity(
                                uid = request.senderUID,
                                name = request.senderName,
                                ipAddress = request.senderIP,
                                authorizedAt = now,
                                lastTaskAt = now,
                                taskCount = 0,
                                isActive = true
                            )
                            deviceDao.upsertSender(newSender)
                        }
                        deviceDao.updateSenderActivity(request.senderUID, now, request.senderIP, null)

                        val syncResult = RecipeSyncResult(
                            transferId = request.transferId,
                            recipeId = syncedRecipe.id,
                            recipeCode = syncedRecipe.code,
                            operation = operation,
                            receiverUID = deviceIdentity.uid,
                            receiverName = deviceIdentity.deviceName
                        )
                        val message = if (operation == RecipeSyncOperation.CREATED) "配方同步成功" else "配方覆盖成功"

                        call.respond(
                            HttpStatusCode.Created,
                            ApiResponse(success = true, message = message, data = syncResult)
                        )
                    } catch (e: IllegalArgumentException) {
                        Log.e(TAG, "配方同步参数错误", e)
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse<Unit>(success = false, message = e.message ?: "配方同步失败")
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "配方同步失败", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ApiResponse<Unit>(success = false, message = e.message ?: "配方同步失败")
                        )
                    }
                }

                // 获取待处理的接收任务
                get("/pending") {
                    try {
                        val database = com.example.smartdosing.database.SmartDosingDatabase.getDatabase(appContext)
                        val pendingTasks = database.deviceDao().getReceivedTasksPaged(status = "PENDING", limit = 50, offset = 0)
                        call.respond(ApiResponse(success = true, data = pendingTasks))
                    } catch (e: Exception) {
                        Log.e(TAG, "获取待处理任务失败", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ApiResponse<Unit>(success = false, message = "获取待处理任务失败")
                        )
                    }
                }

                // 确认接收任务
                post("/accept/{id}") {
                    try {
                        val id = call.parameters["id"] ?: throw IllegalArgumentException("任务ID不能为空")
                        val taskReceiver = com.example.smartdosing.data.transfer.TaskReceiver.getInstance(appContext)
                        val success = taskReceiver.acceptTask(id)

                        if (success) {
                            call.respond(ApiResponse<Unit>(success = true, message = "任务已接收"))
                        } else {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, message = "接收任务失败"))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "确认任务失败", e)
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse<Unit>(success = false, message = e.message ?: "确认任务失败")
                        )
                    }
                }

                // 拒绝任务
                post("/reject/{id}") {
                    try {
                        val id = call.parameters["id"] ?: throw IllegalArgumentException("任务ID不能为空")
                        val taskReceiver = com.example.smartdosing.data.transfer.TaskReceiver.getInstance(appContext)
                        val success = taskReceiver.rejectTask(id)

                        if (success) {
                            call.respond(ApiResponse<Unit>(success = true, message = "任务已拒绝"))
                        } else {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, message = "拒绝任务失败"))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "拒绝任务失败", e)
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse<Unit>(success = false, message = e.message ?: "拒绝任务失败")
                        )
                    }
                }

                // 获取所有接收任务历史
                get("/history") {
                    try {
                        val status = call.request.queryParameters["status"]
                        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                        val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

                        val database = com.example.smartdosing.database.SmartDosingDatabase.getDatabase(appContext)
                        val tasks = database.deviceDao().getReceivedTasksPaged(status = status, limit = limit, offset = offset)
                        call.respond(ApiResponse(success = true, data = tasks))
                    } catch (e: Exception) {
                        Log.e(TAG, "获取任务历史失败", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ApiResponse<Unit>(success = false, message = "获取任务历史失败")
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

                    var fileTooLarge = false
                    multipart.forEachPart { part ->
                        when (part) {
                            is PartData.FileItem -> {
                                fileName = part.originalFileName
                                Log.i(TAG, "[Import] 接收文件: $fileName")
                                val bytes = part.streamProvider().readBytes()
                                if (bytes.size > MAX_UPLOAD_SIZE) {
                                    fileTooLarge = true
                                } else {
                                    fileBytes = bytes
                                    Log.i(TAG, "[Import] 文件大小: ${fileBytes?.size} bytes")
                                }
                            }
                            else -> Unit
                        }
                        part.dispose()
                    }

                    if (fileTooLarge) {
                        return@post call.respond(
                            HttpStatusCode.PayloadTooLarge,
                            ApiResponse<Unit>(success = false, message = "文件大小超过限制（最大 ${MAX_UPLOAD_SIZE / 1024 / 1024}MB）")
                        )
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
 * 配置记录存储：持久化至应用私有目录，确保展示真实数据
 */
private class ConfigurationRecordStore(
    private val context: Context,
    private val gson: Gson
) {
    companion object {
        private const val STORE_TAG = "ConfigurationRecordStore"
        private const val STORE_FILE_NAME = "configuration_records.json"
    }

    private val mutex = Mutex()
    private val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val storageFile = File(context.filesDir, STORE_FILE_NAME)
    private val listType = object : TypeToken<List<ConfigurationRecord>>() {}.type
    private val records = mutableListOf<ConfigurationRecord>()

    init {
        loadRecordsFromDisk()
    }

    private fun loadRecordsFromDisk() {
        runCatching {
            if (!storageFile.exists() || storageFile.length() == 0L) {
                ensureStorageFile()
                return
            }
            val json = storageFile.readText()
            if (json.isBlank()) return
            val parsed: List<ConfigurationRecord>? = gson.fromJson(json, listType)
            if (parsed != null) {
                records.clear()
                records.addAll(parsed)
            }
        }.onFailure {
            Log.e(STORE_TAG, "加载配置记录文件失败", it)
        }
    }

    private fun ensureStorageFile() {
        runCatching {
            storageFile.parentFile?.mkdirs()
            if (!storageFile.exists()) {
                storageFile.createNewFile()
            }
        }.onFailure {
            Log.e(STORE_TAG, "创建配置记录文件失败", it)
        }
    }

    private fun persistRecordsLocked() {
        runCatching {
            ensureStorageFile()
            storageFile.writeText(gson.toJson(records))
        }.onFailure {
            Log.e(STORE_TAG, "保存配置记录失败", it)
        }
    }

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
            note = payload.note,
            materialDetails = payload.materialDetails
        )
        records.add(0, record)
        persistRecordsLocked()
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
            persistRecordsLocked()
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
