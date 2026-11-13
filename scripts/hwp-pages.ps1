# 한컴 한글(Windows) 로 문서마다 쪽 수를 잰다(한글 기준 점검, docs/formats/test-corpus.md).
#
#   powershell -ExecutionPolicy Bypass -File scripts/hwp-pages.ps1 -List <문서 경로 목록> -Out <결과.tsv> [-Timeout 60]
#
# 한글 자동화 개체(HWPFrame.HwpObject) 하나를 숨긴 창으로 띄워 목록의 문서를 차례로 열고 PageCount 를 적는다
# (열 수 없으면 error). 한글을 띄우는 데 20초 남짓 걸려 문서마다 새로 띄우지 않는다.
# 보안 승인 창이 뜨면 목록 문서에 한해 "접근 허용"을 누른다(아래 Approve-Access).
# 한 문서가 시간을 넘기면(멈춤) 이 점검이 띄운 한글(시작 뒤에 생긴 Hwp.exe)만 닫고 timeout 으로 적은 뒤
# 다음 문서부터 한글을 다시 띄운다. 사용자가 직접 연 한글은 건드리지 않는다.
param([string]$List, [string]$Out, [int]$Timeout = 60, [int]$From = -1)

$files = @(Get-Content $List | Where-Object { $_ })

if ($From -ge 0) {
  # 일꾼: $From 번째부터 열어 한 줄씩 적는다. 열기 전에 진행 표시 파일에 번호를 남긴다.
  $h = New-Object -ComObject HWPFrame.HwpObject
  try { $h.XHwpWindows.Item(0).Visible = $false } catch {}
  try { [void]$h.RegisterModule("FilePathCheckDLL", "FilePathCheckerModule") } catch {}
  for ($i = $From; $i -lt $files.Count; $i++) {
    Set-Content -Path "$Out.progress" -Value $i
    $pages = "error"
    try {
      if ($h.Open($files[$i], "", "forceopen:true;suspendpassword:true")) { $pages = $h.PageCount }
    } catch { $pages = "error" }
    try { [void]$h.Clear(1) } catch {}
    Add-Content -Path $Out -Value "$($files[$i])`t$pages" -Encoding utf8
  }
  try { $h.Quit() } catch {}
  Set-Content -Path "$Out.progress" -Value $files.Count
  exit 0
}

# 한글 보안 승인 창("글을 이용하여 위 파일에 접근하려는 시도…")이 가끔 떠 자동화가 멈춘다. 이 점검이 띄운 한글의 창이고
# 목록에 있는 문서를 묻는 것일 때만 "접근 허용"을 누른다(다른 한글·다른 파일은 건드리지 않는다).
Add-Type -AssemblyName UIAutomationClient, UIAutomationTypes
function Approve-Access([datetime]$Since) {
  $auto = [System.Windows.Automation.AutomationElement]
  $mine = Get-CimInstance Win32_Process -Filter "Name='Hwp.exe'" | Where-Object { $_.CreationDate -ge $Since }
  foreach ($p in $mine) {
    $cond = New-Object System.Windows.Automation.PropertyCondition($auto::ProcessIdProperty, [int]$p.ProcessId)
    foreach ($w in $auto::RootElement.FindAll([System.Windows.Automation.TreeScope]::Children, $cond)) {
      $all = $w.FindAll([System.Windows.Automation.TreeScope]::Descendants, [System.Windows.Automation.Condition]::TrueCondition)
      $text = ($all | ForEach-Object { $_.Current.Name }) -join "`n"
      if ($text -notmatch "접근하려는 시도") { continue }
      if (-not ($files | Where-Object { $text.Contains($_) })) { continue }
      $button = $all | Where-Object { $_.Current.ControlType -eq [System.Windows.Automation.ControlType]::Button -and $_.Current.Name -like "접근 허용*" } | Select-Object -First 1
      if ($button) { $button.GetCurrentPattern([System.Windows.Automation.InvokePattern]::Pattern).Invoke() }
    }
  }
}

$next = 0
while ($next -lt $files.Count) {
  $start = Get-Date
  Set-Content -Path "$Out.progress" -Value $next
  $arguments = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $PSCommandPath,
    "-List", $List, "-Out", $Out, "-From", $next)
  $process = Start-Process powershell -ArgumentList $arguments -PassThru -WindowStyle Hidden
  # 진행 번호가 $Timeout 초 동안 그대로면 멈춘 것으로 본다.
  $last = -1; $since = Get-Date
  while (-not $process.HasExited) {
    Start-Sleep -Seconds 2
    try { Approve-Access $start } catch {}
    $now = [int](Get-Content "$Out.progress" -ErrorAction SilentlyContinue)
    if ($now -ne $last) { $last = $now; $since = Get-Date }
    elseif (((Get-Date) - $since).TotalSeconds -gt $Timeout) {
      Stop-Process -Id $process.Id -Force -Confirm:$false -ErrorAction SilentlyContinue
      Start-Sleep -Seconds 2
      Get-CimInstance Win32_Process -Filter "Name='Hwp.exe'" |
        Where-Object { $_.CreationDate -ge $start } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -Confirm:$false -ErrorAction SilentlyContinue }
      Add-Content -Path $Out -Value "$($files[$now])`ttimeout" -Encoding utf8
      $last = $now + 1
      break
    }
  }
  $done = [int](Get-Content "$Out.progress" -ErrorAction SilentlyContinue)
  if ($done -ge $files.Count) { break }
  if ($last -le $done) {
    # 시간 안에 끝났는데 목록을 다 못 돌았다: 한글이 그 문서에서 죽었다.
    Add-Content -Path $Out -Value "$($files[$done])`tcrash" -Encoding utf8
    $last = $done + 1
  }
  $next = $last
}
Remove-Item "$Out.progress" -ErrorAction SilentlyContinue
