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
    private val recipeRepository = DatabaseRecipeRepository.getInstance(context)
    private val templateRepository = TemplateRepository.getInstance()
    private val importManager = DatabaseRecipeImportManager.getInstance(context, recipeRepository)
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
        // 添加编码测试页面
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

            Log.i(TAG, "=== 发送测试页面 ===")
            Log.i(TAG, "Content-Type: text/html; charset=UTF-8")
            Log.i(TAG, "Content-Length: ${testHtml.toByteArray(Charsets.UTF_8).size}")

            call.respondText(testHtml, ContentType.Text.Html.withCharset(Charsets.UTF_8))
        }

        get("/") {
            Log.i(TAG, "请求首页")
            val htmlContent = """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>SmartDosing 智能投料系统 - 管理后台</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Arial, sans-serif; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); min-height: 100vh; }
        .navbar { background: rgba(255,255,255,0.1); backdrop-filter: blur(10px); padding: 1rem 2rem; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
        .nav-container { display: flex; justify-content: space-between; align-items: center; max-width: 1400px; margin: 0 auto; }
        .nav-brand { color: white; font-size: 1.5rem; font-weight: bold; text-decoration: none; }
        .nav-links { display: flex; gap: 2rem; }
        .nav-links a { color: white; text-decoration: none; transition: opacity 0.3s; }
        .nav-links a:hover { opacity: 0.8; }
        .container { max-width: 1200px; margin: 4rem auto; padding: 0 2rem; }
        .hero { text-align: center; color: white; margin-bottom: 4rem; }
        .hero h1 { font-size: 3rem; margin-bottom: 1rem; font-weight: bold; }
        .hero p { font-size: 1.25rem; opacity: 0.9; }
        .cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 2rem; }
        .card { background: white; padding: 2rem; border-radius: 12px; box-shadow: 0 10px 30px rgba(0,0,0,0.2); transition: transform 0.3s, box-shadow 0.3s; }
        .card:hover { transform: translateY(-5px); box-shadow: 0 15px 40px rgba(0,0,0,0.3); }
        .card-icon { font-size: 3rem; margin-bottom: 1rem; }
        .card h3 { color: #2d3748; font-size: 1.5rem; margin-bottom: 0.5rem; }
        .card p { color: #718096; margin-bottom: 1.5rem; }
        .btn { display: inline-block; padding: 0.75rem 1.5rem; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; text-decoration: none; border-radius: 8px; transition: all 0.3s; border: none; cursor: pointer; font-size: 1rem; }
        .btn:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4); }
    </style>
</head>
<body>
    <nav class="navbar">
        <div class="nav-container">
            <a href="/" class="nav-brand">SmartDosing</a>
            <div class="nav-links">
                <a href="/">首页</a>
                <a href="/recipes">配方管理</a>
                <a href="/import">导入配方</a>
                <a href="/templates">模板管理</a>
                <a href="/stats">统计分析</a>
            </div>
        </div>
    </nav>

    <div class="container">
        <div class="hero">
            <h1>智能投料系统管理后台</h1>
            <p>轻松管理配方，精确控制投料</p>
        </div>

        <div class="cards">
            <div class="card">
                <div class="card-icon">📋</div>
                <h3>配方管理</h3>
                <p>查看、编辑和管理所有配方信息</p>
                <a href="/recipes" class="btn">进入配方管理</a>
            </div>

            <div class="card">
                <div class="card-icon">📁</div>
                <h3>导入配方</h3>
                <p>通过CSV或Excel文件批量导入配方</p>
                <a href="/import" class="btn">导入配方</a>
            </div>

            <div class="card">
                <div class="card-icon">📄</div>
                <h3>模板管理</h3>
                <p>下载标准配方导入模板</p>
                <a href="/templates" class="btn">下载模板</a>
            </div>

            <div class="card">
                <div class="card-icon">📊</div>
                <h3>统计分析</h3>
                <p>查看配方使用统计和数据分析</p>
                <a href="/stats" class="btn">查看统计</a>
            </div>
        </div>
    </div>
</body>
</html>
            """.trimIndent()
            Log.i(TAG, "首页HTML长度: ${htmlContent.length}")
            call.respondText(htmlContent, ContentType.Text.Html.withCharset(Charsets.UTF_8))
        }

        get("/recipes") {
            Log.i(TAG, "请求配方页面")
            val htmlContent = """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>配方管理 - SmartDosing</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Arial, sans-serif; background: #f5f7fa; }
        .navbar { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 1rem 2rem; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
        .nav-container { display: flex; justify-content: space-between; align-items: center; max-width: 1400px; margin: 0 auto; }
        .nav-brand { color: white; font-size: 1.5rem; font-weight: bold; text-decoration: none; }
        .nav-links { display: flex; gap: 2rem; }
        .nav-links a { color: white; text-decoration: none; transition: opacity 0.3s; }
        .nav-links a:hover { opacity: 0.8; }
        .container { max-width: 1400px; margin: 2rem auto; padding: 0 2rem; }
        .page-header { background: white; padding: 2rem; border-radius: 12px; margin-bottom: 2rem; box-shadow: 0 2px 10px rgba(0,0,0,0.05); }
        .page-header h2 { color: #2d3748; font-size: 2rem; margin-bottom: 0.5rem; }
        .page-header p { color: #718096; }
        .search-bar { background: white; padding: 1.5rem; border-radius: 12px; margin-bottom: 2rem; box-shadow: 0 2px 10px rgba(0,0,0,0.05); }
        .search-input { width: 100%; padding: 0.75rem 1rem; border: 1px solid #cbd5e0; border-radius: 8px; font-size: 1rem; }
        .table-container { background: white; border-radius: 12px; box-shadow: 0 2px 10px rgba(0,0,0,0.05); overflow: hidden; }
        table { width: 100%; border-collapse: collapse; }
        thead { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); }
        thead th { color: white; padding: 1rem; text-align: left; font-weight: 600; }
        tbody tr { border-bottom: 1px solid #e2e8f0; transition: background 0.2s; }
        tbody tr:hover { background: #f7fafc; }
        tbody td { padding: 1rem; color: #2d3748; }
        .loading { text-align: center; padding: 2rem; color: #718096; }
        .error { background: #fed7d7; color: #c53030; padding: 1rem; border-radius: 8px; margin: 1rem 0; }
        .actions { display: flex; gap: 0.5rem; }
        .action-btn { padding: 0.25rem 0.75rem; border: none; border-radius: 6px; cursor: pointer; font-size: 0.9rem; }
        .action-btn.delete { background: #fee2e2; color: #b91c1c; }
        .action-btn.delete:hover { background: #fecaca; }
    </style>
</head>
<body>
    <nav class="navbar">
        <div class="nav-container">
            <a href="/" class="nav-brand">SmartDosing</a>
            <div class="nav-links">
                <a href="/">首页</a>
                <a href="/recipes">配方管理</a>
                <a href="/import">导入配方</a>
                <a href="/templates">模板管理</a>
                <a href="/stats">统计分析</a>
            </div>
        </div>
    </nav>

    <div class="container">
        <div class="page-header">
            <h2>配方管理</h2>
            <p>查看和管理所有配方信息</p>
        </div>

        <div class="search-bar">
            <input type="text" id="search-input" class="search-input" placeholder="搜索配方名称或编码...">
        </div>

        <div class="table-container">
            <table>
                <thead>
                    <tr>
                        <th>配方编码</th>
                        <th>配方名称</th>
                        <th>分类</th>
                        <th>客户</th>
                        <th>状态</th>
                        <th>使用次数</th>
                        <th>创建时间</th>
                        <th>操作</th>
                    </tr>
                </thead>
                <tbody id="recipes-tbody">
                    <tr><td colspan="8" class="loading">加载中...</td></tr>
                </tbody>
            </table>
        </div>
    </div>

    <script>
        let allRecipes = [];

        async function loadRecipes() {
            try {
                const response = await fetch('/api/recipes');
                const result = await response.json();

                if (result.success && result.data) {
                    allRecipes = result.data;
                    displayRecipes(allRecipes);
                } else {
                    showError('加载配方失败：' + (result.message || '未知错误'));
                }
            } catch (error) {
                showError('网络错误：' + error.message);
            }
        }

        function displayRecipes(recipes) {
            const tbody = document.getElementById('recipes-tbody');

            if (recipes.length === 0) {
                tbody.innerHTML = '<tr><td colspan="8" class="loading">暂无配方数据</td></tr>';
                return;
            }

            tbody.innerHTML = recipes.map(recipe => {
                const useCount = recipe.usageCount ?? recipe.useCount ?? 0;
                const createTime = recipe.createTime ?? recipe.createdAt;
                const formattedTime = createTime ? new Date(createTime).toLocaleDateString('zh-CN') : '-';
                const safeName = encodeURIComponent(recipe.name || recipe.code || '-');
                return `
                    <tr>
                        <td>${'$'}{recipe.code || '-'}</td>
                        <td>${'$'}{recipe.name || '-'}</td>
                        <td>${'$'}{recipe.category || '-'}</td>
                        <td>${'$'}{recipe.customer || '-'}</td>
                        <td>${'$'}{recipe.status || '-'}</td>
                        <td>${'$'}{useCount}</td>
                        <td>${'$'}{formattedTime}</td>
                        <td>
                            <div class="actions">
                                <button class="action-btn delete delete-btn" data-id="${'$'}{recipe.id}" data-name="${'$'}{safeName}">删除</button>
                            </div>
                        </td>
                    </tr>
                `;
            }).join('');
            attachActionHandlers();
        }

        function showError(message) {
            const tbody = document.getElementById('recipes-tbody');
            tbody.innerHTML = `<tr><td colspan="8"><div class="error">${'$'}{message}</div></td></tr>`;
        }

        function attachActionHandlers() {
            const buttons = document.querySelectorAll('.delete-btn');
            buttons.forEach(btn => {
                btn.addEventListener('click', () => {
                    const id = btn.dataset.id;
                    const name = decodeURIComponent(btn.dataset.name || '');
                    confirmDeleteRecipe(id, name);
                });
            });
        }

        async function confirmDeleteRecipe(id, name) {
            if (!id) {
                alert('未找到配方ID，无法删除');
                return;
            }
            const confirmed = window.confirm(`确定删除配方【${'$'}{name || '未命名'}】吗？此操作不可恢复。`);
            if (!confirmed) return;
            try {
                const response = await fetch(`/api/recipes/${'$'}{id}`, { method: 'DELETE' });
                const result = await response.json();
                if (result.success) {
                    alert(result.message || '删除成功');
                    loadRecipes();
                } else {
                    alert('删除失败：' + (result.message || '未知错误'));
                }
            } catch (error) {
                alert('网络错误：' + error.message);
            }
        }

        document.getElementById('search-input').addEventListener('input', (e) => {
            const searchTerm = e.target.value.toLowerCase();
            const filtered = allRecipes.filter(recipe =>
                (recipe.name && recipe.name.toLowerCase().includes(searchTerm)) ||
                (recipe.code && recipe.code.toLowerCase().includes(searchTerm))
            );
            displayRecipes(filtered);
        });

        loadRecipes();
    </script>
</body>
</html>
            """.trimIndent()
            call.respondText(htmlContent, ContentType.Text.Html.withCharset(Charsets.UTF_8))
        }

        get("/import") {
            Log.i(TAG, "请求导入页面")
            val htmlContent = """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>配方导入 - SmartDosing</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Arial, sans-serif; background: #f5f7fa; }
        .navbar { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 1rem 2rem; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
        .nav-container { display: flex; justify-content: space-between; align-items: center; max-width: 1400px; margin: 0 auto; }
        .nav-brand { color: white; font-size: 1.5rem; font-weight: bold; text-decoration: none; }
        .nav-links { display: flex; gap: 2rem; }
        .nav-links a { color: white; text-decoration: none; transition: opacity 0.3s; }
        .nav-links a:hover { opacity: 0.8; }
        .container { max-width: 1200px; margin: 2rem auto; padding: 0 2rem; }
        .page-header { background: white; padding: 2rem; border-radius: 12px; margin-bottom: 2rem; box-shadow: 0 2px 10px rgba(0,0,0,0.05); }
        .page-header h2 { color: #2d3748; font-size: 2rem; margin-bottom: 0.5rem; }
        .page-header p { color: #718096; }
        .import-card { background: white; padding: 2rem; border-radius: 12px; box-shadow: 0 2px 10px rgba(0,0,0,0.05); }
        .section-header { margin-bottom: 2rem; border-bottom: 2px solid #e2e8f0; padding-bottom: 1rem; }
        .section-header h3 { color: #2d3748; font-size: 1.5rem; margin-bottom: 0.5rem; }
        .section-header p { color: #718096; }
        .form-group { margin-bottom: 1.5rem; }
        .form-group label { display: block; margin-bottom: 0.5rem; color: #2d3748; font-weight: 500; }
        .form-control { width: 100%; padding: 0.75rem; border: 1px solid #cbd5e0; border-radius: 8px; font-size: 1rem; }
        .file-upload-area { border: 2px dashed #cbd5e0; border-radius: 8px; padding: 3rem; text-align: center; background: #f7fafc; cursor: pointer; transition: all 0.3s; }
        .file-upload-area:hover { background: #edf2f7; border-color: #667eea; }
        .file-upload-area.drag-over { background: #e6f2ff; border-color: #667eea; }
        .upload-icon { font-size: 3rem; margin-bottom: 1rem; color: #667eea; }
        .upload-text { color: #4a5568; margin-bottom: 0.5rem; }
        .upload-hint { color: #a0aec0; font-size: 0.875rem; }
        .btn { padding: 0.75rem 1.5rem; border: none; border-radius: 8px; font-size: 1rem; cursor: pointer; transition: all 0.3s; text-decoration: none; display: inline-block; }
        .btn-primary { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; }
        .btn-primary:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4); }
        #upload-progress { display: none; margin-top: 1.5rem; }
        .progress-bar { width: 100%; height: 8px; background: #e2e8f0; border-radius: 4px; overflow: hidden; }
        .progress-fill { height: 100%; background: linear-gradient(90deg, #667eea, #764ba2); width: 0%; transition: width 0.3s; }
        .result-message { margin-top: 1.5rem; padding: 1rem; border-radius: 8px; display: none; }
        .result-success { background: #d4edda; color: #155724; border: 1px solid #c3e6cb; }
        .result-error { background: #f8d7da; color: #721c24; border: 1px solid #f5c6cb; }
        .file-info { margin-top: 1rem; padding: 1rem; background: #edf2f7; border-radius: 8px; display: none; }
    </style>
</head>
<body>
    <nav class="navbar">
        <div class="nav-container">
            <a href="/" class="nav-brand">SmartDosing</a>
            <div class="nav-links">
                <a href="/">首页</a>
                <a href="/recipes">配方管理</a>
                <a href="/import">导入配方</a>
                <a href="/templates">模板管理</a>
                <a href="/stats">统计分析</a>
            </div>
        </div>
    </nav>

    <div class="container">
        <div class="page-header">
            <h2>配方导入</h2>
            <p>通过CSV或Excel文件批量导入配方数据</p>
        </div>

        <div class="import-card">
            <div class="section-header">
                <h3>文件上传</h3>
                <p>支持CSV和Excel格式，单次最多导入1000条配方</p>
            </div>

            <div class="file-upload-area" id="drop-zone">
                <div class="upload-icon">📁</div>
                <div class="upload-text">点击选择文件或拖拽文件到此处</div>
                <div class="upload-hint">支持 .csv, .xlsx 文件，最大50MB</div>
                <input type="file" id="file-input" accept=".csv,.xlsx" style="display: none;">
            </div>

            <div class="file-info" id="file-info">
                <strong>已选择文件：</strong><span id="file-name"></span>
                <br>
                <strong>文件大小：</strong><span id="file-size"></span>
            </div>

            <div id="upload-progress">
                <div class="progress-bar">
                    <div class="progress-fill" id="progress-fill"></div>
                </div>
                <p id="progress-text" style="text-align: center; margin-top: 0.5rem;">上传中...</p>
            </div>

            <div id="result-message" class="result-message"></div>

            <div style="margin-top: 2rem; text-align: center;">
                <button class="btn btn-primary" id="upload-btn" disabled>开始上传</button>
            </div>
        </div>
    </div>

    <script>
        const dropZone = document.getElementById('drop-zone');
        const fileInput = document.getElementById('file-input');
        const uploadBtn = document.getElementById('upload-btn');
        const fileInfo = document.getElementById('file-info');
        const uploadProgress = document.getElementById('upload-progress');
        const resultMessage = document.getElementById('result-message');
        let selectedFile = null;

        dropZone.addEventListener('click', () => fileInput.click());

        fileInput.addEventListener('change', (e) => {
            if (e.target.files.length > 0) {
                selectedFile = e.target.files[0];
                showFileInfo(selectedFile);
            }
        });

        dropZone.addEventListener('dragover', (e) => {
            e.preventDefault();
            dropZone.classList.add('drag-over');
        });

        dropZone.addEventListener('dragleave', () => {
            dropZone.classList.remove('drag-over');
        });

        dropZone.addEventListener('drop', (e) => {
            e.preventDefault();
            dropZone.classList.remove('drag-over');
            if (e.dataTransfer.files.length > 0) {
                selectedFile = e.dataTransfer.files[0];
                showFileInfo(selectedFile);
            }
        });

        function showFileInfo(file) {
            document.getElementById('file-name').textContent = file.name;
            document.getElementById('file-size').textContent = (file.size / 1024).toFixed(2) + ' KB';
            fileInfo.style.display = 'block';
            uploadBtn.disabled = false;
        }

        uploadBtn.addEventListener('click', async () => {
            if (!selectedFile) return;

            const formData = new FormData();
            formData.append('file', selectedFile);

            uploadProgress.style.display = 'block';
            resultMessage.style.display = 'none';
            uploadBtn.disabled = true;

            try {
                const response = await fetch('/api/import/recipes', {
                    method: 'POST',
                    body: formData
                });

                const result = await response.json();

                if (result.success) {
                    showResult('成功导入 ' + result.data.success + ' 条配方！失败 ' + result.data.failed + ' 条', 'success');
                } else {
                    showResult('导入失败：' + result.message, 'error');
                }
            } catch (error) {
                showResult('上传失败：' + error.message, 'error');
            } finally {
                uploadProgress.style.display = 'none';
                uploadBtn.disabled = false;
            }
        });

        function showResult(message, type) {
            resultMessage.textContent = message;
            resultMessage.className = 'result-message result-' + type;
            resultMessage.style.display = 'block';
        }
    </script>
</body>
</html>
            """.trimIndent()
            call.respondText(htmlContent, ContentType.Text.Html.withCharset(Charsets.UTF_8))
        }

        get("/stats") {
            Log.i(TAG, "请求统计页面")
            val htmlContent = """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>统计分析 - SmartDosing</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Arial, sans-serif; background: #f5f7fa; }
        .navbar { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 1rem 2rem; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
        .nav-container { display: flex; justify-content: space-between; align-items: center; max-width: 1400px; margin: 0 auto; }
        .nav-brand { color: white; font-size: 1.5rem; font-weight: bold; text-decoration: none; }
        .nav-links { display: flex; gap: 2rem; }
        .nav-links a { color: white; text-decoration: none; transition: opacity 0.3s; }
        .nav-links a:hover { opacity: 0.8; }
        .container { max-width: 1400px; margin: 2rem auto; padding: 0 2rem; }
        .page-header { background: white; padding: 2rem; border-radius: 12px; margin-bottom: 2rem; box-shadow: 0 2px 10px rgba(0,0,0,0.05); }
        .page-header h2 { color: #2d3748; font-size: 2rem; margin-bottom: 0.5rem; }
        .page-header p { color: #718096; }
        .stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 1.5rem; margin-bottom: 2rem; }
        .stat-card { background: white; padding: 1.5rem; border-radius: 12px; box-shadow: 0 2px 10px rgba(0,0,0,0.05); }
        .stat-card h3 { color: #718096; font-size: 0.875rem; margin-bottom: 0.5rem; text-transform: uppercase; }
        .stat-card .value { color: #2d3748; font-size: 2rem; font-weight: bold; }
        .chart-card { background: white; padding: 2rem; border-radius: 12px; box-shadow: 0 2px 10px rgba(0,0,0,0.05); margin-bottom: 2rem; }
        .chart-card h3 { color: #2d3748; font-size: 1.25rem; margin-bottom: 1rem; }
        .category-item { display: flex; justify-content: space-between; padding: 0.75rem; border-bottom: 1px solid #e2e8f0; }
        .category-item:last-child { border-bottom: none; }
        .category-name { color: #2d3748; }
        .category-count { color: #667eea; font-weight: bold; }
        .loading { text-align: center; padding: 2rem; color: #718096; }
    </style>
</head>
<body>
    <nav class="navbar">
        <div class="nav-container">
            <a href="/" class="nav-brand">SmartDosing</a>
            <div class="nav-links">
                <a href="/">首页</a>
                <a href="/recipes">配方管理</a>
                <a href="/import">导入配方</a>
                <a href="/templates">模板管理</a>
                <a href="/stats">统计分析</a>
            </div>
        </div>
    </nav>

    <div class="container">
        <div class="page-header">
            <h2>统计分析</h2>
            <p>查看配方使用统计和数据分析</p>
        </div>

        <div class="stats-grid">
            <div class="stat-card">
                <h3>配方总数</h3>
                <div class="value" id="total-recipes">-</div>
            </div>
            <div class="stat-card">
                <h3>分类数量</h3>
                <div class="value" id="category-count">-</div>
            </div>
            <div class="stat-card">
                <h3>客户数量</h3>
                <div class="value" id="customer-count">-</div>
            </div>
            <div class="stat-card">
                <h3>总使用次数</h3>
                <div class="value" id="total-uses">-</div>
            </div>
        </div>

        <div class="chart-card">
            <h3>按分类统计</h3>
            <div id="category-chart" class="loading">加载中...</div>
        </div>

        <div class="chart-card">
            <h3>按客户统计</h3>
            <div id="customer-chart" class="loading">加载中...</div>
        </div>
    </div>

    <script>
        async function loadStats() {
            try {
                const response = await fetch('/api/stats');
                const result = await response.json();

                if (result.success && result.data) {
                    const stats = result.data;

                    document.getElementById('total-recipes').textContent = stats.totalRecipes || 0;
                    document.getElementById('category-count').textContent = Object.keys(stats.categoryCounts || {}).length;
                    document.getElementById('customer-count').textContent = Object.keys(stats.customerCounts || {}).length;

                    let totalUses = 0;
                    if (stats.mostUsed && Array.isArray(stats.mostUsed)) {
                        totalUses = stats.mostUsed.reduce((sum, r) => sum + (r.useCount || 0), 0);
                    }
                    document.getElementById('total-uses').textContent = totalUses;

                    displayCategoryChart(stats.categoryCounts || {});
                    displayCustomerChart(stats.customerCounts || {});
                } else {
                    showError();
                }
            } catch (error) {
                console.error('加载统计失败:', error);
                showError();
            }
        }

        function displayCategoryChart(categoryCounts) {
            const container = document.getElementById('category-chart');

            if (Object.keys(categoryCounts).length === 0) {
                container.innerHTML = '<div class="loading">暂无数据</div>';
                return;
            }

            container.innerHTML = Object.entries(categoryCounts)
                .sort((a, b) => b[1] - a[1])
                .map(([name, count]) => `
                    <div class="category-item">
                        <span class="category-name">${'$'}{name}</span>
                        <span class="category-count">${'$'}{count}</span>
                    </div>
                `).join('');
        }

        function displayCustomerChart(customerCounts) {
            const container = document.getElementById('customer-chart');

            if (Object.keys(customerCounts).length === 0) {
                container.innerHTML = '<div class="loading">暂无数据</div>';
                return;
            }

            container.innerHTML = Object.entries(customerCounts)
                .sort((a, b) => b[1] - a[1])
                .slice(0, 10)
                .map(([name, count]) => `
                    <div class="category-item">
                        <span class="category-name">${'$'}{name}</span>
                        <span class="category-count">${'$'}{count}</span>
                    </div>
                `).join('');
        }

        function showError() {
            document.getElementById('total-recipes').textContent = '0';
            document.getElementById('category-count').textContent = '0';
            document.getElementById('customer-count').textContent = '0';
            document.getElementById('total-uses').textContent = '0';
            document.getElementById('category-chart').innerHTML = '<div class="loading">加载失败</div>';
            document.getElementById('customer-chart').innerHTML = '<div class="loading">加载失败</div>';
        }

        loadStats();
    </script>
</body>
</html>
            """.trimIndent()
            call.respondText(htmlContent, ContentType.Text.Html.withCharset(Charsets.UTF_8))
        }

        get("/templates") {
            Log.i(TAG, "请求模板页面")
            val htmlContent = """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>模板管理 - SmartDosing</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Arial, sans-serif; background: #f5f7fa; }
        .navbar { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 1rem 2rem; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
        .nav-container { display: flex; justify-content: space-between; align-items: center; max-width: 1400px; margin: 0 auto; }
        .nav-brand { color: white; font-size: 1.5rem; font-weight: bold; text-decoration: none; }
        .nav-links { display: flex; gap: 2rem; }
        .nav-links a { color: white; text-decoration: none; transition: opacity 0.3s; }
        .nav-links a:hover { opacity: 0.8; }
        .container { max-width: 1200px; margin: 2rem auto; padding: 0 2rem; }
        .page-header { background: white; padding: 2rem; border-radius: 12px; margin-bottom: 2rem; box-shadow: 0 2px 10px rgba(0,0,0,0.05); }
        .page-header h2 { color: #2d3748; font-size: 2rem; margin-bottom: 0.5rem; }
        .page-header p { color: #718096; }
        .template-grid { display: grid; gap: 1.5rem; }
        .template-card { background: white; padding: 2rem; border-radius: 12px; box-shadow: 0 2px 10px rgba(0,0,0,0.05); }
        .template-header { display: flex; justify-content: space-between; align-items: start; margin-bottom: 1rem; }
        .template-title { color: #2d3748; font-size: 1.5rem; font-weight: bold; }
        .template-desc { color: #718096; margin-bottom: 1.5rem; }
        .template-actions { display: flex; gap: 1rem; }
        .btn { padding: 0.75rem 1.5rem; border: none; border-radius: 8px; font-size: 1rem; cursor: pointer; transition: all 0.3s; text-decoration: none; display: inline-block; }
        .btn-primary { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; }
        .btn-primary:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4); }
        .btn-secondary { background: #e2e8f0; color: #2d3748; }
        .btn-secondary:hover { background: #cbd5e0; }
        .loading { text-align: center; padding: 3rem; color: #718096; }
    </style>
</head>
<body>
    <nav class="navbar">
        <div class="nav-container">
            <a href="/" class="nav-brand">SmartDosing</a>
            <div class="nav-links">
                <a href="/">首页</a>
                <a href="/recipes">配方管理</a>
                <a href="/import">导入配方</a>
                <a href="/templates">模板管理</a>
                <a href="/stats">统计分析</a>
            </div>
        </div>
    </nav>

    <div class="container">
        <div class="page-header">
            <h2>模板管理</h2>
            <p>下载和管理配方导入模板</p>
        </div>

        <div id="templates-container" class="template-grid">
            <div class="loading">加载模板列表...</div>
        </div>
    </div>

    <script>
        async function loadTemplates() {
            try {
                const response = await fetch('/api/templates');
                const result = await response.json();

                if (result.success && result.data) {
                    displayTemplates(result.data);
                } else {
                    showError('加载模板失败：' + (result.message || '未知错误'));
                }
            } catch (error) {
                showError('网络错误：' + error.message);
            }
        }

        function displayTemplates(templates) {
            const container = document.getElementById('templates-container');

            if (templates.length === 0) {
                container.innerHTML = '<div class="loading">暂无模板</div>';
                return;
            }

            container.innerHTML = templates.map(template => `
                <div class="template-card">
                    <div class="template-header">
                        <div class="template-title">${'$'}{template.name || '未命名模板'}</div>
                    </div>
                    <div class="template-desc">${'$'}{template.description || '无描述'}</div>
                    <div class="template-actions">
                        <a href="/api/templates/${'$'}{template.id}/download?format=csv" class="btn btn-primary">下载CSV模板</a>
                        <a href="/api/templates/${'$'}{template.id}/download?format=xlsx" class="btn btn-secondary">下载Excel模板</a>
                    </div>
                </div>
            `).join('');
        }

        function showError(message) {
            const container = document.getElementById('templates-container');
            container.innerHTML = `<div class="loading" style="color: #e53e3e;">${'$'}{message}</div>`;
        }

        loadTemplates();
    </script>
</body>
</html>
            """.trimIndent()
            call.respondText(htmlContent, ContentType.Text.Html.withCharset(Charsets.UTF_8))
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
