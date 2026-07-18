$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

function Start-HiddenService($name, $workdir, $command, $log, $err, $pidFile) {
    if (Test-Path -LiteralPath $pidFile) {
        $existing = [int](Get-Content -LiteralPath $pidFile -Raw)
        if (Get-Process -Id $existing -ErrorAction SilentlyContinue) {
            Write-Host "$name is already running (PID $existing)"
            return
        }
        Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
    }

    $cmd = "cd /d `"$workdir`" && $command > `"$log`" 2> `"$err`""
    $process = Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', $cmd -WindowStyle Hidden -PassThru
    [System.IO.File]::WriteAllText($pidFile, [string]$process.Id, [System.Text.UTF8Encoding]::new($false))
    Write-Host "$name started (PID $($process.Id))"
}

Start-HiddenService 'Backend' (Join-Path $root 'backend') 'mvn spring-boot:run' (Join-Path $root 'backend-run.log') (Join-Path $root 'backend-run.err.log') (Join-Path $root '.backend.pid')
Start-HiddenService 'Frontend' (Join-Path $root 'frontend') 'npm run dev' (Join-Path $root 'frontend-run.log') (Join-Path $root 'frontend-run.err.log') (Join-Path $root '.frontend.pid')
Write-Host 'Field Realm is starting. Open http://localhost:5180 after a few seconds.'
