$ErrorActionPreference='Stop'
if(!$env:MAVEN_OPTS){$env:MAVEN_OPTS='-Xmx256m -XX:ActiveProcessorCount=2'}
Set-Location -LiteralPath $PSScriptRoot
Push-Location 'frontend'
try{if(Test-Path 'package-lock.json'){& npm.cmd ci}else{& npm.cmd install};if($LASTEXITCODE -ne 0){throw 'npm install failed'};& npm.cmd run build;if($LASTEXITCODE -ne 0){throw 'frontend build failed'}}finally{Pop-Location}
$static=Join-Path $PSScriptRoot 'backend\src\main\resources\static'
if(!([IO.Path]::GetFullPath($static).StartsWith($PSScriptRoot+[IO.Path]::DirectorySeparatorChar))){throw 'Invalid static path'}
if(Test-Path -LiteralPath $static){New-Item -ItemType Directory -Force -Path "$PSScriptRoot\build-backups" | Out-Null;Move-Item -LiteralPath $static -Destination (Join-Path "$PSScriptRoot\build-backups" ('static-'+[guid]::NewGuid().ToString('N')))}
Copy-Item -LiteralPath 'frontend\dist' -Destination $static -Recurse
Push-Location 'backend'
try{& mvn.cmd '-DargLine=-Xmx256m -XX:ActiveProcessorCount=2' '-Dclub.snapshot.path=./target/test-club-state.json' '-Dspring.datasource.url=jdbc:h2:mem:buildverify;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1' package;if($LASTEXITCODE -ne 0){throw 'backend build or tests failed'}}finally{Pop-Location}
New-Item -ItemType Directory -Force -Path 'release' | Out-Null
Copy-Item -LiteralPath 'backend\target\heritage-archive-system.jar' -Destination 'release\system.jar' -Force
Write-Host 'Build and tests passed; release/system.jar updated.'

