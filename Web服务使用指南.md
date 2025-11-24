# SmartDosing Web服务使用指南

**更新日期**: 2024-11-24
**版本**: v2.0 (数据库集成版本)

---

## 📊 概述

SmartDosing Web服务现已完全集成数据库持久化功能，所有通过Web端导入的配方都会自动保存到SQLite数据库中，与Android应用UI共享数据。

### 核心特性

- ✅ **数据持久化**: 导入的配方自动保存到数据库
- ✅ **Web管理界面**: 通过浏览器管理配方
- ✅ **文件导入**: 支持CSV和Excel格式
- ✅ **RESTful API**: 完整的CRUD操作支持
- ✅ **实时同步**: Web端和移动端数据实时共享

---

## 🚀 快速开始

### 1. 启动Web服务

Web服务在应用启动时自动启动（默认端口：8080）。

**查看启动日志**：
```bash
adb logcat | grep "WebServerManager"
```

**预期输出**：
```
Web服务器启动成功，端口: 8080
Web管理后台已启动: http://192.168.x.x:8080
```

### 2. 访问Web管理界面

在浏览器中打开：
```
http://[设备IP]:8080
```

**获取设备IP**：
```bash
# 方法1: 查看应用Toast提示
# 应用启动时会显示：Web管理后台已启动: http://x.x.x.x:8080

# 方法2: adb命令查看
adb shell ip -f inet addr show wlan0
```

### 3. Web界面导航

主要页面：
- **首页** - `http://[IP]:8080/` - 系统概览和快速操作
- **配方管理** - `http://[IP]:8080/recipes` - 查看和管理所有配方
- **导入配方** - `http://[IP]:8080/import` - 上传CSV/Excel文件
- **统计分析** - `http://[IP]:8080/stats` - 配方统计数据
- **模板管理** - `http://[IP]:8080/templates` - 导入模板

---

## 📥 配方导入指南

### 方式1：使用Web界面导入

1. 访问导入页面：`http://[IP]:8080/import`
2. 点击"选择文件"按钮
3. 选择CSV或Excel文件
4. 点击"上传导入"
5. 查看导入结果

**导入成功后**：
- 配方自动保存到数据库
- 可在Android应用的"配方管理"页面看到
- 可在Web端的"配方管理"页面看到

### 方式2：使用API导入

**端点**: `POST /api/import/recipes`

**示例**（使用curl）：
```bash
curl -X POST \
  http://[IP]:8080/api/import/recipes \
  -F "file=@配方数据.csv"
```

**示例**（使用Python）：
```python
import requests

url = "http://[IP]:8080/api/import/recipes"
files = {'file': open('配方数据.csv', 'rb')}
response = requests.post(url, files=files)
print(response.json())
```

---

## 📋 CSV文件格式规范

### 基础格式

CSV文件需包含以下列（第一行为标题行）：

```csv
配方编码,配方名称,分类,子分类,客户,批次号,版本,描述,材料1,重量1,单位1,序号1,备注1,材料2,重量2,单位2,序号2,备注2,...
```

### 示例文件

```csv
配方编码,配方名称,分类,子分类,客户,批次号,版本,描述,材料1,重量1,单位1,序号1,备注1,材料2,重量2,单位2,序号2,备注2
RECIPE001,苹果香精,香精,水果香精,ABC公司,BATCH001,1.0,用于饮料,苹果酯,100,g,1,主要成分,乙醇,50,g,2,溶剂
RECIPE002,草莓香精,香精,水果香精,XYZ公司,BATCH002,1.0,用于糖果,草莓酮,80,g,1,主要成分,丙二醇,40,g,2,溶剂
```

### 字段说明

| 字段 | 必填 | 说明 | 示例 |
|------|------|------|------|
| 配方编码 | ✅ | 唯一标识 | RECIPE001 |
| 配方名称 | ✅ | 配方名称 | 苹果香精 |
| 分类 | ✅ | 一级分类 | 香精 |
| 子分类 | ❌ | 二级分类 | 水果香精 |
| 客户 | ❌ | 客户名称 | ABC公司 |
| 批次号 | ❌ | 生产批次 | BATCH001 |
| 版本 | ❌ | 配方版本 | 1.0 |
| 描述 | ❌ | 配方说明 | 用于饮料 |
| 材料N | ✅ | 材料名称 | 苹果酯 |
| 重量N | ✅ | 材料重量 | 100 |
| 单位N | ✅ | 重量单位 | g |
| 序号N | ✅ | 投料顺序 | 1 |
| 备注N | ❌ | 材料备注 | 主要成分 |

**注意事项**：
- 每个配方至少包含1种材料（材料1-5的列）
- 材料数量可以扩展（材料1-5, 材料6-10等）
- 配方编码必须唯一，重复会导入失败
- CSV文件必须使用UTF-8编码（避免中文乱码）

---

## 🔌 完整API文档

### 配方管理API

#### 1. 获取所有配方
```http
GET /api/recipes
```

