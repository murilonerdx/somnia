@echo off
echo [PACKAGER] Building Somnia Engine Standalone JAR...
call gradlew.bat shadowJar

if exist build\libs\somnia-engine-1.0-SNAPSHOT-all.jar (
    echo [PACKAGER] Success! JAR created.
    echo [PACKAGER] Making somnia_app portable...
    
    copy build\libs\somnia-engine-1.0-SNAPSHOT-all.jar src\main\resources\somnia_app\somnia-engine.jar
    
    echo @echo off > src\main\resources\somnia_app\somnia.bat
    echo REM Somnia Standalone Launcher >> src\main\resources\somnia_app\somnia.bat
    echo java -jar somnia-engine.jar . >> src\main\resources\somnia_app\somnia.bat
    
    echo [PACKAGER] DONE. Your folder 'src/main/resources/somnia_app' is now SELF-EXECUTABLE.
    echo [PACKAGER] Just go inside and run 'somnia.bat'.
) else (
    echo [PACKAGER] FAILED. Check build output.
)
