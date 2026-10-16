$ErrorActionPreference='Stop'
$tempFile=Join-Path $env:TEMP ([guid]::NewGuid().ToString('N')+'.log')
try {
 foreach($message in @('Colony:1 is missing, loading backup!','Successfully restored colony:1','Error loading colony 1')){
  Set-Content -LiteralPath $tempFile $message
  $rejected=$false
  try{& (Join-Path $PSScriptRoot 'Assert-FoundationPrimaryLoad.ps1') -Paths @($tempFile)}catch{$rejected=$true}
  if(!$rejected){throw ('False primary-load pass: '+$message)}
 }
 Set-Content -LiteralPath $tempFile 'Normal colony load'
 & (Join-Path $PSScriptRoot 'Assert-FoundationPrimaryLoad.ps1') -Paths @($tempFile)
 Write-Output 'PASS: primary-load log guards'
}finally{Remove-Item -LiteralPath $tempFile -Force -ErrorAction SilentlyContinue}
