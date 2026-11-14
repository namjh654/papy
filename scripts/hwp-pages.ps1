# 한컴 한글(Windows) 로 문서마다 쪽 수를 잰다(한글 기준 점검, docs/formats/test-corpus.md).
#
#   powershell -ExecutionPolicy Bypass -File scripts/hwp-pages.ps1 -List <문서 경로 목록> -Out <결과.tsv> [-Timeout 60]
#
# 한글 자동화 개체(HWPFrame.HwpObject) 하나를 숨긴 창으로 띄워 목록의 문서를 차례로 열고 PageCount 를 적는다
# (열 수 없으면 error). 한글을 띄우는 데 20초 남짓 걸려 문서마다 새로 띄우지 않는다.
# 메시지 상자는 SetMessageBoxMode 로 한글이 스스로 답한다. 한컴 보안 모듈이 등록돼 있지 않아 보안 승인 창이 뜨면
# 목록 문서에 한해 "접근 허용"을 누른다(아래 Approve-Access). 그래도 남는 다른 창은 누르지 않고
# 문서 번호와 글을 <결과>.dialogs 에 남긴다.
# 한 문서가 시간을 넘기면(멈춤) 이 점검이 띄운 한글(시작 뒤에 생긴 Hwp.exe)만 닫고 timeout 으로 적은 뒤
# 다음 문서부터 한글을 다시 띄운다. 사용자가 직접 연 한글은 건드리지 않는다.
param([string]$List, [string]$Out, [int]$Timeout = 60, [int]$From = -1)

$files = @(Get-Content $List | Where-Object { $_ })

if ($From -ge 0) {
  # 일꾼: $From 번째부터 열어 한 줄씩 적는다. 열기 전에 진행 표시 파일에 번호를 남긴다.
  $h = New-Object -ComObject HWPFrame.HwpObject
  try { $h.XHwpWindows.Item(0).Visible = $false } catch {}
  # 한컴 보안 모듈(FilePathCheckerModule DLL)이 HKCU\Software\HNC\HwpAutomation\Modules 에 등록돼 있으면
  # 그 값 이름으로 올린다. 그러면 보안 승인 창이 뜨지 않는다(없으면 아래 Approve-Access 가 대신 누른다).
  $modules = Get-Item "HKCU:\Software\HNC\HwpAutomation\Modules" -ErrorAction SilentlyContinue
  foreach ($name in @($modules.Property) + "FilePathCheckerModule" | Select-Object -Unique) {
    try { [void]$h.RegisterModule("FilePathCheckDLL", $name) } catch {}
  }
  # 메시지 상자(예: "표가 손상되었습니다… 복원할까요?")에 한글이 스스로 답하게 해 창이 화면에 남지 않게 한다:
  # 확인 0x1, 확인/취소→확인 0x10, 중단/재시도/무시→무시 0x400, 예/아니요/취소→예 0x1000,
  # 예/아니요→예 0x10000, 재시도/취소→취소 0x200000.
  try { [void]$h.SetMessageBoxMode(0x211411) } catch {}
  for ($i = $From; $i -lt $files.Count; $i++) {
    Set-Content -Path "$Out.progress" -Value $i
    $pages = "error"
    try {
      if ($h.Open($files[$i], "", "forceopen:true;suspendpassword:true")) { $pages = $h.PageCount }
    } catch {
      # 자동화 개체가 예외를 내면 한글이 죽은 것이다: 감독이 이 문서를 crash 로 적고 다음 문서부터 다시 띄운다.
      exit 1
    }
    if ("$pages" -notmatch '^\d+$') {
      # 열었다는데 쪽 수를 못 읽으면 한글이 죽어 가는 중이다(그 뒤 문서가 모두 error 가 됐다).
      exit 1
    }
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
      if ($text -notmatch "접근하려는 시도") {
        # 다른 대화 상자(예: "표가 손상되었습니다… 복구할까요?")는 누르지 않고 글만 남긴다(그 문서는 시간이 넘으면 timeout).
        $line = "$(Get-Content "$Out.progress" -ErrorAction SilentlyContinue)`t$($w.Current.Name)`t$($text -replace '\s+', ' ')"
        if ($text -and -not (Select-String -Path "$Out.dialogs" -SimpleMatch $line -Quiet -ErrorAction SilentlyContinue)) {
          Add-Content -Path "$Out.dialogs" -Value $line -Encoding utf8
        }
        continue
      }
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
  # Start-Process 는 인자를 따옴표 없이 잇는다: 빈칸이 든 경로는 감싸 준다.
  $arguments = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "`"$PSCommandPath`"",
    "-List", "`"$List`"", "-Out", "`"$Out`"", "-From", $next)
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
