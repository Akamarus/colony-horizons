param()
$ErrorActionPreference='Stop'
$dir=Join-Path $PSScriptRoot ('../build/reports/outpost-runner-tests/'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $dir | Out-Null
$cases=@(
    @{name='valid';json='[{"caseId":"one","outcome":"PASS"}]';good=$true},
    @{name='duplicate';json='[{"caseId":"one","outcome":"PASS"},{"caseId":"one","outcome":"PASS"}]';good=$false},
    @{name='missing';json='[]';good=$false},
    @{name='blocked';json='[{"caseId":"one","outcome":"BLOCKED"}]';good=$false},
    @{name='failed';json='[{"caseId":"one","outcome":"FAIL"}]';good=$false},
    @{name='extra';json='[{"caseId":"one","outcome":"PASS"},{"caseId":"extra","outcome":"PASS"}]';good=$false},
    @{name='nonzero';json='[{"caseId":"one","outcome":"PASS"}]';good=$false;code=7},
    @{name='absent_file';json='[]';good=$false;absent=$true},
    @{name='malformed';json='{broken';good=$false}
)
foreach($case in $cases){
    $file=Join-Path $dir ($case.name+'.json');if(-not $case.absent){Set-Content -LiteralPath $file -Value $case.json}
    $testExit=if($case.code){$case.code}else{0}
    $ErrorActionPreference='Continue'
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'Assert-OutpostReport.ps1') -Report $file -ExpectedCase one -ProcessExitCode $testExit *> (Join-Path $dir ($case.name+'.log'))
    $actual=$LASTEXITCODE
    $ErrorActionPreference='Stop'
    if(($actual -eq 0) -ne $case.good){throw "Report validator failed case $($case.name): exit $actual"}
}
Write-Host "PASS: $($cases.Count) runner report checks"
