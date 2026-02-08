@echo off
echo Compiling all Java files...
javac -cp "lib/*" *.java

if %ERRORLEVEL% NEQ 0 (
    echo Compilation failed!
    pause
    exit /b 1
)

echo Running game...
java -cp "lib/*;." Game
pause