# FileScanner Android 应用项目指南

## 项目概述

FileScanner是一款功能实用的Android文档扫描应用，使用Kotlin开发，支持通过相机拍照或从相册选择图片进行文档扫描处理。应用提供高质量的图像增强、边缘检测和二值化功能，帮助用户快速将纸质文档数字化。

**主要特性：**
- 📸 相机扫描：直接通过相机拍摄文档并进行实时处理
- 📁 相册导入：从设备相册选择现有图片进行扫描处理
- ✨ 图像增强：自动进行灰度转换、对比度增强和二值化处理
- 📏 边缘检测：智能检测文档边缘，支持手动调整边缘点
- 🎨 参数调节：支持调整阈值、对比度、亮度和模糊参数
- 🔄 图片编辑：支持图片旋转（左转/右转）功能
- 📚 历史记录：自动保存扫描历史，方便查看和管理
- 🗑️ 批量管理：支持长按选择多张图片并批量删除
- 📱 自适应布局：支持不同屏幕尺寸的Android设备

## 技术栈

- **开发语言:** Kotlin
- **基础框架:** AndroidX, Material3
- **图像处理:** OpenCV 4.5.3.0
- **相机API:** Android CameraX
- **最低API级别:** 24 (Android 7.0 Nougat)
- **目标API级别:** 36 (Android 14)
- **编译SDK:** 36
- **构建工具:** Gradle Kotlin DSL

## 项目结构

```
app/
├── src/
│   ├── main/
│   │   ├── java/com/max/filescaner/
│   │   │   ├── MainActivity.kt          # 主界面
│   │   │   ├── ImageProcessingActivity.kt # 图像处理界面
│   │   │   ├── ImageDetailActivity.kt   # 图片详情界面
│   │   │   ├── HistoryActivity.kt       # 历史记录界面
│   │   │   └── EdgePreviewImageView.kt  # 自定义边缘预览组件 (可能不存在)
│   │   ├── res/
│   │   │   ├── layout/                  # 布局文件
│   │   │   ├── values/                  # 字符串、样式等资源
│   │   │   ├── xml/                     # 配置文件
│   │   │   └── mipmap/                  # 应用图标
│   │   └── AndroidManifest.xml          # 应用清单
│   ├── test/                            # 单元测试
│   └── androidTest/                     # 仪器化测试
├── build.gradle.kts                     # 应用模块构建配置
└── proguard-rules.pro                   # 代码混淆规则
```

## 核心模块

### MainActivity
- 应用入口点
- 提供相机拍照、相册选择和历史记录访问功能
- 处理权限请求和相机调用
- 管理临时文件和图片存储

### ImageProcessingActivity
- 核心图像处理逻辑
- 实现灰度转换、对比度增强、边缘检测和二值化算法
- 提供处理前后对比预览
- 支持实时参数调节和边缘点手动调整
- 使用自定义边缘预览组件进行边缘预览

### ImageDetailActivity
- 显示图片全屏预览
- 提供图片旋转编辑功能
- 支持再次处理图片
- 保存编辑后的图片

### HistoryActivity
- 以网格布局展示所有扫描历史
- 支持长按进入多选模式
- 实现批量删除功能
- 提供单张图片查看入口

## 构建和部署

### 环境要求
- Android Studio 2022.3.1或更高版本
- JDK 11
- Android SDK 36
- Kotlin插件最新版本

### 构建命令
```bash
# 使用Android Studio打开项目
# 或使用命令行构建
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug

# 构建发布版本
./gradlew assembleRelease

# 清理项目
./gradlew clean

# 运行Lint检查
./gradlew lint

# 运行测试
./gradlew test
```

### 构建配置说明
- **Debug版本:** 代码混淆已启用，调试模式已禁用
- **Release版本:** 代码混淆和资源压缩均已启用
- **ABI过滤:** 支持arm64-v8a和armeabi-v7a架构
- **ViewBinding:** 已启用，用于类型安全的视图访问

## 文件存储

- **扫描历史存储路径:** `/storage/emulated/0/Android/data/com.max.filescaner/files/Pictures/FileScanner/`
- **文件命名格式:** `scanned_YYYYMMdd_HHmmss.jpg`（基于时间戳）
- **临时文件:** 存储在应用缓存目录，确保不占用过多存储空间

## 权限要求

应用需要以下权限才能正常工作：
- **相机权限:** 用于拍摄文档照片
- **存储权限:**
  - Android 10及以下：读写外部存储权限
  - Android 11及以上：读取媒体文件权限（使用分区存储）

## 图像处理流程

1. **输入获取:** 通过相机拍摄或相册选择获取原始图像
2. **边缘检测:** 自动识别文档边缘，支持手动调整边缘点
3. **图像裁剪:** 根据边缘点进行透视变换和裁剪
4. **预处理:** 灰度转换，将彩色图像转换为灰度图
5. **增强处理:** 提升对比度和亮度，使文档内容更加清晰
6. **二值化:** 将图像转换为黑白二值图像，突出文字内容
7. **实时预览:** 显示处理前后对比，支持参数实时调节
8. **保存管理:** 保存处理后的图像到应用私有存储目录

## 测试策略

### 单元测试
- 位于 `app/src/test/java/` 目录
- 使用JUnit 4进行本地单元测试
- 示例：`ExampleUnitTest.kt` 包含基本的加法运算测试

### 仪器化测试
- 位于 `app/src/androidTest/java/` 目录
- 使用Espresso和JUnit 4进行UI测试
- 示例：`ExampleInstrumentedTest.kt` 验证应用上下文

### 测试执行
```bash
# 运行所有测试
./gradlew test

# 运行Debug版本的仪器化测试
./gradlew connectedDebugAndroidTest

# 运行单元测试
./gradlew testDebugUnitTest
```

## 开发最佳实践

### 代码规范
- 采用Kotlin官方编码规范
- 使用ViewBinding进行视图绑定，避免findViewById
- 遵循Android组件生命周期管理
- 合理使用Kotlin扩展函数提升代码可读性

### 性能优化
- 图像处理使用OpenCV库，注意内存管理
- 实现图片按需加载，避免内存溢出
- 使用适当的图片压缩策略
- 及时回收Bitmap资源防止内存泄漏

### 安全考虑
- 使用FileProvider安全分享文件
- 遵循Android权限管理最佳实践
- 代码混淆保护应用逻辑

### 图像处理功能
- **灰度转换:** 将彩色图像转换为灰度图，简化后续处理
- **对比度增强:** 提升图像对比度，使文档内容更加清晰
- **边缘检测:** 识别文档边缘，用于透视校正
- **二值化:** 将图像转换为黑白二值图像，突出文字内容
- **参数调节:** 实时调节阈值、对比度、亮度和模糊参数

## 许可证

本软件根据 GNU 通用公共许可证第三版授权。然而，以下附加限制适用：
- 禁止对本软件进行任何形式的商业使用，除非获得版权持有人的事先书面许可。
- "商业使用"包括但不限于：将本软件集成到商业产品中、使用它来提供商业服务、或将其作为营利活动的一部分进行分发。