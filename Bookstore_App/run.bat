@echo off
title Bookstore App Launcher
cls
echo ==========================================================
echo           📚  BOOKSTORE CORE JAVA LAUNCHER  📚
echo ==========================================================
echo.
echo Compiling source files...
javac -encoding utf-8 -d out/production/Bookstore_App -sourcepath src src/com/mycompany/bookstore/testing/BookClientWebServer.java src/com/mycompany/bookstore/testing/BookClientInteractive.java src/com/mycompany/bookstore/testing/BookClient.java

if %errorlevel% neq 0 (
    echo.
    echo ❌ [ERROR] Compilation failed. Please check the Java files.
    pause
    exit /b %errorlevel%
)

echo.
echo Class files successfully compiled!
echo.
echo Select the mode to execute:
echo [1] Run Modern Web Server on Localhost (Web Browser UI)
echo [2] Run Interactive CLI Client (Create / Search Books)
echo [3] Run Hardcoded Default Client (Pre-configured demonstration)
echo [4] Exit
echo.
set /p choice="👉 Choose option (1-4): "

if "%choice%"=="2" (
    echo.
    echo Running Interactive CLI Client...
    echo.
    java -cp out/production/Bookstore_App com.mycompany.bookstore.testing.BookClientInteractive
) else if "%choice%"=="3" (
    echo.
    echo Running Hardcoded Client...
    echo.
    java -cp out/production/Bookstore_App com.mycompany.bookstore.testing.BookClient
) else if "%choice%"=="4" (
    echo Goodbye!
    exit /b 0
) else (
    echo.
    echo Running Modern Web Server on Localhost...
    echo.
    start http://localhost:8080/
    java -cp out/production/Bookstore_App com.mycompany.bookstore.testing.BookClientWebServer
)
echo.
echo ==========================================================
echo Execution complete. Press any key to exit.
echo ==========================================================
pause > nul
