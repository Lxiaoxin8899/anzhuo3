package com.example.smartdosing.web

import android.content.Context
import android.util.Log
import com.example.smartdosing.data.*
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
import kotlinx.coroutines.*
import java.util.Locale

/**
 * Web服务器管理类
 * 负责启动和管理Ktor web服务器
 */
class WebServerManager(private val context: Context) {

    private var server: NettyApplicationEngine? = null
    private val recipeRepository = RecipeRepository.getInstance()
    private val templateRepository = TemplateRepository.getInstance()
    private val importManager = RecipeImportManager(recipeRepository)
    private val gson = Gson()

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
        get("/") {
            call.respondHtml {
                generateMainPage()
            }
        }

        get("/recipes") {
            call.respondHtml {
                generateRecipesPage()
            }
        }

        get("/import") {
            call.respondHtml {
                generateImportPage()
            }
        }

        get("/stats") {
            call.respondHtml {
                generateStatsPage()
            }
        }

        get("/templates") {
            call.respondHtml {
                generateTemplatePage()
            }
        }
    }

    /**
     * 配置API路由
     */
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
                        ApiResponse<List<Recipe>>(success = false, message = "获取配方列表失败")
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

            post("/import/recipes") {
                try {
                    val multipart = call.receiveMultipart()
                    var fileBytes: ByteArray? = null
                    var fileName: String? = null

                    multipart.forEachPart { part ->
                        when (part) {
                            is PartData.FileItem -> {
                                fileBytes = part.streamProvider().readBytes()
                                fileName = part.originalFileName
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
                    val summary = when {
                        lowerName.endsWith(".xlsx") -> importManager.importExcel(bytes)
                        lowerName.endsWith(".csv") -> importManager.importCsvFile(bytes)
                        else -> return@post call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse<ImportSummary>(success = false, message = "仅支持CSV或Excel模板文件")
                        )
                    }

                    val message = when {
                        summary.success > 0 && summary.failed == 0 -> "成功导入${summary.success}条配方"
                        summary.success > 0 && summary.failed > 0 -> "成功导入${summary.success}条，${summary.failed}条失败"
                        else -> "未导入任何配方，请检查模板内容"
                    }

                    call.respond(
                        ApiResponse(success = summary.success > 0 && summary.failed == 0, message = message, data = summary)
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "文件导入失败", e)
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<ImportSummary>(success = false, message = e.message ?: "导入失败")
                    )
                }
            }
        }
    }
}
