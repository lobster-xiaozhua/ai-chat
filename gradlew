#!/bin/sh

# gradlew — Gradle Wrapper 启动脚本（sh 兼容，Linux/macOS/GitHub Actions 通用）
# 核心：java -cp gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain <args>

set -e

# 1) 定位脚本所在目录（兼容 symlink）
APP_HOME=$( cd "$( dirname "$0" )" && pwd )
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$WRAPPER_JAR" ]; then
  echo "ERROR: 找不到 $WRAPPER_JAR" >&2
  exit 1
fi

# 2) 查找 Java 执行器（优先 JAVA_HOME，其次 PATH）
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD=$(command -v java 2>/dev/null || true)
  if [ -z "$JAVACMD" ]; then
    echo "ERROR: 找不到 java 命令。请安装 JDK 17+ 或设置 JAVA_HOME。" >&2
    exit 1
  fi
fi

APP_BASE_NAME=$(basename "$0")

# 3) 启动 GradleWrapperMain
#    DEFAULT_JVM_OPTS 内带引号的双参数需由 shell 字段拆分，因此不加双引号
exec "$JAVACMD" \
  $DEFAULT_JVM_OPTS \
  $JAVA_OPTS \
  $GRADLE_OPTS \
  "-Dorg.gradle.appname=$APP_BASE_NAME" \
  -classpath "$WRAPPER_JAR" \
  org.gradle.wrapper.GradleWrapperMain \
  "$@"
