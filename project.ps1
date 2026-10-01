param(
    [ValidateSet('build', 'run', 'test')]
    [string]$Action = 'run',
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$AppArguments
)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
foreach ($tool in @('java', 'javac')) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
        throw "Missing $tool. Install a Java Development Kit (JDK) version 17 or later, then reopen your terminal."
    }
}
$jarCommand = Get-Command jar -ErrorAction SilentlyContinue
$jarPath = if ($jarCommand) { $jarCommand.Source } else { $null }
if (-not $jarPath) {
    # Oracle's Windows PATH shim exposes java/javac but sometimes omits jar.
    # Locate jar beside the active runtime without changing the user's PATH.
    $savedErrorPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $javaSettings = & java '-XshowSettings:properties' -version 2>&1
    } finally { $ErrorActionPreference = $savedErrorPreference }
    $homeLine = $javaSettings | ForEach-Object { $_.ToString() } | Where-Object { $_ -match '^\s*java\.home\s*=' } | Select-Object -First 1
    if ($homeLine) {
        $runtimeHome = ($homeLine -split '=', 2)[1].Trim()
        $candidate = Join-Path $runtimeHome 'bin/jar.exe'
        if (Test-Path -LiteralPath $candidate) { $jarPath = $candidate }
    }
}
if (-not $jarPath) { throw 'Missing jar. Install a complete JDK 17 or later and add its bin folder to PATH.' }
New-Item -ItemType Directory -Force -Path build/classes | Out-Null
$sources = @(Get-ChildItem -LiteralPath src/main/java -Recurse -Filter *.java | ForEach-Object FullName)
& javac --release 17 -encoding UTF-8 -d build/classes @sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $jarPath --create --file build/knightlight.jar --main-class chess.ChessApp -C build/classes .
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host 'Built build/knightlight.jar'
if ($Action -eq 'run') {
    & java -jar build/knightlight.jar @AppArguments
    exit $LASTEXITCODE
}
if ($Action -eq 'test') {
    New-Item -ItemType Directory -Force -Path build/test-classes | Out-Null
    $tests = @(Get-ChildItem -LiteralPath src/test/java -Recurse -Filter *.java | ForEach-Object FullName)
    & javac --release 17 -encoding UTF-8 -cp build/classes -d build/test-classes @tests
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    foreach ($test in @('chess.RulesTest', 'chess.EngineTest', 'chess.UiTest')) {
        & java '-Djava.awt.headless=true' -cp 'build/classes;build/test-classes' $test
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
    Write-Host 'All tests passed.'
}
