$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
 $log=Join-Path $root 'build/menu-navigation.log'
 New-Item -ItemType Directory -Force -Path (Split-Path $log) | Out-Null
 & ./gradlew.bat runClient -PmenuPreview -PmenuRegression '-Pmod_version=0.1.0-dev.menus.1' --no-daemon --console=plain *> $log
 if($LASTEXITCODE -ne 0){throw "Client failed; see $log"}
 $result=Get-Content -LiteralPath $log -Raw
 if($result -notmatch 'MENU_NAVIGATION_RESULT failures=0 cases=5'){throw "Menu navigation regression failed; see $log"}
 Write-Output 'PASS: route returns, founding coordinate validation, level control and Mayor allowance view.'
} finally { Pop-Location }
