@rem gradlew.bat — Gradle Wrapper 启动脚本（Windows cmd 专用）
@echo off
setlocal

rem 1) 定位脚本所在目录
set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_HOME=%DIRNAME%

set WRAPPER_JAR="%APP_HOME%gradle\wrapper\gradle-wrapper.jar"
if not exist %WRAPPER_JAR% (
  echo ERROR: 找不到 %WRAPPER_JAR%
  exit /b 1
)

rem 2) 查找 java.exe
set JAVA_EXE=java.exe
if defined JAVA_HOME (
  set JAVA_EXE=%JAVA_HOME%\bin\java.exe
  if not exist "%JAVA_EXE%" (
    echo ERROR: JAVA_HOME 设置无效：%JAVA_HOME%
    exit /b 1
  )
) else (
  where java >nul 2>&1 || (
    echo ERROR: 找不到 java.exe，请安装 JDK 17+ 或设置 JAVA_HOME
    exit /b 1
  )
)

rem 3) 启动 GradleWrapperMain
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% ^
  "-Dorg.gradle.appname=%~nx0" ^
  -classpath %WRAPPER_JAR% ^
  org.gradle.wrapper.GradleWrapperMain %*

:end
endlocal
