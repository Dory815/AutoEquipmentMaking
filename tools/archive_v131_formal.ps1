param([Parameter(Mandatory=$true)][string]$Label)
$ErrorActionPreference='Stop'
if($Label -notmatch '^[a-z0-9-]+$'){throw 'Invalid label'}
$root=Join-Path $PSScriptRoot '../docs/evidence/v131-formal-20261025'
$dest=Join-Path $root $Label
if(Test-Path -LiteralPath $dest){throw 'Evidence exists'}
New-Item -ItemType Directory -Path $dest | Out-Null
$target='D:/MC/PCL 正式版 2.10.6/.minecraft/versions/26.2-自动打装备'
$task='5f3c9fe5-e7c3-47b1-a517-5457f60ba31d'
$stream=[IO.File]::Open((Join-Path $target 'config/auto-equipment-making/task.jsonl'),[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::ReadWrite)
$reader=[IO.StreamReader]::new($stream)
$writer=[IO.StreamWriter]::new((Join-Path $dest 'task.jsonl'),$false,[Text.UTF8Encoding]::new($false))
$events=[Collections.Generic.List[object]]::new()
try{while(($line=$reader.ReadLine()) -ne $null){if($line.Contains($task)){$writer.WriteLine($line);$row=$line|ConvertFrom-Json;$events.Add(($row|Select-Object time,state,event,completed,deliveredKits,levels))}}}finally{$reader.Dispose();$writer.Dispose()}
Get-Content -LiteralPath (Join-Path $target 'logs/latest.log') | ForEach-Object {if($_ -match 'token|Authorization|secret|[A-Za-z0-9_=-]{65}'){'[credential-bearing line omitted]'}else{$_}} | Set-Content -LiteralPath (Join-Path $dest 'latest-sanitized.log') -Encoding utf8
foreach($name in @('settings.json','workshop.json')){Copy-Item -LiteralPath (Join-Path $target "config/auto-equipment-making/$name") -Destination $dest}
$last=$events[-1]
[ordered]@{task=$task;events=$events.Count;confirmedPurchases=@($events|Where-Object event -like 'TRADE BUY CONFIRMED*').Count;pauses=@($events|Where-Object event -like 'PAUSED*');waiting=@($events|Where-Object event -like 'WAITING*');cooldownChecks=@($events|Where-Object event -like 'TRADE COOLDOWN CHECK*').Count;notices=@($events|Where-Object event -like 'NOTICE*');last=$last;formalServer=$true;runtimeJarSHA256=(Get-FileHash -LiteralPath (Join-Path $target 'mods/auto-equipment-making-client-1.3.1.jar')).Hash.ToLowerInvariant();snapshotOnly=$true;taskCancelled=$false} | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $dest 'summary.json') -Encoding utf8
Get-Content -LiteralPath (Join-Path $dest 'summary.json')
