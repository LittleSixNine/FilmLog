# 第三方组件与资源声明

本清单用于记录当前实际依赖与素材来源，不能代替各权利人的许可。项目自有代码采用 GNU GPL v3.0（GPL-3.0-only），见 LICENSE；第三方内容保留各自许可，不因项目许可而改变。

| 类别 | 当前内容 | 来源与许可记录 | 状态 |
|---|---|---|---|
| EXIF依赖 | AndroidX ExifInterface1.4.2 | `app/libs/exifinterface-LICENSE.txt`；来源和SHA256见`app/libs/README.md` | 保留现有Apache-2.0许可 |
| 注解依赖 | AndroidX annotation-jvm1.8.1、JSpecify1.0.0 | 固定JAR与官方Maven地址见`app/libs/README.md` | 已保留 Apache-2.0 文本：`annotation-LICENSE.txt`、`jspecify-LICENSE.txt`，位于 `app/libs/` |
| 图标字体 | Phosphor Regular／Fill／Bold | `app/assets/Phosphor-LICENSE.txt` | 保留现有MIT许可及版权声明 |
| 正文字体 | Newsreader | `app/assets/fonts/newsreader/OFL.txt` | 保留现有SIL OFL文本 |
| 胶片及相机目录 | pellica-catalog、filmer-catalog等 | 由项目历史研究整理；原始证据在本地归档 | 公开再分发范围待核 |
| 胶片封面和说明 | film-artwork及相关目录 | 来源与型号关联在素材JSON中保留 | 公开再分发范围待核 |
| 品牌／相机标识 | film-brands、camera-logos | 逐图来源在素材索引中保留，历史许可资料在本地归档 | 不以软件项目许可代替品牌授权 |
| 应用及界面资源 | `app/res/` | 项目现有图标与派生资源 | 公开前复核原始来源与可分发范围 |

素材索引中的历史响应文件名属于来源标识，不是构建时依赖。公开仓库不会附带原应用安装包、原始接口响应、提取工具、私钥或研究档案。本次未替换图片或改变应用视觉，仅整理现有文件。
