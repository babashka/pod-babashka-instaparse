@echo off

if "%GRAALVM_HOME%"=="" (
    echo Please set GRAALVM_HOME
    exit /b
)

set JAVA_HOME=%GRAALVM_HOME%
set PATH=%GRAALVM_HOME%\bin;%PATH%

set /P VERSION=< resources\POD_BABASHKA_INSTAPARSE_VERSION
echo Building version %VERSION%

if "%GRAALVM_HOME%"=="" (
echo Please set GRAALVM_HOME
exit /b
)

call clojure -T:build uber

call %GRAALVM_HOME%\bin\native-image.cmd ^
  "-jar" "pod-babashka-instaparse.jar" ^
  "-H:Name=pod-babashka-instaparse" ^
  "-H:+ReportExceptionStackTraces" ^
  "--features=clj_easy.graal_build_time.InitClojureClasses" ^
  "--verbose" ^
  "--no-fallback" ^
  "-J-Xmx3g"

if %errorlevel% neq 0 exit /b %errorlevel%
