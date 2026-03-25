# --- 核心配置 ---
$JDK = "$ENV:JAVA_HOME\bin"
$LIB = "C:\tools\Reverse\javassist.jar"
$NAME = "STSTrainer"

# --- 环境清理与准备 ---
if (Test-Path "build") { rm -r -Force "build" }
mkdir "build" | out-null

# --- 编译 ---
& "$JDK\javac.exe" -cp "$LIB" --release 8 -d "build" "$NAME.java"
if ($LASTEXITCODE -ne 0) { exit }

# --- 依赖处理与清单生成 ---
pushd "build"
& "$JDK\jar.exe" xf "$LIB"
if (Test-Path "META-INF") { rm -r -Force "META-INF" }

# 注入核心权限：必须包含空行和 Retransform 声明
$manifest = @"
Premain-Class: $NAME
Can-Redefine-Classes: true
Can-Retransform-Classes: true

"@
$manifest | Out-File -FilePath "m.txt" -Encoding ascii

# --- 打包 ---
& "$JDK\jar.exe" cvfm "../$NAME.jar" m.txt .
popd

# --- 结果提示 ---
write-host "[OK] $NAME.jar" -f Green