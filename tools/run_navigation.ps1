param(
    [ValidateSet('navigation','v11-production','complex')][string]$Suite='navigation',
    [Parameter(Mandatory=$true)][string]$EvidenceName,
    [string]$TestInstance=(Join-Path $PSScriptRoot "../.test-instance"),
    [switch]$Offline
)
$ErrorActionPreference='Stop'
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
if(-not $env:JAVA_HOME){throw 'Set JAVA_HOME to a JDK 25 installation'}
if(-not(Test-Path -LiteralPath (Join-Path $TestInstance 'mods'))){throw 'Supply an independent test instance with MaLiLib 0.29.3 and MiniHUD 0.40.4 in mods'}
if($EvidenceName -notmatch '^[a-z0-9-]+$'){throw 'Invalid evidence name'}
$navRoot=Join-Path (Get-Location) "docs/evidence/navigation-v1/$EvidenceName"
if(Test-Path -LiteralPath $navRoot){throw 'Refusing to overwrite evidence'}
New-Item -ItemType Directory -Path $navRoot | Out-Null
$env:GRADLE_USER_HOME=Join-Path (Get-Location) '.gradle-user-home'
$navRun=Join-Path (Get-Location) 'client/build/run/clientGameTest'
$navStart=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$navExit=1
try {
    $navGradleOptions=@('--no-daemon','--max-workers=2')
    if($Offline){$navGradleOptions+='--offline'}
    & ./gradlew.bat @navGradleOptions "-PmcInstance=$TestInstance" "-PaemClientTest=$Suite" :client:jar :client:runClientGameTest *> (Join-Path $navRoot 'console.txt')
    $navExit=$LASTEXITCODE
} finally {
    foreach($navRelative in @('logs/latest.log','navigation-results.json','options.txt')) {
        $navSource=Join-Path $navRun $navRelative
        if((Test-Path -LiteralPath $navSource) -and (Get-Item -LiteralPath $navSource).LastWriteTimeUtc -ge [DateTimeOffset]::FromUnixTimeMilliseconds($navStart).UtcDateTime) {
            Copy-Item -LiteralPath $navSource -Destination (Join-Path $navRoot (Split-Path -Leaf $navRelative))
        }
    }
    $navJournal=Join-Path $navRun 'config/auto-equipment-making/task.jsonl'
    if(Test-Path -LiteralPath $navJournal) {
        Get-Content -LiteralPath $navJournal | Where-Object {($_ | ConvertFrom-Json).time -ge $navStart} | Set-Content -LiteralPath (Join-Path $navRoot 'task.jsonl') -Encoding utf8
    }
    $navJar=Join-Path (Get-Location) 'client/build/libs/auto-equipment-making-client-1.0.0.jar'
    $navHash=if(Test-Path -LiteralPath $navJar){(Get-FileHash -LiteralPath $navJar).Hash.ToLowerInvariant()}else{$null}
    [ordered]@{suite=$Suite;exitCode=$navExit;artifactSHA256=$navHash;environment='independent singleplayer';formalServerConnected=$false} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $navRoot 'run.json') -Encoding utf8
    if(Test-Path -LiteralPath (Join-Path $navRun 'screenshots')) {
        $navScreens=Get-ChildItem -LiteralPath (Join-Path $navRun 'screenshots') -File | Where-Object {$_.LastWriteTimeUtc -ge [DateTimeOffset]::FromUnixTimeMilliseconds($navStart).UtcDateTime}
        foreach($navScreen in $navScreens){Copy-Item -LiteralPath $navScreen.FullName -Destination $navRoot}
    }
}
Get-Content -LiteralPath (Join-Path $navRoot 'console.txt') -Tail 25
exit $navExit
