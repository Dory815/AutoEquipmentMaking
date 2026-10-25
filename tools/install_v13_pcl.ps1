$ErrorActionPreference='Stop'
$project=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$target=[IO.Path]::GetFullPath('D:/MC/PCL 正式版 2.10.6/.minecraft/versions/26.2-自动打装备')
$mods=Join-Path $target 'mods'
$source=Join-Path $project 'client/build/libs/auto-equipment-making-client-1.3.0.jar'
$expected='120f208c1f4dd4687e242f458e2fe750846f2503139ab1bbc8e33173b2c97ba7'
$old=Join-Path $mods 'auto-equipment-making-client-1.1.0-alpha.2.jar'
$installed=Join-Path $mods 'auto-equipment-making-client-1.3.0.jar'
$pending=$installed+'.pending'
$backup=[IO.Path]::GetFullPath((Join-Path $project 'runtime/pcl-equipment-client/backups/v11-alpha2-before-v13-bulk-tools-20261025'))
$evidence=Join-Path $project 'docs/evidence/v13-bulk-tools-20261025/pcl-update.json'
function Digest([string]$path){(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()}
function ProtectedHashes {
    $hashes=@{}
    Get-ChildItem -LiteralPath $target -File -Recurse | Where-Object {$_.FullName -ne $old -and $_.FullName -ne $installed -and $_.FullName -ne $pending} | ForEach-Object {
        $hashes[$_.FullName.Substring($target.Length+1)]=Digest $_.FullName
    }
    return $hashes
}
if(!$backup.StartsWith($project+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Backup outside workspace'}
if(!(Test-Path -LiteralPath $target -PathType Container)){throw 'Target version missing'}
if(Get-ChildItem -LiteralPath $target -Recurse -Attributes ReparsePoint){throw 'Unexpected reparse point in target version'}
$java=Get-CimInstance Win32_Process | Where-Object {$_.Name -match '^javaw?\.exe$'}
if($java | Where-Object {!$_.CommandLine -or $_.CommandLine -like '*26.2-自动打装备*'}){throw 'Target client running or Java command unavailable; no files changed'}
if((Digest $source) -ne $expected){throw 'Tested artifact hash mismatch'}
if((Digest $old) -ne '54dc2f07716e818e6d035a79b123bc117c262fafe5caa151294cb75e8b5df309'){throw 'Unexpected installed version'}
if(@(Get-ChildItem -LiteralPath $mods -Filter 'auto-equipment-making*.jar').Count -ne 1){throw 'Unexpected AEM jar set'}
foreach($path in @($backup,$installed,$pending,$evidence)){if(Test-Path -LiteralPath $path){throw "Refuse to overwrite: $path"}}
$before=ProtectedHashes
$configs=@($before.Keys | Where-Object { $_.StartsWith('config\') })
New-Item -ItemType Directory -Path $backup | Out-Null
$before | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath (Join-Path $backup 'protected-hashes.json') -Encoding utf8
Copy-Item -LiteralPath (Join-Path $target 'config') -Destination (Join-Path $backup 'config') -Recurse
foreach($name in $configs){if((Digest (Join-Path $backup $name)) -ne $before[$name]){throw "Config backup mismatch: $name"}}
Copy-Item -LiteralPath $source -Destination $pending
if((Digest $pending) -ne $expected){throw 'Staged artifact mismatch'}
$oldBackup=Join-Path $backup ([IO.Path]::GetFileName($old))
Move-Item -LiteralPath $old -Destination $oldBackup
try {
    Move-Item -LiteralPath $pending -Destination $installed
    if((Digest $installed) -ne $expected){throw 'Installed artifact mismatch'}
    $after=ProtectedHashes
    if($after.Count -ne $before.Count){throw 'Protected file count changed'}
    foreach($name in $before.Keys){if($after[$name] -ne $before[$name]){throw "Protected file changed: $name"}}
    if(@(Get-ChildItem -LiteralPath $mods -Filter 'auto-equipment-making*.jar').Count -ne 1){throw 'Duplicate AEM jar'}
} catch {
    if(Test-Path -LiteralPath $installed){Remove-Item -LiteralPath $installed}
    Copy-Item -LiteralPath $oldBackup -Destination $old
    throw
}
$report=[ordered]@{versionName='26.2-自动打装备';aem='1.3.0';revision='bulk-tools-20261025';artifactSHA256=$expected;backup=$backup;configurationFilesUnchanged=$configs.Count;protectedFilesUnchanged=$before.Count;modCount=@(Get-ChildItem -LiteralPath $mods -Filter '*.jar').Count;clientLaunched=$false;formalServerConnected=$false;configurationBackupVerified=$true;originalVersionsWritten=@();installedJar=$installed}
$report | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $evidence -Encoding utf8
$report | ConvertTo-Json -Depth 4
