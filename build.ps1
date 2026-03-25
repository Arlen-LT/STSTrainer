# --- 配置区 ---
$JDK_BIN = "$ENV:JAVA_HOME\bin"
$JAVASSIST_PATH = "C:\tools\Reverse\javassist.jar"
$AGENT_NAME = "STSTrainer"
$BUILD_DIR = "build_temp"

# --- 1. 环境自检 ---
if (-not (Test-Path $JAVASSIST_PATH)) { 
    Write-Error "找不到 Javassist！请检查路径: $JAVASSIST_PATH"
    exit 
}

# --- 2. 清理旧文件 ---
if (Test-Path $BUILD_DIR) { Remove-Item -Recurse -Force $BUILD_DIR }
New-Item -ItemType Directory -Path $BUILD_DIR | Out-Null

# --- 3. 编译 Agent ---
Write-Host "[1/3] 正在编译 $AGENT_NAME..." -ForegroundColor Cyan
& "$JDK_BIN\javac.exe" -cp "$JAVASSIST_PATH" --release 8 -d $BUILD_DIR "$AGENT_NAME.java"

if ($LASTEXITCODE -ne 0) { Write-Host "编译失败！" -ForegroundColor Red; exit }

# --- 4. 构建 Jar (解压并合并) ---
Write-Host "[2/3] 正在合并依赖 (Jar)..." -ForegroundColor Cyan
Push-Location $BUILD_DIR
# 解压 javassist 到当前编译目录
& "$JDK_BIN\jar.exe" xf "$JAVASSIST_PATH"
# 移除冗余的 Manifest
if (Test-Path "META-INF") { Remove-Item -Recurse -Force "META-INF" }

# 生成全新的 Manifest (确保末尾有换行)
"Premain-Class: $AGENT_NAME`n" | Out-File -FilePath "manifest.txt" -Encoding ascii

# 打包
& "$JDK_BIN\jar.exe" cvfm "../STSTrainer.jar" manifest.txt .
Pop-Location

# --- 5. 完成 ---
Write-Host "[3/3] 构建成功！文件位于: ./STSTrainer.jar" -ForegroundColor Green
# Remove-Item $BUILD_DIR -Recurse # 可选：清理临时目录