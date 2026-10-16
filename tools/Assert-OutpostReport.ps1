param([Parameter(Mandatory=$true)][string]$Report,[Parameter(Mandatory=$true)][string[]]$ExpectedCase,[int]$ProcessExitCode=0)
$ErrorActionPreference='Stop'
try {
    if($ProcessExitCode -ne 0){throw "Process exit $ProcessExitCode"}
    if(-not(Test-Path -LiteralPath $Report)){throw 'Missing report'}
    $rows=@(foreach($entry in (Get-Content -LiteralPath $Report -Raw | ConvertFrom-Json)){$entry})
    if($rows.Count -ne $ExpectedCase.Count){throw "Unexpected case count"}
    if($rows.Count -eq 0 -or @($rows | Where-Object {$_.outcome -ne 'PASS'}).Count -gt 0){throw 'Non-passing result'}
    foreach($id in $ExpectedCase){if(@($rows | Where-Object {$_.caseId -eq $id}).Count -ne 1){throw "Missing or duplicate case: $id"}}
    Write-Output $rows.Count
} catch { Write-Error $_;exit 1 }
