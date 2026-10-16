param([ValidateSet('Native','Transfers','Restart')][string]$Suite='Native',[ValidateRange(30,900)][int]$TimeoutSeconds=240)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$runId=[guid]::NewGuid().ToString('N')
$reportRoot=Join-Path $projectRoot "build/reports/outpost-probe/$runId"
New-Item -ItemType Directory -Force -Path $reportRoot | Out-Null
$env:JAVA_HOME=if($env:JAVA_HOME){$env:JAVA_HOME}else{'C:\Program Files\Java\jdk-21'}
if(-not(Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))){throw 'Java 21 installation not found. Set JAVA_HOME.'}
$phases=if($Suite -eq 'Restart'){@('seed','verify_loaded','verify_partial','verify_arrived')}else{@('seed')}
foreach($phase in $phases){
    $phaseRoot=Join-Path $reportRoot $phase
    New-Item -ItemType Directory -Force -Path $phaseRoot | Out-Null
    $log=Join-Path $phaseRoot "$Suite.log"
    $stderr=Join-Path $phaseRoot "$Suite.stderr.log"
    $argsText='/d /s /c ""{0}" runOutpostProbe "-PoutpostSuite={1}" "-PprobeOutput={2}" "-PprobeWorld=probe-{3}" "-PprobePhase={4}" --no-daemon --console=plain"' -f (Join-Path $projectRoot 'gradlew.bat'),$Suite,$phaseRoot,$runId,$phase
    $process=Start-Process -FilePath $env:ComSpec -ArgumentList $argsText -WorkingDirectory $projectRoot -WindowStyle Hidden -RedirectStandardOutput $log -RedirectStandardError $stderr -PassThru
    $processHandle=$process.Handle # Keep association alive so Windows PowerShell retains ExitCode.
    if(-not $process.WaitForExit($TimeoutSeconds*1000)){
        & taskkill.exe /PID $process.Id /T /F | Out-Null
        throw "Probe timed out; stopped only launched process tree $($process.Id). Log: $log"
    }
    $process.WaitForExit()
    $code=$process.ExitCode
    $report=Join-Path $phaseRoot "$Suite.json"
    if(-not(Test-Path -LiteralPath $report)){Write-Host "FAIL: missing report (Gradle exit $code). Log: $log; $stderr";exit 1}
    $rows=@(foreach($entry in (Get-Content -LiteralPath $report -Raw | ConvertFrom-Json)){$entry})
    if($rows.Count -eq 0 -or @($rows | Where-Object {$_.outcome -ne 'PASS'}).Count -gt 0 -or $code -ne 0){
        $rows | Format-Table caseId,outcome,evidence -Wrap | Out-Host
        Write-Host "FAIL: $Suite/$phase; Gradle exit $code. Log: $log; $stderr"
        exit 1
    }
    $expected=switch($Suite){
        Native {@('fixture_owns_one_real_colony','remote_site_preserves_colony_identity','remote_workplace_placement','remote_builder_places_supplied_blocks','remote_logger_harvests_and_replants','remote_worker_returns_without_duplicate_identity')}
        Transfers {@('load_moves_real_items_once','partial_delivery_retains_remainder','components_and_tools_survive_transfer','slot_restrictions_are_respected','endpoint_permission_or_identity_change_blocks_transfer','shipment_roundtrip_preserves_components_and_policy','interrupted_transfer_requires_recovery','interrupted_extraction_preserves_pending_evidence','interrupted_insertion_preserves_pending_evidence')}
        Restart {@("restart_$phase")}
    }
    & (Join-Path $PSScriptRoot "Assert-OutpostReport.ps1") -Report $report -ExpectedCase $expected -ProcessExitCode $code | Out-Null
    Write-Host "PASS: $Suite/$phase ($($rows.Count) cases). Report: $report"
}
