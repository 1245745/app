$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:ANDROID_HOME = "E:\test\test1\android-sdk"
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:PATH"

$licensesFile = "$env:TEMP\licenses.txt"
"y`ny`ny`ny`ny`ny`ny`ny`ny`ny`ny`ny`ny`ny`ny`ny`ny`ny`ny`ny" | Out-File -FilePath $licensesFile -Encoding ASCII

Get-Content $licensesFile | sdkmanager.bat --licenses --sdk_root="$env:ANDROID_HOME"