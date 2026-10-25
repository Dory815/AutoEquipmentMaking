$ErrorActionPreference='Stop'
$captureProject=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Set-Location -LiteralPath $captureProject
$captureRecord=Get-Content -LiteralPath 'docs/evidence/201-world-fixture-20261025/review-package.json' -Raw | ConvertFrom-Json
if($captureRecord.finalBaselineCreated -or -not $captureRecord.awaitingUserArrangement){throw 'Expected editable review package'}
$captureVersion=[IO.Path]::GetFullPath('D:/MC/PCL 正式版 2.10.6/.minecraft/versions/26.2-自动打装备')
$captureSaves=Join-Path $captureVersion 'saves'
$captureWorld=Join-Path $captureSaves '201交易所-待整理-20261025'
$captureStaging=Join-Path $captureSaves '.aem-201-trading-hall-importing'
foreach($capturePath in @($captureWorld,$captureStaging)){
    if(-not [IO.Path]::GetFullPath($capturePath).StartsWith($captureSaves+[IO.Path]::DirectorySeparatorChar)){throw 'World outside authorized version saves'}
    if(Test-Path -LiteralPath $capturePath){throw 'Refuse to overwrite existing review world or staging'}
}
$captureSource=[IO.Path]::GetFullPath($captureRecord.worldDirectory)
if(-not $captureSource.StartsWith((Join-Path $captureProject 'runtime/201-trading-hall-review-v2')+[IO.Path]::DirectorySeparatorChar)){throw 'Unexpected source world'}
$captureProtected=@{}
Get-ChildItem -LiteralPath $captureVersion -Recurse -File | ForEach-Object {$captureProtected[$_.FullName]=(Get-FileHash -LiteralPath $_.FullName).Hash}
$captureConfigCount=@(Get-ChildItem -LiteralPath (Join-Path $captureVersion 'config') -Recurse -File).Count
New-Item -ItemType Directory -Path $captureSaves -Force | Out-Null
Copy-Item -LiteralPath $captureSource -Destination $captureStaging -Recurse
foreach($captureProperty in $captureRecord.worldHashes.PSObject.Properties){
    if((Get-FileHash -LiteralPath (Join-Path $captureStaging $captureProperty.Name)).Hash.ToLowerInvariant() -ne $captureProperty.Value){throw 'Copied world hash mismatch'}
}
Move-Item -LiteralPath $captureStaging -Destination $captureWorld
foreach($capturePath in $captureProtected.Keys){if((Get-FileHash -LiteralPath $capturePath).Hash -ne $captureProtected[$capturePath]){throw "Existing protected file changed: $capturePath"}}
[ordered]@{worldDirectory=$captureWorld;displayName='201交易所-待整理-20261025';worldFiles=$captureRecord.worldFiles;configurationFilesUnchanged=$captureConfigCount;protectedFilesUnchanged=$captureProtected.Count;clientLaunched=$false;finalBaselineCreated=$false;awaitingUserArrangement=$true;existingWorldsOverwritten=$false} | ConvertTo-Json | Set-Content -LiteralPath 'docs/evidence/201-world-fixture-20261025/pcl-review-install.json' -Encoding utf8
Get-Content -LiteralPath 'docs/evidence/201-world-fixture-20261025/pcl-review-install.json'
