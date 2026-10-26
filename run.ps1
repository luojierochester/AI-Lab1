param([Parameter(ValueFromRemainingArguments=$true)][string[]]$LabArgs)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    & java scripts/Build.java
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & java '-Xmx4g' '-Dfile.encoding=UTF-8' -cp build/classes stud.g01.runner.LabRunner @LabArgs
    exit $LASTEXITCODE
} finally { Pop-Location }
