@echo off
rem Rozbor ulozenych snimku hry: rozbor.cmd SLOZKA [-Obrazky] [-Hra gta6] [-ZonaUkolu 0.915]
rem Vysledek rozbor.txt a rozbor.csv vedle snimku (viz pc\src\Rozbor.kt).
chcp 65001 >nul
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0rozbor.ps1" %*
