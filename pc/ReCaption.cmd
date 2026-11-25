@echo off
rem ReCaption pro pocitac - okno aplikace (dvojklik).
chcp 65001 >nul
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0aplikace.ps1"
