<div align="center">

# 翰墨阅读器 · NovelReader

**一个纯本地的 Android 小说 + 漫画阅读器**
不联网、无广告、零权限，只读你自己手机里的文件

![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![minSdk](https://img.shields.io/badge/minSdk-21%20(Android%205.0)-3DDC84?logo=android&logoColor=white)
![permissions](https://img.shields.io/badge/permissions-0-success)

</div>

![宣传图](宣传图.png)

## 为什么做这个

市面上的阅读器要么强制联网、塞满广告，要么一上来就要通讯录、存储、网络一堆权限。所以自己写了一个。

- **零权限**：`AndroidManifest.xml` 里没有声明任何权限，连 `INTERNET` 都没有。文件通过系统 SAF 选择器授权访问，因此也不需要申请存储权限。
- **纯本地**：没有书城、没有登录、没有埋点，所有数据只存在手机本地。
- **两种看法**：小说和漫画在同一个 App 里，书架用两个标签页分开管。

## 功能

### 📖 小说阅读

| 能力 | 说明 |
|---|---|
| 格式 | TXT、EPUB |
| 编码 | UTF-8 / GBK 自动识别 |
| 自动分章 | 识别 `第X章 / 第X节 / 第X回`、`序章`、`楔子`、`番外` 等标题；EPUB 按 `container.xml → OPF → spine` 解析章节顺序 |
| 阅读设置 | 文字大小、文字亮度（0.4~1.3）、12 种阅读背景、6 种主题色 |
| 书签 | 添加 / 列表 / 跳转 / 删除 |
| 目录 | 章节列表跳转 |

### 🖼️ 漫画阅读

| 导入方式 | 分话规则 |
|---|---|
| **CBZ / ZIP 压缩包** | 按包内目录自动分话，图片全在根目录则算一章 |
| **PDF 文档** | 整份文档一章，用系统 `PdfRenderer` 逐页渲染 |
| **图片文件夹** | 按子文件夹自动分话，**不复制文件**，直接按目录 URI 懒加载 |

- 页码**自然序**排序：`2.jpg` 一定排在 `10.jpg` 前面，不会出现 1、10、2 的乱序
- 左右滑动翻页、进度条拖动、目录跳话，黑底 `Fit` 显示
- 按屏幕尺寸采样解码 + LRU 缓存 + 相邻页预加载

### 🗄️ 书架

- **小说 / 漫画** 双标签页，各自独立空状态引导；从阅读器返回仍停留在原标签页
- 按最近阅读排序；漫画卡片显示**第一页真实封面** + 「漫」角标
- 长按删除，连带本地文件、章节、页面、书签、目录授权一起清理

### 🎨 外观

- 默认深色，水墨国风配色
- 3 种模式：跟随系统 / 浅色 / 深色
- 12 种阅读背景：米白、纯白、淡青、淡黄、淡绿、淡粉 / 黑纸、深灰、墨蓝、墨绿、棕褐、藏青
- 6 种主题色：黛青、朱砂、墨绿、群青、赭红、青绿

## 操作手势

**小说阅读器**

| 操作 | 效果 |
|---|---|
| 点击顶部 18% 区域 | 打开目录 |
| 点击左侧 30% | 上一章 |
| 点击右侧 30% | 下一章 |
| 长按 | 呼出菜单（目录 / 书签 / 文字大小 / 文字亮度） |
| 从左向右滑动 | 退出阅读器 |

**漫画阅读器**

| 操作 | 效果 |
|---|---|
| 左右滑动 | 翻页 |
| 点击左侧 1/3 / 右侧 1/3 | 上一页 / 下一页 |
| 点击中间 | 显隐工具栏（目录、进度条） |

## 技术栈

| 组件 | 版本 / 说明 |
|---|---|
| 语言 | Kotlin 1.9.24 |
| UI | Jetpack Compose（Material 3，BOM 2024.06.00） |
| 架构 | MVVM + Repository |
| 数据库 | Room 2.6.1（KSP） |
| 偏好存储 | DataStore Preferences |
| 导航 | Navigation Compose |
| 构建 | AGP 8.4.2 / Gradle 8.6 / JDK 17 |
| 最低系统 | Android 5.0（API 21） |
| 目标系统 | Android 14（API 34） |

## 实现说明

几个值得一提的实现选择。

**1. 不引入第三方图片加载库**

漫画解码全部手写：`BitmapFactory` 两段式采样（先 `inJustDecodeBounds` 读尺寸，再按需设 `inSampleSize` 解码），配合 `LruCache` 按 `maxMemory / 6` 计算缓存上限。省掉 Coil / Glide 依赖，APK 更小，也更好控制大图内存。

**2. 图片文件夹不复制、不预扫描落盘**

用 `OpenDocumentTree` 拿到树 URI 并申请持久化读取权限，扫码时只把每张图的**文档 URI** 写进数据库，翻到哪页才解码哪页。几百 MB 的漫画目录可以秒导入，不占额外空间。

> 踩坑记录：树 URI 本身不能直接 `ContentResolver.query`，必须先 `buildDocumentUriUsingTree()` 转成文档 URI，否则 `DocumentsProvider` 会抛 `Invalid URI`。

**3. PDF 直接用系统能力**

`android.graphics.pdf.PdfRenderer`（API 21+）逐页渲染到白底 `ARGB_8888` 位图并按需缩放，不依赖 PDFium 或任何第三方库。`PdfRenderer` 非线程安全，用锁串行化访问。

**4. Room 增量迁移**

数据库 v2 → v3 新增 `comic_pages` 表时写了真实的 `Migration`，而不是 `fallbackToDestructiveMigration()`。升级后原有书籍、章节和阅读进度全部保留。

**5. 章节分组的自然排序**

压缩包和图片文件夹都按父目录分话，目录名与文件名用自定义比较器排序：遇到连续数字按数值比较，其余按不区分大小写的字符比较。这样 `第2话` 排在 `第10话` 前面。

## 项目结构

```
NovelReader/
├── gradlew / gradlew.bat
├── settings.gradle.kts / build.gradle.kts
├── gradle/libs.versions.toml            # 依赖版本目录
├── test_comics/                         # 测试素材（CBZ / PDF / 图片文件夹）
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml          # 未声明任何权限
        ├── res/
        └── java/com/example/novelreader/
            ├── NovelReaderApp.kt        # Application + 依赖容器
            ├── MainActivity.kt          # 入口 + 导航
            ├── data/
            │   ├── BookRepository.kt    # 小说导入 / 进度 / 书签
            │   ├── ComicRepository.kt   # 漫画导入（CBZ / PDF / 图片文件夹）
            │   ├── ComicPageLoader.kt   # 页面解码 + LRU 缓存
            │   ├── SettingsRepository.kt
            │   ├── db/                  # Room 实体 / DAO / 迁移
            │   ├── model/               # 书籍类型枚举
            │   └── parser/              # TXT / EPUB 解析
            └── ui/
                ├── theme/               # 主题、阅读背景、主题色
                ├── viewmodel/
                ├── ViewModelFactory.kt
                └── screen/              # 书架 / 小说阅读器 / 漫画阅读器 / 设置 / 关于
```

## 编译运行

**环境**：Android Studio（Ladybug 或更新）、JDK 17、Android SDK Platform 34。

```bash
git clone https://github.com/Zayo-x/NovelReader.git
cd NovelReader
```

用 Android Studio 打开根目录（含 `settings.gradle.kts` 那一层），等待 Gradle 同步完成后点工具栏绿色 ▶ 运行。

命令行构建：

```bash
./gradlew assembleDebug
```

产物位于 `app/build/outputs/apk/debug/app-debug.apk`，传到手机安装即可（需允许「安装未知来源应用」）。

> 首次构建需要联网下载依赖。国内网络可在 `settings.gradle.kts` 里把 Maven 仓库换成阿里云镜像加速。

## 使用说明

1. **导入小说**：书架「小说」标签页 → 右下角 `+` → 选择 TXT / EPUB
2. **导入漫画**：书架「漫画」标签页 → 右下角 `+` → 三选一（压缩包 / PDF / 图片文件夹）
3. **图片文件夹**：选完目录后系统会询问授权，点「允许」。建议在 `Pictures/` 下单独建一个目录，每个子文件夹放一话
4. **删除**：长按书籍卡片

`test_comics/` 里带了测试素材：

| 文件 | 内容 |
|---|---|
| `测试漫画.cbz` | 2 话共 18 页，第 1 话特意做了 12 页，用于验证自然排序 |
| `测试PDF.pdf` | 6 页 |
| `图集/` | 7 张图 + 2 个子目录，用于测试图片文件夹导入（需自行推到手机 `Pictures/` 下） |

## 已知限制

- 漫画目前只有横向翻页模式，没有纵向滚动、双页并排
- 图片文件夹是按 URI 即时读取的，原目录被删除后这本书会打不开（不会影响其他数据）
- PDF 只支持翻页阅读，不支持文字重排
- 小说仅支持 TXT / EPUB，暂不支持 MOBI、AZW3

## 版权与免责

本项目仅供学习交流，仓库中不含任何书籍内容。请仅使用你**有权阅读**的文件，不要用于传播盗版内容。
