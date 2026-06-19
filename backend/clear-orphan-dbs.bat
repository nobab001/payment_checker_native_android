@echo off
rem Clear contents of OLD/ORPHAN user DB folders (NOT the live one)
for %%D in (payment_checker payment_checker_db) do (
  if exist "C:\xampp\mysql\data\%%D" (
    echo === clearing %%D ===
    pushd "C:\xampp\mysql\data\%%D"
    for %%F in (*.*) do (
      del /F /Q "%%F" 2>nul
    )
    rd /s /q "C:\xampp\mysql\data\%%D" 2>nul
    popd
  ) else (
    echo === %%D not found - skipping ===
  )
)
echo === done ===