@echo off
chcp 65001 > nul
setlocal enabledelayedexpansion

REM SmartDosing Web服务测试脚本 (Windows版本)
REM 用于快速测试Web API功能

echo ==========================================
echo SmartDosing Web服务测试脚本
echo ==========================================
echo.

REM 配置
set DEVICE_IP=192.168.1.100
set PORT=8080
set BASE_URL=http://%DEVICE_IP%:%PORT%

echo 提示: 请先修改脚本中的DEVICE_IP变量为你的设备IP地址
echo 当前配置: %BASE_URL%
echo.

REM 检查curl是否可用
where curl >nul 2>nul
if %errorlevel% neq 0 (
    echo ❌ 错误: 未找到curl命令
    echo 请安装curl或使用Windows 10及以上版本
    pause
    exit /b 1
)

REM 检测设备IP
echo 1. 检测Android设备IP...
echo    执行: adb shell ip -f inet addr show wlan0
adb shell ip -f inet addr show wlan0 2>nul | findstr "inet"
echo.

REM 检查Web服务状态
echo 2. 测试Web服务连接...
echo    访问: %BASE_URL%
curl -s -o nul -w "%%{http_code}" %BASE_URL% > temp_status.txt 2>nul
set /p HTTP_CODE=<temp_status.txt
del temp_status.txt >nul 2>nul

if "%HTTP_CODE%"=="200" (
    echo    ✅ Web服务正常运行 ^(HTTP %HTTP_CODE%^)
) else (
    echo    ❌ 无法连接到Web服务 ^(HTTP %HTTP_CODE%^)
    echo    请检查:
    echo    - 设备IP是否正确
    echo    - Web服务是否已启动
    echo    - 设备和电脑是否在同一网络
    pause
    exit /b 1
)
echo.

REM 测试获取配方列表
echo 3. 测试获取配方列表...
echo    GET %BASE_URL%/api/recipes
curl -s "%BASE_URL%/api/recipes" > recipes_response.json
type recipes_response.json
echo.

REM 测试获取统计数据
echo 4. 测试获取统计数据...
echo    GET %BASE_URL%/api/stats
curl -s "%BASE_URL%/api/stats"
echo.
echo.

REM 测试导入CSV文件
echo 5. 测试CSV文件导入...
if exist "test_recipes.csv" (
    echo    POST %BASE_URL%/api/import/recipes
    echo    文件: test_recipes.csv
    curl -s -X POST "%BASE_URL%/api/import/recipes" -F "file=@test_recipes.csv" > import_result.json
    type import_result.json
    echo.
    echo.

    echo    📊 导入结果: 请查看上方JSON响应
) else (
    echo    ⚠️  未找到 test_recipes.csv 文件
    echo    请确保文件存在于当前目录
)
echo.

REM 再次获取统计，验证导入效果
echo 6. 验证导入效果...
echo    GET %BASE_URL%/api/stats
curl -s "%BASE_URL%/api/stats"
echo.
echo.

REM 测试搜索功能
echo 7. 测试搜索功能...
echo    GET %BASE_URL%/api/recipes?search=Web测试
curl -s "%BASE_URL%/api/recipes?search=Web测试" > search_result.json
type search_result.json
echo.
echo.

REM 测试分类筛选
echo 8. 测试分类筛选...
echo    GET %BASE_URL%/api/recipes?category=香精
curl -s "%BASE_URL%/api/recipes?category=香精" > category_result.json
type category_result.json
echo.
echo.

REM 清理临时文件
del recipes_response.json >nul 2>nul
del import_result.json >nul 2>nul
del search_result.json >nul 2>nul
del category_result.json >nul 2>nul

echo ==========================================
echo 测试完成！
echo ==========================================
echo.
echo 📝 后续步骤：
echo 1. 在浏览器中访问: %BASE_URL%
echo 2. 查看导入的配方: %BASE_URL%/recipes
echo 3. 在Android应用中验证数据同步
echo.
echo 🔍 查看日志：
echo adb logcat ^| findstr "WebServerManager DBTest"
echo.

pause
