$ErrorActionPreference='Stop'
$captureProject=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$captureJava='C:/Users/Lenovo/AppData/Local/Programs/Microsoft/jdk-25.0.2.10-hotspot/bin'
$captureBuild=Join-Path $captureProject '.tools/world-fixture-build'
New-Item -ItemType Directory -Path $captureBuild -Force | Out-Null
$captureClasspath=@((Join-Path $captureProject '.gradle-user-home/caches/fabric-loom/26.2/minecraft-merged.jar'))
$captureClasspath+=Get-ChildItem -LiteralPath (Join-Path $captureProject '.gradle-user-home/caches/modules-2/files-2.1') -Recurse -File -Filter '*.jar' | ForEach-Object FullName
$captureCp=$captureClasspath -join ';'
$captureRevision=(Get-FileHash -LiteralPath (Join-Path $PSScriptRoot 'CaptureWorker.java')).Hash.Substring(0,12)
$captureAgentClass='CaptureAgent'+$captureRevision
$captureAgentSource=(Get-Content -LiteralPath (Join-Path $PSScriptRoot 'CaptureAgent.java') -Raw).Replace('CaptureAgent',$captureAgentClass)
[IO.File]::WriteAllText((Join-Path $captureBuild "$captureAgentClass.java"),$captureAgentSource,[Text.UTF8Encoding]::new($false))
$captureArgs=@('-encoding','UTF-8','--release','25','-classpath',('"'+$captureCp.Replace('\','/')+'"'),'-d',('"'+$captureBuild.Replace('\','/')+'"'),('"'+(Join-Path $captureBuild "$captureAgentClass.java").Replace('\','/')+'"'),('"'+(Join-Path $PSScriptRoot 'CaptureWorker.java').Replace('\','/')+'"'))
[IO.File]::WriteAllLines((Join-Path $captureBuild 'javac.args'),$captureArgs,[Text.UTF8Encoding]::new($false))
& (Join-Path $captureJava 'javac.exe') ('@'+(Join-Path $captureBuild 'javac.args'))
if($LASTEXITCODE){throw 'Capture tool compilation failed'}
$captureManifest="Manifest-Version: 1.0`nAgent-Class: $captureAgentClass`n`n"
[IO.File]::WriteAllText((Join-Path $captureBuild 'MANIFEST.MF'),$captureManifest,[Text.UTF8Encoding]::new($false))
$captureJar=Join-Path $captureBuild "capture-$captureRevision.jar"
& (Join-Path $captureJava 'jar.exe') cfm $captureJar (Join-Path $captureBuild 'MANIFEST.MF') -C $captureBuild "$captureAgentClass.class" -C $captureBuild CaptureWorker.class
if($LASTEXITCODE){throw 'Capture tool packaging failed'}
$captureJar | Set-Content -LiteralPath (Join-Path $captureBuild 'latest-jar.txt') -Encoding utf8
'Capture helper compiled; no game or server action executed.'