**查询参数**：
- `category`: 按分类筛选
- `search`: 搜索关键词

**响应示例**：
```json
{
  "success": true,
  "data": [
    {
      "id": "uuid-123",
      "code": "RECIPE001",
      "name": "苹果香精",
      "category": "香精",
      "totalWeight": 150.0,
      "materials": [...]
    }
  ]
}
```

#### 2. 获取单个配方
```http
GET /api/recipes/{id}
```

#### 3. 创建配方
```http
POST /api/recipes
Content-Type: application/json

{
  "code": "RECIPE003",
  "name": "柠檬香精",
  "category": "香精",
  "materials": [
    {
      "name": "柠檬醛",
      "weight": 80.0,
      "unit": "g",
      "sequence": 1
    }
  ]
}
```

#### 4. 更新配方
```http
PUT /api/recipes/{id}
Content-Type: application/json

{配方数据...}
```

#### 5. 删除配方
```http
DELETE /api/recipes/{id}
```

#### 6. 标记配方使用
```http
POST /api/recipes/{id}/use
```

#### 7. 获取统计数据
```http
GET /api/stats
```

**响应示例**：
```json
{
  "success": true,
  "data": {
    "totalRecipes": 25,
    "categoryCounts": {
      "香精": 15,
      "化妆品": 10
    },
    "statusCounts": {...},
    "priorityCounts": {...}
  }
}
```

### 导入API

#### 导入配方文件
```http
POST /api/import/recipes
Content-Type: multipart/form-data

file: [CSV或Excel文件]
```

**响应示例**：
```json
{
  "success": true,
  "data": {
    "total": 10,
    "success": 8,
    "failed": 2,
    "successRecipes": [...],
    "errors": [
      {
        "row": 3,
        "reason": "配方编码重复"
      }
    ]
  }
}
```

---

## 🧪 测试步骤

### 完整测试流程

1. **启动应用并检查Web服务**
   ```bash
   # 查看日志确认服务启动
   adb logcat | grep "WebServerManager\|WebService\|DBTest"
   ```

2. **访问Web管理界面**
   - 打开浏览器访问 `http://[设备IP]:8080`
   - 应该看到SmartDosing管理后台首页

3. **测试配方导入**

   **方法A：使用测试CSV文件**

   创建 `test_recipes.csv`：
   ```csv
   配方编码,配方名称,分类,子分类,客户,批次号,版本,描述,材料1,重量1,单位1,序号1,备注1,材料2,重量2,单位2,序号2,备注2
   WEB001,Web测试配方1,香精,水果,测试客户,B001,1.0,Web导入测试,材料A,100,g,1,主料,材料B,50,g,2,辅料
   WEB002,Web测试配方2,香精,花香,测试客户,B002,1.0,Web导入测试,材料C,80,g,1,主料,材料D,40,g,2,辅料
   ```

   - 访问 `http://[IP]:8080/import`
   - 上传 `test_recipes.csv`
   - 查看导入结果

   **方法B：使用API测试**
   ```bash
   curl -X POST http://[设备IP]:8080/api/import/recipes \
     -F "file=@test_recipes.csv"
   ```

4. **验证数据库持久化**

   **方法1：通过Web界面验证**
   - 访问 `http://[IP]:8080/recipes`
   - 应该能看到刚导入的配方

   **方法2：通过Android应用验证**
   - 打开应用的"配方管理"页面
   - 搜索"WEB001"或"Web测试配方"
   - 应该能找到刚导入的配方

   **方法3：通过Logcat验证**
   ```bash
   adb logcat | grep "DBTest"
   ```
   - 应该能看到数据库初始化日志
   - 配方总数应该包含新导入的配方

5. **测试CRUD操作**

   **读取配方**：
   ```bash
   curl http://[设备IP]:8080/api/recipes
   ```

   **删除配方**：
   ```bash
   curl -X DELETE http://[设备IP]:8080/api/recipes/[配方ID]
   ```

   **再次验证**：
   - 刷新Web界面，配方应该消失
   - 打开Android应用，配方也应该消失

---

## 🔍 故障排查

### 问题1：无法访问Web服务

**症状**：浏览器无法打开 `http://[IP]:8080`

**排查步骤**：
1. 检查Web服务是否启动
   ```bash
   adb logcat | grep "WebServerManager"
   # 应该看到：Web服务器启动成功，端口: 8080
   ```

2. 确认设备IP正确
   ```bash
   adb shell ip -f inet addr show wlan0
   ```

3. 检查端口是否被占用
   ```bash
   adb shell netstat -an | grep 8080
   ```

4. 确保设备和电脑在同一网络

**解决方案**：
- 重启应用
- 检查防火墙设置
- 尝试使用电脑的浏览器和手机的浏览器

### 问题2：导入配方后看不到数据

**症状**：导入成功，但在Android应用中看不到

**排查步骤**：
1. 检查导入结果
   ```bash
   curl http://[设备IP]:8080/api/stats
   ```

