package com.example.smartdosing.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartdosing.SmartDosingApplication
import com.example.smartdosing.bluetooth.model.ConnectionState
import com.example.smartdosing.data.ConfigurationRecord
import com.example.smartdosing.data.ConfigurationTask
import com.example.smartdosing.data.RecipeStats
import com.example.smartdosing.data.TaskStatus
import com.example.smartdosing.data.repository.ConfigurationRepositoryProvider
import com.example.smartdosing.data.repository.ConfigurationRecordFilter
import com.example.smartdosing.data.DatabaseRecipeRepository
import com.example.smartdosing.web.WebService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 首页 Dashboard 的 ViewModel
 * 负责聚合各种系统状态、统计数据以及任务恢复逻辑
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SmartDosingApplication
    private val recipeRepository = DatabaseRecipeRepository.getInstance(application)
    private val taskRepository = ConfigurationRepositoryProvider.taskRepository
    private val configurationRecordRepository = ConfigurationRepositoryProvider.recordRepository
    private val webService = WebService.getInstance(application)
    private val scaleManager = app.bluetoothScaleManager

    // 统计数据
    private val _stats = MutableStateFlow(DashboardStats())
    val stats: StateFlow<DashboardStats> = _stats.asStateFlow()

    // 运行状态
    private val _runtimeStatus = MutableStateFlow(HomeRuntimeStatus())
    val runtimeStatus: StateFlow<HomeRuntimeStatus> = _runtimeStatus.asStateFlow()

    // 最近操作
    private val _recentOperations = MutableStateFlow<List<ConfigurationRecord>>(emptyList())
    val recentOperations: StateFlow<List<ConfigurationRecord>> = _recentOperations.asStateFlow()

    // 待恢复的任务
    private val _recoveryTask = MutableStateFlow<ConfigurationTask?>(null)
    val recoveryTask: StateFlow<ConfigurationTask?> = _recoveryTask.asStateFlow()

    // 加载状态
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 蓝牙秤状态
    val scaleConnectionState: StateFlow<ConnectionState> = scaleManager.connectionState

    init {
        refreshData()
        startStatusPolling()
    }

    fun refreshData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 加载统计
                val recipeStats = recipeRepository.getRecipeStats()
                val tasks = taskRepository.fetchTasks()
                
                val pendingCount = tasks.count { 
                    it.status == TaskStatus.DRAFT || it.status == TaskStatus.READY || it.status == TaskStatus.PUBLISHED 
                }
                val inProgressTasks = tasks.filter { it.status == TaskStatus.IN_PROGRESS }
                val completedTodayCount = tasks.count { it.status == TaskStatus.COMPLETED }

                _stats.value = DashboardStats(
                    totalRecipes = recipeStats.totalRecipes,
                    pendingTasks = pendingCount,
                    inProgressTasks = inProgressTasks.size,
                    completedToday = completedTodayCount
                )

                // 记录最近的操作 (从 ConfigurationRecordRepository 获取)
                try {
                    val records = configurationRecordRepository.fetchRecords(ConfigurationRecordFilter(limit = 5))
                    _recentOperations.value = records
                } catch (e: Exception) {
                    _recentOperations.value = emptyList()
                }

                // 查找待恢复的任务 (第一个进行中的任务)
                _recoveryTask.value = inProgressTasks.firstOrNull()

            } catch (e: Exception) {
                // 错误处理
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun startStatusPolling() {
        viewModelScope.launch {
            while (true) {
                val deviceInfo = webService.getDeviceInfo()

                _runtimeStatus.value = HomeRuntimeStatus(
                    isWirelessRunning = deviceInfo.isServerRunning,
                    wirelessAddress = deviceInfo.serverUrl ?: "未连接网络",
                    ttsStatus = "语音服务已下线",
                    ttsHint = "语音播报功能暂时关闭"
                )
                delay(3000)
            }
        }
    }
}

/**
 * 仪表盘统计数据
 */
data class DashboardStats(
    val totalRecipes: Int = 0,
    val pendingTasks: Int = 0,
    val inProgressTasks: Int = 0,
    val completedToday: Int = 0
)

/**
 * 系统运行时状态
 */
data class HomeRuntimeStatus(
    val isWirelessRunning: Boolean = false,
    val wirelessAddress: String = "未连接网络",
    val ttsStatus: String = "语音服务检测中",
    val ttsHint: String = "将根据设备能力自动选择语音引擎"
)
