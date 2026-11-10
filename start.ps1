$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot
if(Test-Path -LiteralPath "$PSScriptRoot\database-config.ps1"){. "$PSScriptRoot\database-config.ps1"}
$java=if($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")){"$env:JAVA_HOME\bin\java.exe"}else{(Get-Command java -ErrorAction Stop).Source}
if(!(Test-Path -LiteralPath "$PSScriptRoot\release\system.jar")){throw 'release/system.jar missing. Run build.ps1 first.'}
$javaOptions=@('-Xmx512m')
$probePassed=$false
try { & $java "$PSScriptRoot\NetworkProbe.java" *> $null; $probePassed=($LASTEXITCODE -eq 0) } catch { $probePassed=$false }
if(!$probePassed){
 $fallback=Join-Path $PSScriptRoot '.nonexistent-afunix-fallback'
 if(Test-Path -LiteralPath $fallback){throw 'Remove or rename the unexpected .nonexistent-afunix-fallback directory before starting.'}
 $javaOptions+="-Djdk.net.unixdomain.tmpdir=$fallback"
 Write-Host 'Using JDK TCP loopback fallback for this Windows environment.'
}
$port=if($env:SERVER_PORT){$env:SERVER_PORT}else{'8081'}
if(!$env:INITIAL_ADMIN_USERNAME -and !$env:INITIAL_ADMIN_PASSWORD){
 Write-Host 'For a new database, enter the initial administrator. For an existing database press Enter to skip.'
 $initialName=Read-Host 'Initial administrator username (Enter to skip)'
 if($initialName){
  $initialSecret=Read-Host 'Initial administrator password (at least 6 characters)' -AsSecureString
  $initialPointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($initialSecret)
  try{$env:INITIAL_ADMIN_PASSWORD=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($initialPointer)}finally{[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($initialPointer)}
  if($env:INITIAL_ADMIN_PASSWORD.Length -lt 6){throw 'Administrator password must contain at least 6 characters.'}
  $env:INITIAL_ADMIN_USERNAME=$initialName
 }
}
Write-Host "Open http://127.0.0.1:$port/ after the server has started. Keep this window open."
try{& $java @javaOptions -jar "$PSScriptRoot\release\system.jar" "--server.port=$port" '--server.address=127.0.0.1'}finally{Remove-Item Env:INITIAL_ADMIN_PASSWORD -ErrorAction SilentlyContinue}
if($LASTEXITCODE -ne 0){throw 'Server startup failed. Read the error above and check database configuration.'}

