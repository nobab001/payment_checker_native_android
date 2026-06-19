@echo off
rem Clear contents of user DB folders so MariaDB can drop them
for %%D in (paychek_online_v2 payment_checker payment_checker_db) do (
  if exist "C:\xampp\mysql\data\%%D" (
    echo === clearing %%D ===
    pushd "C:\xampp\mysql\data\%%D"
    for %%F in (*.*) do (
      del /F /Q "%%F" 2>nul
    )
    rd /s /q "C:\xampp\mysql\data\%%D" 2>nul
    popd
  )
)
echo === done ===