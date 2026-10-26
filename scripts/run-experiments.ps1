param([string]$OutputDirectory = 'results/reproduced')
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
Push-Location $repo
try {
    & java scripts/Build.java
    if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
    function Invoke-Lab([string[]]$LabArguments, [string[]]$JavaProperties = @()) {
        & java '-Xmx4g' @JavaProperties -cp build/classes stud.g01.runner.LabRunner @LabArguments
        if ($LASTEXITCODE -ne 0) { throw "Experiment exited with code $LASTEXITCODE. Preserve its result metadata for diagnosis." }
    }
    Invoke-Lab @('benchmark','--input','resources/problems.txt','--size','3','--repeats','5','--timeout','10','--output',"$OutputDirectory/course-eight.csv")
    Invoke-Lab @('benchmark','--input','resources/problems.txt','--size','3','--repeats','3','--timeout','10','--algorithms','astar','--heuristics','manhattan,pdb','--frontiers','heap,list','--output',"$OutputDirectory/frontiers-eight.csv")
    Invoke-Lab @('benchmark','--input','resources/generated.txt','--size','4','--repeats','3','--timeout','10','--output',"$OutputDirectory/generated-fifteen.csv")
    Invoke-Lab @('benchmark','--input','resources/problems3.txt','--size','4','--repeats','1','--timeout','60','--algorithms','ida','--heuristics','pdb','--nodes','1000000000','--output',"$OutputDirectory/course-fifteen-pdb.csv")
    Invoke-Lab @('benchmark','--input','resources/problems.txt','--size','4','--repeats','3','--timeout','30','--algorithms','ida','--heuristics','manhattan,pdb','--output',"$OutputDirectory/course-fifteen-baseline.csv")
    $cache = '.cache/reproduction-' + [Guid]::NewGuid().ToString('N')
    foreach ($label in @('cold','warm-1','warm-2')) {
        Invoke-Lab @('solve','--input','resources/problems3.txt','--case','10','--algorithm','ida','--heuristic','pdb','--timeout','60','--nodes','1000000000','--output',"$OutputDirectory/hard15-$label.npz") @("-Dlab.pdbCache=$cache")
    }
    Write-Host "Completed. Raw measurements and paths: $OutputDirectory"
} finally { Pop-Location }