2. 查看数据库日志
   ```bash
   adb logcat | grep "DBTest"
   ```

3. 检查Android应用是否刷新

**解决方案**：
- 在Android应用中切换页面或下拉刷新
- 检查搜索/筛选条件是否过滤了新配方
- 重启应用重新加载数据

### 问题3：CSV导入失败

**症状**：导入时显示错误

**常见错误**：
- "配方编码重复" - 数据库中已存在相同编码的配方
- "必填字段缺失" - CSV缺少必要的列
- "格式错误" - CSV格式不正确

**解决方案**：
1. 检查CSV文件编码（必须UTF-8）
2. 确保第一行是标题行
3. 检查配方编码的唯一性
4. 验证必填字段不为空

### 问题4：数据不同步

**症状**：Web端和移动端数据不一致

**解决方案**：
- 两端都使用DatabaseRecipeRepository（已完成）
- 刷新页面或重新进入页面
- 检查是否有异常日志

---

## 📊 性能测试建议

### 导入性能测试

测试不同数量级的配方导入：

**小数据量** (10条)：
```bash
# 预期：< 1秒
time curl -X POST http://[IP]:8080/api/import/recipes -F "file=@10_recipes.csv"
```

**中数据量** (100条)：
```bash
# 预期：< 5秒
time curl -X POST http://[IP]:8080/api/import/recipes -F "file=@100_recipes.csv"
```

**大数据量** (1000条)：
```bash
# 预期：< 30秒
time curl -X POST http://[IP]:8080/api/import/recipes -F "file=@1000_recipes.csv"
```

### 查询性能测试

```bash
# 测试获取所有配方
time curl http://[IP]:8080/api/recipes

# 测试搜索
time curl "http://[IP]:8080/api/recipes?search=香精"

# 测试分类查询
time curl "http://[IP]:8080/api/recipes?category=香精"
```

---

## 🎯 最佳实践

### 导入大量配方

1. **分批导入**：建议每批不超过500条
2. **验证数据**：导入前检查CSV格式
3. **备份数据**：重要数据先备份
4. **监控日志**：导入时监控Logcat

### 数据管理

1. **定期清理**：删除测试数据
2. **编码规范**：使用统一的编码格式（如：PROD-2024-001）
3. **分类管理**：合理使用分类和子分类
4. **版本控制**：重要配方记录版本号

### 安全建议

1. **局域网使用**：仅在可信网络使用
2. **定期更新**：及时更新应用版本
3. **访问控制**：注意Web服务的访问权限

---

## 📝 附录

### A. 完整API列表

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/recipes` | GET | 获取配方列表 |
| `/api/recipes/{id}` | GET | 获取配方详情 |
| `/api/recipes` | POST | 创建配方 |
| `/api/recipes/{id}` | PUT | 更新配方 |
| `/api/recipes/{id}` | DELETE | 删除配方 |
| `/api/recipes/{id}/use` | POST | 标记使用 |
| `/api/stats` | GET | 统计数据 |
| `/api/import/recipes` | POST | 导入配方 |
| `/api/templates` | GET | 模板列表 |
| `/api/templates/{id}` | GET | 模板详情 |
| `/api/templates/{id}/download` | GET | 下载模板 |

### B. 数据库Schema

配方数据保存在SQLite数据库中：
- **数据库名**: `smartdosing.db`
- **位置**: `/data/data/com.example.smartdosing/databases/`
- **主表**: `recipes`, `materials`, `recipe_tags`

可使用Android Studio的Database Inspector查看。

### C. 示例代码

**JavaScript前端示例**：
```javascript
// 导入配方
async function importRecipes(file) {
  const formData = new FormData();
  formData.append('file', file);

  const response = await fetch('http://192.168.1.100:8080/api/import/recipes', {
    method: 'POST',
    body: formData
  });

  const result = await response.json();
  console.log('导入结果:', result);
}

// 获取配方列表
async function getRecipes() {
  const response = await fetch('http://192.168.1.100:8080/api/recipes');
  const result = await response.json();
  console.log('配方列表:', result.data);
}
```

---

## ✅ 验证清单

完成以下步骤确认Web服务正常：

- [ ] 应用启动，看到"Web管理后台已启动"提示
- [ ] 浏览器能访问 `http://[IP]:8080`
- [ ] 能看到Web管理界面首页
- [ ] 能访问"配方管理"页面并看到配方列表
- [ ] 能访问"导入配方"页面
- [ ] 成功上传并导入CSV文件
- [ ] 导入后能在Web端"配方管理"看到新配方
- [ ] 导入后能在Android应用"配方管理"看到新配方
- [ ] 在Android应用中删除配方后，Web端也看不到了
- [ ] 所有API端点返回正确的JSON响应

---

**文档版本**: v2.0
**最后更新**: 2024-11-24
**维护者**: Claude AI

**相关文档**：
- [数据库集成说明](README_数据库集成.md)
- [下一步工作计划](下一步工作计划.md)
- [执行记录](执行记录.md)
