@echo off
cd /d "%~dp0"
java -jar target\ecommerce-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev > backend.log 2>&1
