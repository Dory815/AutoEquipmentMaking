param([Parameter(Mandatory=$true)][string]$EvidenceName)
$ErrorActionPreference='Stop'
$captureProject=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Set-Location -LiteralPath $captureProject
if($EvidenceName -notmatch '^[a-z0-9-]+$'){throw 'Invalid evidence name'}
$captureRoot=Join-Path $captureProject "docs/evidence/201-world-fixture-20261025/$EvidenceName"
if(Test-Path -LiteralPath $captureRoot){throw 'Refuse to overwrite evidence'}
New-Item -ItemType Directory -Path $captureRoot | Out-Null
$env:JAVA_HOME='C:/Users/Lenovo/AppData/Local/Programs/Microsoft/jdk-25.0.2.10-hotspot'
$env:GRADLE_USER_HOME=Join-Path $captureProject '.gradle-user-home'
$env:AEM_FIXTURE_SNAPSHOT=Join-Path $captureProject 'docs/evidence/201-world-fixture-20261025/201-trading-hall.nbt'
if(-not(Test-Path -LiteralPath $env:AEM_FIXTURE_SNAPSHOT)){throw 'Capture must finish before import'}
$captureRun=Join-Path $captureProject 'client/build/run/clientGameTest'
$captureStarted=[DateTime]::UtcNow
$captureExit=1
try{
    & ./gradlew.bat --offline --no-daemon --max-workers=2 "-PmcInstance=$captureProject/.test-instance" '-PaemClientTest=fixture-copy' :client:runClientGameTest *> (Join-Path $captureRoot 'console.txt')
    $captureExit=$LASTEXITCODE
}finally{
    foreach($captureRelative in @('logs/latest.log','201-fixture-results.json')){
        $captureFile=Join-Path $captureRun $captureRelative
        if((Test-Path -LiteralPath $captureFile) -and (Get-Item -LiteralPath $captureFile).LastWriteTimeUtc -ge $captureStarted){Copy-Item -LiteralPath $captureFile -Destination (Join-Path $captureRoot (Split-Path -Leaf $captureFile))}
    }
    $captureScreens=Join-Path $captureRun 'screenshots'
    if(Test-Path -LiteralPath $captureScreens){Get-ChildItem -LiteralPath $captureScreens -File | Where-Object LastWriteTimeUtc -ge $captureStarted | ForEach-Object {Copy-Item -LiteralPath $_.FullName -Destination $captureRoot}}
    [ordered]@{suite='fixture-copy';exitCode=$captureExit;snapshotSHA256=(Get-FileHash -LiteralPath $env:AEM_FIXTURE_SNAPSHOT).Hash.ToLowerInvariant();formalServerConnected=$false;finalBaselineCreated=$false;awaitingUserArrangement=$true} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $captureRoot 'run.json') -Encoding utf8
}
Get-Content -LiteralPath (Join-Path $captureRoot 'console.txt') -Tail 25
exit $captureExit
