# 架构总览

## 系统架构图

```
┌──────────────────────────────────────────────────────────────────────────┐
│  UI Layer                                                                │
│  ┌──────────────┐    ┌──────────────────┐    ┌───────────────────────┐  │
│  │ MainScreen   │◄──►│  MainViewModel   │◄──►│ ScanUiState           │  │
│  │  (Composable)│    │  (状态管理)       │    │  Idle/Decoding/      │  │
│  │              │    │                   │    │  Success/Error       │  │
│  └──────────────┘    └────────┬─────────┘    └───────────────────────┘  │
│                               │                                          │
│                        调用 UseCase                                      │
├───────────────────────────────┼──────────────────────────────────────────┤
│  Domain Layer                 │                                          │
│                               ▼                                          │
│                    ┌─────────────────────┐                               │
│                    │  ScanImageUseCase    │                               │
│                    │  编排扫描流程        │                               │
│                    │  load → scan → map   │                               │
│                    └──┬──────┬───────────┘                               │
│                       │      │                                           │
│               ┌───────┘      └───────┐                                   │
│               ▼                      ▼                                   │
│  ┌──────────────────────┐  ┌──────────────────────┐                      │
│  │ ImageLoader (接口)    │  │ BarcodeScanner (接口) │                     │
│  │ 图片来源抽象          │  │ 扫描抽象              │                      │
│  └──────────────────────┘  └──────────────────────┘                      │
│                                                                          │
│  Domain 模型: ImageSource, ScanResult, BarcodeContentType               │
│  Repository 接口: ScanHistoryRepository                                  │
├───────────────────────────────┼──────────────────────────────────────────┤
│  Data Layer                   │                                          │
│                               ▼                                          │
│  ┌──────────────────────┐  ┌──────────────────────┐                     │
│  │ ImageLoaderImpl      │  │ BarcodeScannerImpl    │                     │
│  │ ContentResolver →    │  │ ML Kit BarcodeScan →  │                     │
│  │ Bitmap               │  │ List<BarcodeData>    │                     │
│  └──────────────────────┘  └──────────────────────┘                     │
│                                                                          │
│  ┌──────────────────────────────┐                                        │
│  │ InMemoryScanHistoryRepo      │                                        │
│  │ (domain 接口的内存实现)        │                                        │
│  └──────────────────────────────┘                                        │
└──────────────────────────────────────────────────────────────────────────┘
```

## 数据流总览

```
[用户] → 点"选择图片"
         → Photo Picker Activity (系统)
             → URI 回传
                 → ViewModel: 更新 UiState.Decoding
                 → ViewModel: viewModelScope.launch {
                     ScanImageUseCase.execute(ImageSource.GalleryUri(uri))
                 }
                     → ImageLoader.loadBitmap(uri) → Bitmap
                     → BarcodeScannerDataSource.scan(bitmap) → List<BarcodeData>
                     → BarcodeContentAnalyzer.analyze(rawValue) → BarcodeContentType
                     → 映射为 ScanResult.Success / Failure
                 → ViewModel: 更新 UiState.Success / Error
             → Compose 重组渲染
```

## 文件索引

| 规格文件 | 对应模块 | 层 |
|----------|----------|-----|
| [modules/image-source.md](modules/image-source.md) | ImageSource sealed interface | Domain |
| [modules/barcode-scanner.md](modules/barcode-scanner.md) | BarcodeScannerDataSource | Data |
| [modules/image-loader.md](modules/image-loader.md) | ImageLoader + BitmapUtils | Data |
| [modules/content-analyzer.md](modules/content-analyzer.md) | BarcodeContentAnalyzer | Util |
| [modules/scan-usecase.md](modules/scan-usecase.md) | ScanImageUseCase + ScanResult | Domain |
| [modules/scan-history.md](modules/scan-history.md) | ScanHistoryRepository (接口+实现) | Domain / Data |
| [modules/ui-state.md](modules/ui-state.md) | MainViewModel + ScanUiState | UI |
| [modules/ui-components.md](modules/ui-components.md) | MainScreen + 子组件 | UI |

## 数据模型总览

### 跨层传递的数据

```
UI (DecodedBarcodeUi)             Domain (ScanResult)               Data (BarcodeData)
┌─────────────────┐              ┌──────────────────┐             ┌───────────────────┐
│ rawValue: String │◄────────────│ 包含 List<        │◄───────────│ rawValue: String  │
│ boundingBox: Rect│             │  DecodedBarcodeUi>│             │ boundingBox: Rect │
│ format: String   │             │  + ImageSource    │             │ cornerPoints:     │
│ contentType:     │             │  + errorMessage   │             │   Point[]         │
│   BarcodeContent │             └──────────────────┘             │ format: Int       │
│   Type           │                                                              │
│ contentSummary:  │                                                              │
│   String         │                                                              │
└─────────────────┘                                                              └───────────────────┘
```

### ImageSource 继承关系

```
ImageSource (sealed interface)
├── GalleryUri(uri: Uri)       ← 当前唯一实现
└── CameraFrame(bitmap: Bitmap) ← 未来扩展预留
```
