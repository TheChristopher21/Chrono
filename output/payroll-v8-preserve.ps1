param([string]$Phase='before')
$ErrorActionPreference='Stop'
$dump='C:/Program Files/MySQL/MySQL Server 8.0/bin/mysqldump.exe'
$root='C:/Users/siefe/WebstormProjects/Chrono/output'
$combined=[System.IO.MemoryStream]::new()
foreach($table in @('payslips','payslip_earnings','payslip_deductions','payslip_employer_contribs')) {
  $filter=if($table -eq 'payslips'){'id<=16'}else{'payslip_id<=16'}
  $file=Join-Path $root "payroll-v8-$Phase-$table.sql"
  & $dump --protocol=TCP --host=127.0.0.1 --port=13316 --user=root --compact --skip-extended-insert --no-create-info --skip-triggers --skip-add-locks --skip-disable-keys --skip-comments --skip-set-charset --order-by-primary --set-gtid-purged=OFF "--where=$filter" "--result-file=$file" chrono_restore_test_v8_20261106 $table
  if($LASTEXITCODE -ne 0){throw "Dump failed: $table"}
  $bytes=[System.IO.File]::ReadAllBytes($file);$combined.Write($bytes,0,$bytes.Length)
}
$path=Join-Path $root "payroll-v8-original-$Phase.sql"
[System.IO.File]::WriteAllBytes($path,$combined.ToArray());$combined.Dispose()
Get-FileHash -Algorithm SHA256 -LiteralPath $path | Select-Object Hash,Path
