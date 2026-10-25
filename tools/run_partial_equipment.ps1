param(
    [Parameter(Mandatory=$true)][ValidatePattern('^[a-z0-9-]+$')][string]$EvidenceName,
    [ValidateSet('mixed','all-netherite','all-enchanted-diamond','raw-regression')][string]$Scenario='mixed'
)
$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot/..
$partialEvidence=Join-Path (Get-Location) "docs/evidence/v11-partial-equipment-20261025/$EvidenceName"
if(Test-Path -LiteralPath $partialEvidence){throw 'Refuse to overwrite evidence'}
New-Item -ItemType Directory -Path $partialEvidence | Out-Null
$env:JAVA_HOME='C:/Users/Lenovo/AppData/Local/Programs/Microsoft/jdk-25.0.2.10-hotspot'
$env:GRADLE_USER_HOME=Join-Path (Get-Location) '.gradle-user-home'
$env:AEM_PARTIAL_SCENARIO=$Scenario
$partialSuite=if($Scenario -eq 'raw-regression'){'v11-production'}else{'partial-equipment'}
$partialStart=[DateTime]::UtcNow
$partialRun=Join-Path (Get-Location) 'client/build/run/clientGameTest'
$partialJournal=Join-Path $partialRun 'config/auto-equipment-making/task.jsonl'
$partialOffset=if(Test-Path -LiteralPath $partialJournal){(Get-Item -LiteralPath $partialJournal).Length}else{0}
$partialSources=@{}
Get-ChildItem client/src/main,engine/src/main -Recurse -File | ForEach-Object {$partialSources[$_.FullName]=(Get-FileHash -LiteralPath $_.FullName).Hash.ToLowerInvariant()}
$partialExit=1
try{
    & ./gradlew.bat --offline --no-daemon --max-workers=2 '-PmcInstance=D:/MC/AutoEquipmentMaking/.test-instance' "-PaemClientTest=$partialSuite" :client:runClientGameTest *> (Join-Path $partialEvidence 'console.txt')
    $partialExit=$LASTEXITCODE
}finally{
    foreach($partialFile in @('logs/latest.log','partial-equipment-results.json')){
        $partialPath=Join-Path $partialRun $partialFile
        if((Test-Path -LiteralPath $partialPath) -and (Get-Item -LiteralPath $partialPath).LastWriteTimeUtc -ge $partialStart){Copy-Item -LiteralPath $partialPath -Destination $partialEvidence}
    }
    if(Test-Path -LiteralPath $partialJournal){
        $partialStream=[IO.File]::OpenRead($partialJournal)
        try{
            if($partialStream.Length -ge $partialOffset){$partialStream.Seek($partialOffset,[IO.SeekOrigin]::Begin) | Out-Null}
            $partialReader=[IO.StreamReader]::new($partialStream)
            try{$partialText=$partialReader.ReadToEnd()}finally{$partialReader.Dispose()}
            [IO.File]::WriteAllText((Join-Path $partialEvidence 'task.jsonl'),$partialText,[Text.UTF8Encoding]::new($false))
        }finally{$partialStream.Dispose()}
    }
    if(Test-Path -LiteralPath (Join-Path $partialRun 'screenshots')){Get-ChildItem -LiteralPath (Join-Path $partialRun 'screenshots') -File | Where-Object LastWriteTimeUtc -ge $partialStart | ForEach-Object {Copy-Item -LiteralPath $_.FullName -Destination $partialEvidence}}
    [ordered]@{scenario=$Scenario;suite=$partialSuite;exitCode=$partialExit;environment='old simple independent singleplayer fixture, explicit user exception 2026-10-25';formalServerConnected=$false;arrangedBaselineOpened=$false;sources=$partialSources} | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $partialEvidence 'run.json') -Encoding utf8
}
Get-Content -LiteralPath (Join-Path $partialEvidence 'console.txt') -Tail 18
exit $partialExit
