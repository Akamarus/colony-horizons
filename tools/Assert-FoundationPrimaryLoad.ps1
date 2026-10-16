param([string[]]$Paths)
$ErrorActionPreference='Stop'
if(Select-String -LiteralPath $Paths -Pattern 'Failed to load colony|Error loading colony|restor.*backup|loading backup|restored colony' -Quiet){throw 'Native colony did not load cleanly from primary data'}
