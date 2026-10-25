param(
    [Parameter(Mandatory=$true)][ValidatePattern('^[a-z0-9-]+$')][string]$EvidenceName,
    [ValidateSet('before','after')][string]$Scenario='after',
    [long]$Seed=20120261025
)
$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot/..
$partialEvidence=Join-Path (Get-Location) "docs/evidence/v13-villager-navigation-20261025/$EvidenceName"
if(Test-Path -LiteralPath $partialEvidence){throw 'Refuse to overwrite evidence'}
New-Item -ItemType Directory -Path $partialEvidence | Out-Null
$env:JAVA_HOME='C:/Users/Lenovo/AppData/Local/Programs/Microsoft/jdk-25.0.2.10-hotspot'
$env:GRADLE_USER_HOME=Join-Path (Get-Location) '.gradle-user-home'
# This independent client has no audio-output requirement. Do not depend on a
# desktop device staying connected during the long survival acceptance run.
$env:ALSOFT_DRIVERS='null'
$env:AEM_MERCHANT_BEFORE=if($Scenario -eq 'before'){'true'}else{'false'}
$env:AEM_HALL_SEED=[string]$Seed
$partialSuite='villager-navigation'
$hallSource=Join-Path (Get-Location) 'runtime/test-baselines/201-trading-hall-user-arranged-20261025/world'
$hallWorld=Join-Path (Get-Location) "client/build/run/clientGameTest/saves/aem-hall-$EvidenceName"
if(Test-Path -LiteralPath $hallWorld){throw 'Refuse to overwrite test save'}
Copy-Item -LiteralPath $hallSource -Destination $hallWorld -Recurse
$env:AEM_HALL_WORLD=$hallWorld
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
    foreach($partialFile in @('logs/latest.log','merchant-navigation-results.json')){
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
    [ordered]@{scenario=$Scenario;suite=$partialSuite;exitCode=$partialExit;environment='clone of user-arranged 201 baseline; singleplayer only';formalServerConnected=$false;arrangedBaselineOpened=$false;sources=$partialSources} | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $partialEvidence 'run.json') -Encoding utf8
    $hallClasses=@{}
    foreach($hallModule in @('client','engine')){
        $hallClassRoot=Join-Path (Get-Location) "$hallModule/build/classes/java/main"
        $hallHashes=@{}
        Get-ChildItem -LiteralPath $hallClassRoot -Recurse -Filter '*.class' | ForEach-Object {
            $hallName=$_.FullName.Substring($hallClassRoot.Length+1).Replace('\','/')
            $hallHashes[$hallName]=(Get-FileHash -LiteralPath $_.FullName).Hash.ToLowerInvariant()
        }
        $hallClasses[$hallModule]=$hallHashes
    }
    $hallClasses | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $partialEvidence 'runtime-classes.json') -Encoding utf8
}
Get-Content -LiteralPath (Join-Path $partialEvidence 'console.txt') -Tail 18
exit $partialExit
