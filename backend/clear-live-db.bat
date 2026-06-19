@echo off
rem Clear contents of LIVE user DB folder so MariaDB can drop it cleanly
if exist "C:\xampp\mysql\data\paychek_online_v2" (
  echo === clearing paychek_online_v2 ===
  pushd "C:\xampp\mysql\data\paychek_online_v2"
  for %%F in (*.*) do (
    del /F /Q "%%F" 2>nul
  )
  rd /s /q "C:\xampp\mysql\data\paychek_online_v2" 2>nul
  popd
) else (
  echo === paychek_online_v2 not found - skipping ===
)
echo === done ===