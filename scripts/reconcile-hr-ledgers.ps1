# 用法：powershell -ExecutionPolicy Bypass -File scripts\reconcile-hr-ledgers.ps1 -AccountId <账号ID> -JShell <jshell路径>
# 说明：直连本机 EHR 与 MySQL，仅执行 GET / SELECT；覆盖七个新增台账的指标卡和图表卡。
# 账号需具备各台账的 all/view 权限，OrgCodes 必须与账号数据范围一致；team-only 账号不适用。

param(
  [Parameter(Mandatory = $true)][string]$AccountId,
  [string]$BaseUrl      = 'http://127.0.0.1:9006',
  [string]$AccountName  = 'hr-ledger-reconciliation',
  [string]$Permissions  = 'employee:view,leave:all,attendance:overtime:all,recruit:board,contract:view',
  [bool]$Super          = $true,
  [string[]]$OrgCodes   = @('XC0001'),
  [string]$From         = (Get-Date).AddMonths(-119).ToString('yyyy-MM-01'),
  [string]$To           = (Get-Date).ToString('yyyy-MM-dd'),
  [string]$DbUrl        = 'jdbc:mysql://127.0.0.1:3306/ehr?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true',
  [string]$DbUser       = 'root',
  [string]$DbPassword   = 'root',
  [string]$JShell       = 'jshell',
  [string]$MySqlConnectorJar = ''
)

$ErrorActionPreference = 'Stop'
$fromDate = [DateTime]::ParseExact($From, 'yyyy-MM-dd', [Globalization.CultureInfo]::InvariantCulture)
$toDate = [DateTime]::ParseExact($To, 'yyyy-MM-dd', [Globalization.CultureInfo]::InvariantCulture)
if ($fromDate -gt $toDate) { throw 'From 不能晚于 To' }
if (($toDate.Year - $fromDate.Year) * 12 + $toDate.Month - $fromDate.Month -gt 119) { throw '日期跨度不能超过 120 个月' }
if ($OrgCodes.Count -eq 0) { throw '至少指定一个组织编码' }
foreach ($code in $OrgCodes) { if ($code -notmatch '^[A-Za-z0-9]+$') { throw "非法组织编码：$code" } }
$toExclusive = $toDate.AddDays(1).ToString('yyyy-MM-dd')

if (-not $MySqlConnectorJar) {
  $repository = if ($env:MAVEN_REPO) { $env:MAVEN_REPO } else { Join-Path $env:USERPROFILE '.m2\repository' }
  $connectorDir = Join-Path $repository 'com\mysql\mysql-connector-j'
  $MySqlConnectorJar = Get-ChildItem $connectorDir -Filter 'mysql-connector-j-*.jar' -Recurse -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' } |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $MySqlConnectorJar -or -not (Test-Path $MySqlConnectorJar)) { throw '找不到 MySQL Connector/J，请通过 -MySqlConnectorJar 指定' }
$jshellCommand = Get-Command $JShell -ErrorAction SilentlyContinue
if (-not $jshellCommand) { throw '找不到 jshell，请通过 -JShell 指定 JDK 17+ 的 jshell.exe' }

function Invoke-Ledger([string]$Path, [string]$Query) {
  $headers = @{
    'X-Account-Id'    = $AccountId
    'X-Account-Name'  = $AccountName
    'X-Account-Super' = $Super.ToString().ToLowerInvariant()
    'X-Account-Perms' = $Permissions
    'X-App-Code'      = 'ehr'
  }
  $response = Invoke-WebRequest -UseBasicParsing -Method Get -Uri "$BaseUrl/api/ehr/hr-ledgers/$Path$Query" -Headers $headers -TimeoutSec 30
  # Windows PowerShell 对未声明 charset 的 JSON 可能误用 ANSI，直接按 UTF-8 解码原始响应。
  $json = [Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray())
  return ($json | ConvertFrom-Json).data
}

$period = "from=$From&to=$To"
$ledgers = @{
  workforce       = Invoke-Ledger 'workforce' '?page=1&size=100'
  regularization  = Invoke-Ledger 'regularization' '?page=1&size=100&expiry=ALL&approvalStatus=ALL'
  leave           = Invoke-Ledger 'leaves' "?page=1&size=100&$period&type=ALL&status=ALL"
  overtime        = Invoke-Ledger 'overtimes' "?page=1&size=100&$period&type=ALL&status=ALL&settleStatus=ALL"
  recruitment     = Invoke-Ledger 'recruitment' "?page=1&size=100&$period&stage=ALL"
  profileQuality  = Invoke-Ledger 'profile-quality' '?page=1&size=100&issue=ALL'
  employeeChanges = Invoke-Ledger 'employee-changes' "?page=1&size=100&$period&status=ALL"
}

function Scope-Sql([string]$Alias, [bool]$Deleted = $true) {
  $parts = @($OrgCodes | ForEach-Object { "$Alias.org_code LIKE '$_%'" })
  $prefix = if ($Deleted) { "$Alias.deleted = 0 AND " } else { '' }
  return "$prefix(" + ($parts -join ' OR ') + ')'
}
$employeeScope = Scope-Sql 'e'
$candidateScope = Scope-Sql 'c'
$offerScope = Scope-Sql 'o'
$contractScope = Scope-Sql 'ct'
$leaveScope = (Scope-Sql 'l') + ' AND ' + (Scope-Sql 'e')
$overtimeScope = (Scope-Sql 'o' $false) + ' AND ' + (Scope-Sql 'e')
$changeScope = 'ch.deleted = 0 AND ' + (Scope-Sql 'e')
$orgJoin = 'LEFT JOIN ehr_org_node org ON org.id = e.org_id AND org.deleted = 0'
$orgName = "COALESCE(NULLIF(TRIM(org.short_name), ''), org.name, '未知组织')"
$activeContract = "NOT EXISTS (SELECT 1 FROM ehr_emp_contract ct WHERE ct.employee_id = e.id AND ct.status = 'ACTIVE' AND $contractScope)"
$profileIssues = "((e.account_id IS NULL) + (e.org_id IS NULL OR e.position IS NULL OR TRIM(e.position) = '' OR e.hire_date IS NULL) + (e.seniority_start_date IS NULL) + ($activeContract))"

$queries = @()
function Add-Row([string]$Name, [string]$Sql) { $script:queries += @{ Type = 'r'; Name = $Name; Sql = $Sql } }
function Add-Map([string]$Name, [string]$Sql) { $script:queries += @{ Type = 'm'; Name = $Name; Sql = $Sql } }

Add-Row 'workforce' "SELECT COUNT(*) total, COALESCE(SUM(e.status IN (30,40)),0) active, COALESCE(SUM(e.status = 30),0) probation, COALESCE(SUM(e.status IN (10,20,25)),0) pending, COALESCE(SUM(e.status = 50),0) offboarding FROM ehr_employee e WHERE $employeeScope"
Add-Map 'workforceOrg' "SELECT $orgName k, COUNT(*) v FROM ehr_employee e $orgJoin WHERE $employeeScope AND e.status IN (30,40) GROUP BY $orgName"
Add-Map 'workforceEducation' "SELECT COALESCE(NULLIF(TRIM(e.education),''),'UNKNOWN') k, COUNT(*) v FROM ehr_employee e WHERE $employeeScope AND e.status IN (30,40) GROUP BY BINARY COALESCE(NULLIF(TRIM(e.education),''),'UNKNOWN')"
$ageCase = "CASE WHEN e.birthday IS NULL OR e.birthday > CURDATE() THEN 'UNKNOWN' WHEN TIMESTAMPDIFF(YEAR,e.birthday,CURDATE()) < 25 THEN '<25' WHEN TIMESTAMPDIFF(YEAR,e.birthday,CURDATE()) < 30 THEN '25-29' WHEN TIMESTAMPDIFF(YEAR,e.birthday,CURDATE()) < 35 THEN '30-34' WHEN TIMESTAMPDIFF(YEAR,e.birthday,CURDATE()) < 40 THEN '35-39' WHEN TIMESTAMPDIFF(YEAR,e.birthday,CURDATE()) < 45 THEN '40-44' WHEN TIMESTAMPDIFF(YEAR,e.birthday,CURDATE()) < 50 THEN '45-49' ELSE '50+' END"
Add-Map 'workforceAge' "SELECT $ageCase k, COUNT(*) v FROM ehr_employee e WHERE $employeeScope AND e.status IN (30,40) GROUP BY $ageCase"
$seniorityYears = "COALESCE(e.seniority_years, FLOOR(GREATEST(0,DATEDIFF(CURDATE(),e.seniority_start_date)) / 365.25 * 10) / 10)"
$seniorityCase = "CASE WHEN e.seniority_years IS NULL AND e.seniority_start_date IS NULL THEN 'UNKNOWN' WHEN $seniorityYears < 1 THEN '<1' WHEN $seniorityYears < 3 THEN '1-2' WHEN $seniorityYears < 5 THEN '3-4' WHEN $seniorityYears < 10 THEN '5-9' ELSE '10+' END"
Add-Map 'workforceSeniority' "SELECT $seniorityCase k, COUNT(*) v FROM ehr_employee e WHERE $employeeScope AND e.status IN (30,40) GROUP BY $seniorityCase"

Add-Row 'regularization' "SELECT COUNT(*) total, COALESCE(SUM(e.expected_regularize_date < CURDATE()),0) overdue, COALESCE(SUM(e.expected_regularize_date >= CURDATE() AND e.expected_regularize_date <= DATE_ADD(CURDATE(),INTERVAL 30 DAY)),0) due30, COALESCE(SUM(COALESCE(e.regularize_status,0) IN (1,2)),0) approving FROM ehr_employee e WHERE $employeeScope AND e.status = 30"
Add-Map 'regularizationMonthly' "SELECT DATE_FORMAT(e.expected_regularize_date,'%Y-%m') k, COUNT(*) v FROM ehr_employee e WHERE $employeeScope AND e.status = 30 AND e.expected_regularize_date >= CURDATE() AND e.expected_regularize_date <= LAST_DAY(DATE_ADD(CURDATE(),INTERVAL 5 MONTH)) GROUP BY DATE_FORMAT(e.expected_regularize_date,'%Y-%m')"
Add-Map 'regularizationOrgOverdue' "SELECT $orgName k, COALESCE(SUM(e.expected_regularize_date < CURDATE()),0) v FROM ehr_employee e $orgJoin WHERE $employeeScope AND e.status = 30 GROUP BY $orgName"
Add-Map 'regularizationOrgDue30' "SELECT $orgName k, COALESCE(SUM(e.expected_regularize_date >= CURDATE() AND e.expected_regularize_date <= DATE_ADD(CURDATE(),INTERVAL 30 DAY)),0) v FROM ehr_employee e $orgJoin WHERE $employeeScope AND e.status = 30 GROUP BY $orgName"
Add-Map 'regularizationStatus' "SELECT CAST(COALESCE(e.regularize_status,0) AS CHAR) k, COUNT(*) v FROM ehr_employee e WHERE $employeeScope AND e.status = 30 GROUP BY COALESCE(e.regularize_status,0)"

$leaveFrom = "l.start_date BETWEEN '$From' AND '$To'"
Add-Row 'leave' "SELECT COUNT(*) total, COUNT(DISTINCT CASE WHEN l.status IN (1,4) THEN l.employee_id END) effectivePeople, COALESCE(SUM(CASE WHEN l.status IN (1,4) THEN l.days ELSE 0 END),0) effectiveDays, COALESCE(SUM(CASE WHEN l.status = 0 THEN l.days ELSE 0 END),0) pendingDays, COALESCE(SUM(l.status = 0),0) pendingCount FROM ehr_leave l JOIN ehr_employee e ON e.id = l.employee_id WHERE $leaveScope AND $leaveFrom"
Add-Map 'leaveMonthly' "SELECT DATE_FORMAT(l.start_date,'%Y-%m') k, COALESCE(SUM(l.days),0) v FROM ehr_leave l JOIN ehr_employee e ON e.id = l.employee_id WHERE $leaveScope AND $leaveFrom AND l.status IN (1,4) GROUP BY DATE_FORMAT(l.start_date,'%Y-%m')"
Add-Map 'leaveTypeCount' "SELECT UPPER(TRIM(l.leave_type)) k, COUNT(*) v FROM ehr_leave l JOIN ehr_employee e ON e.id = l.employee_id WHERE $leaveScope AND $leaveFrom AND l.status IN (1,4) GROUP BY UPPER(TRIM(l.leave_type))"
Add-Map 'leaveTypeDays' "SELECT UPPER(TRIM(l.leave_type)) k, COALESCE(SUM(l.days),0) v FROM ehr_leave l JOIN ehr_employee e ON e.id = l.employee_id WHERE $leaveScope AND $leaveFrom AND l.status IN (1,4) GROUP BY UPPER(TRIM(l.leave_type))"
Add-Map 'leaveOrg' "SELECT $orgName k, COALESCE(SUM(l.days),0) v FROM ehr_leave l JOIN ehr_employee e ON e.id = l.employee_id $orgJoin WHERE $leaveScope AND $leaveFrom AND l.status IN (1,4) GROUP BY $orgName"

$overtimeFrom = "o.work_date BETWEEN '$From' AND '$To'"
Add-Row 'overtime' "SELECT COUNT(*) total, COALESCE(SUM(CASE WHEN o.status = 1 THEN o.hours ELSE 0 END),0) approvedHours, COALESCE(SUM(CASE WHEN o.status = 1 AND o.settle_status = 1 THEN o.effective_hours ELSE 0 END),0) effectiveHours, COALESCE(SUM(CASE WHEN o.status = 1 AND o.settle_status = 0 THEN o.hours ELSE 0 END),0) unsettledHours, COALESCE(SUM(o.status = 0),0) pendingCount FROM ehr_overtime o JOIN ehr_employee e ON e.id = o.employee_id WHERE $overtimeScope AND $overtimeFrom"
Add-Map 'overtimeMonthly' "SELECT DATE_FORMAT(o.work_date,'%Y-%m') k, COALESCE(SUM(o.hours),0) v FROM ehr_overtime o JOIN ehr_employee e ON e.id = o.employee_id WHERE $overtimeScope AND $overtimeFrom AND o.status = 1 GROUP BY DATE_FORMAT(o.work_date,'%Y-%m')"
Add-Map 'overtimeTypes' "SELECT UPPER(TRIM(o.type)) k, COALESCE(SUM(o.hours),0) v FROM ehr_overtime o JOIN ehr_employee e ON e.id = o.employee_id WHERE $overtimeScope AND $overtimeFrom AND o.status = 1 GROUP BY UPPER(TRIM(o.type))"
Add-Map 'overtimeOrgHours' "SELECT $orgName k, COALESCE(SUM(CASE WHEN o.status = 1 THEN o.hours ELSE 0 END),0) v FROM ehr_overtime o JOIN ehr_employee e ON e.id = o.employee_id $orgJoin WHERE $overtimeScope AND $overtimeFrom GROUP BY $orgName"
Add-Map 'overtimeOrgUnsettled' "SELECT $orgName k, COALESCE(SUM(CASE WHEN o.status = 1 AND o.settle_status = 0 THEN o.hours ELSE 0 END),0) v FROM ehr_overtime o JOIN ehr_employee e ON e.id = o.employee_id $orgJoin WHERE $overtimeScope AND $overtimeFrom GROUP BY $orgName"

$candidatePeriod = "c.created_at >= '$From 00:00:00' AND c.created_at < '$toExclusive 00:00:00'"
$interviewPeriod = "i.decided_at >= '$From 00:00:00' AND i.decided_at < '$toExclusive 00:00:00'"
$offerPeriod = "o.confirmed_at >= '$From 00:00:00' AND o.confirmed_at < '$toExclusive 00:00:00'"
$hirePeriod = "e.hire_date >= '$From' AND e.hire_date < '$toExclusive'"
Add-Row 'recruitment' "SELECT (SELECT COUNT(*) FROM ehr_candidate c WHERE $candidateScope) total, (SELECT COUNT(*) FROM ehr_candidate c WHERE $candidateScope AND $candidatePeriod) newCandidates, (SELECT COUNT(DISTINCT i.candidate_id) FROM ehr_interview i JOIN ehr_candidate c ON c.id = i.candidate_id AND c.deleted = 0 WHERE i.deleted = 0 AND $candidateScope AND i.status = 'DONE' AND i.round_result IN ('PASS','FAIL') AND $interviewPeriod) interviewedCandidates, (SELECT COUNT(*) FROM ehr_offer o JOIN ehr_candidate c ON c.id = o.candidate_id AND c.deleted = 0 WHERE $offerScope AND $candidateScope AND $offerPeriod) confirmedOffers, (SELECT COUNT(DISTINCT e.id) FROM ehr_employee e JOIN ehr_offer o ON o.id = e.source_offer_id AND o.deleted = 0 JOIN ehr_candidate c ON c.id = o.candidate_id AND c.deleted = 0 WHERE $employeeScope AND $offerScope AND $candidateScope AND $hirePeriod) recruitedEmployees"
Add-Map 'recruitmentSources' "SELECT COALESCE(NULLIF(TRIM(c.source),''),'UNKNOWN') k, COUNT(*) v FROM ehr_candidate c WHERE $candidateScope GROUP BY BINARY COALESCE(NULLIF(TRIM(c.source),''),'UNKNOWN')"
Add-Map 'recruitmentStages' "SELECT c.stage k, COUNT(*) v FROM ehr_candidate c WHERE $candidateScope GROUP BY c.stage"
$positionName = "COALESCE(NULLIF(TRIM(c.position),''),'未填写职位')"
Add-Map 'recruitmentPositionCandidates' "SELECT $positionName k, COUNT(*) v FROM ehr_candidate c WHERE $candidateScope GROUP BY BINARY $positionName"
Add-Map 'recruitmentPositionOffers' "SELECT $positionName k, COUNT(o.id) v FROM ehr_candidate c LEFT JOIN ehr_offer o ON o.candidate_id = c.id AND o.deleted = 0 AND $offerScope AND $offerPeriod WHERE $candidateScope GROUP BY BINARY $positionName"

Add-Row 'profileQuality' "SELECT COUNT(*) activePeople, COALESCE(SUM(($profileIssues) > 0),0) problemPeople, COALESCE(SUM($profileIssues),0) problemItems, COALESCE(SUM(e.account_id IS NULL),0) noAccount, COALESCE(SUM(e.org_id IS NULL OR e.position IS NULL OR TRIM(e.position) = '' OR e.hire_date IS NULL),0) incomplete, COALESCE(SUM(e.seniority_start_date IS NULL),0) noSeniority, COALESCE(SUM($activeContract),0) noContract FROM ehr_employee e WHERE $employeeScope AND e.status IN (30,40)"
Add-Map 'profileQualityOrg' "SELECT $orgName k, COUNT(*) v FROM ehr_employee e $orgJoin WHERE $employeeScope AND e.status IN (30,40) AND ($profileIssues) > 0 GROUP BY $orgName"

$changePeriod = "ch.created_at >= '$From 00:00:00' AND ch.created_at < '$toExclusive 00:00:00'"
Add-Row 'employeeChanges' "SELECT COUNT(*) total, COALESCE(SUM(ch.status = 0),0) approving, COALESCE(SUM(ch.status = 1),0) effective, COALESCE(SUM(ch.status = 2),0) rejected, COALESCE(SUM(ch.status = 3),0) canceled, COALESCE(SUM(ch.base_changes),0) baseChanges, COALESCE(SUM(ch.add_count),0) addCount, COALESCE(SUM(ch.mod_count),0) modCount, COALESCE(SUM(ch.del_count),0) delCount FROM ehr_emp_change ch JOIN ehr_employee e ON e.id = ch.employee_id WHERE $changeScope AND $changePeriod"
Add-Map 'employeeChangesMonthly' "SELECT DATE_FORMAT(ch.created_at,'%Y-%m') k, COUNT(*) v FROM ehr_emp_change ch JOIN ehr_employee e ON e.id = ch.employee_id WHERE $changeScope AND $changePeriod GROUP BY DATE_FORMAT(ch.created_at,'%Y-%m')"
$changeFieldKey = "CASE WHEN f.field_key IS NOT NULL AND TRIM(f.field_key) <> '' THEN TRIM(f.field_key) WHEN f.field_label IS NOT NULL AND TRIM(f.field_label) <> '' THEN TRIM(f.field_label) ELSE 'UNKNOWN' END"
Add-Map 'employeeChangesFields' "SELECT $changeFieldKey k, COUNT(*) v FROM ehr_emp_change_field f JOIN ehr_emp_change ch ON ch.id = f.change_id JOIN ehr_employee e ON e.id = ch.employee_id WHERE $changeScope AND $changePeriod GROUP BY BINARY $changeFieldKey ORDER BY v DESC, k LIMIT 10"
$changeOrgName = "COALESCE(NULLIF(TRIM(ch.org_name), ''), NULLIF(TRIM(org.short_name), ''), org.name, '未知组织')"
Add-Map 'employeeChangesOrg' "SELECT $changeOrgName k, COUNT(*) v FROM ehr_emp_change ch JOIN ehr_employee e ON e.id = ch.employee_id LEFT JOIN ehr_org_node org ON org.id = ch.org_id AND org.deleted = 0 WHERE $changeScope AND $changePeriod GROUP BY $changeOrgName"

function Java-String([string]$Value) {
  return $Value.Replace('\', '\\').Replace('"', '\"').Replace("`r", ' ').Replace("`n", ' ')
}
function Java-Base64([string]$Value) {
  return [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Value))
}
$jshellLines = [Collections.Generic.List[string]]::new()
$jshellLines.Add('import java.sql.*;')
$jshellLines.Add('import java.util.Base64;')
$jshellLines.Add('import java.nio.charset.StandardCharsets;')
$jshellLines.Add('Class.forName("com.mysql.cj.jdbc.Driver");')
$jshellLines.Add(('var conn = DriverManager.getConnection("{0}", "{1}", "{2}");' -f (Java-String $DbUrl), (Java-String $DbUser), (Java-String $DbPassword)))
$jshellLines.Add('void r(String name, String encodedSql) { try (var st = conn.createStatement(); var rs = st.executeQuery(new String(Base64.getDecoder().decode(encodedSql), StandardCharsets.UTF_8))) { var md = rs.getMetaData(); if (rs.next()) { for (int i = 1; i <= md.getColumnCount(); i++) { var value = rs.getString(i); System.out.println("DBV|" + name + "." + md.getColumnLabel(i) + "|" + (value == null ? "0" : value)); } } } catch (Exception e) { System.out.println("DBF|" + name + "|" + e.getClass().getSimpleName()); } }')
$jshellLines.Add('void m(String name, String encodedSql) { try (var st = conn.createStatement(); var rs = st.executeQuery(new String(Base64.getDecoder().decode(encodedSql), StandardCharsets.UTF_8))) { while (rs.next()) { var key = rs.getString(1); var encodedKey = Base64.getEncoder().encodeToString((key == null ? "" : key).getBytes(StandardCharsets.UTF_8)); System.out.println("DBM|" + name + "|" + encodedKey + "|" + rs.getString(2)); } } catch (Exception e) { System.out.println("DBF|" + name + "|" + e.getClass().getSimpleName()); } }')
foreach ($query in $queries) {
  $jshellLines.Add(('{0}("{1}", "{2}");' -f $query.Type, $query.Name, (Java-Base64 $query.Sql)))
  $jshellLines.Add(('System.out.println("DBQ|{0}");' -f $query.Name))
}
$jshellLines.Add('conn.close();')
$jshellLines.Add('/exit')
$previousErrorAction = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$dbOutput = $jshellLines -join "`n" | & $JShell --class-path $MySqlConnectorJar 2>&1
$jshellExitCode = $LASTEXITCODE
$ErrorActionPreference = $previousErrorAction
if ($jshellExitCode -ne 0) { throw "JDBC 对账执行失败，退出码 $jshellExitCode" }

$db = @{}
$dbMaps = @{}
$executed = @{}
$failedQueries = @{}
foreach ($line in $dbOutput) {
  $text = [string]$line
  if ($text -match '^DBV\|([^|]+)\|(.*)$') { $db[$Matches[1]] = $Matches[2] }
  elseif ($text -match '^DBM\|([^|]+)\|([^|]*)\|(.*)$') {
    if (-not $dbMaps.ContainsKey($Matches[1])) { $dbMaps[$Matches[1]] = @{} }
    $key = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($Matches[2]))
    $dbMaps[$Matches[1]][$key] = $Matches[3]
  }
  elseif ($text -match '^DBQ\|([^|]+)$') { $executed[$Matches[1]] = $true }
  elseif ($text -match '^DBF\|([^|]+)\|(.*)$') { $failedQueries[$Matches[1]] = $Matches[2] }
}
if ($failedQueries.Count -gt 0) { throw "SQL 执行失败：$(($failedQueries.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join ', ')" }
$missingQueries = @($queries | Where-Object { -not $executed.ContainsKey($_.Name) } | ForEach-Object { $_.Name })
if ($missingQueries.Count -gt 0) { throw "以下 SQL 未成功执行：$($missingQueries -join ', ')" }

function Db-Value([string]$Key) {
  if (-not $db.ContainsKey($Key)) { throw "缺少数据库对账字段：$Key；已返回字段：$($db.Keys -join ', ')" }
  return [decimal]$db[$Key]
}
function Db-Map([string]$Key) {
  if ($dbMaps.ContainsKey($Key)) { return $dbMaps[$Key] }
  return @{}
}
$results = @()
function Add-Result([string]$Name, [bool]$Passed, [string]$Detail = '') {
  $script:results += [pscustomobject]@{ Name = $Name; Passed = $Passed; Detail = $Detail }
  if ($Passed) { Write-Host "PASS $Name" -ForegroundColor Green }
  else { Write-Host "FAIL $Name $Detail" -ForegroundColor Red }
}
function Check-Value([string]$Name, $Expected, $Actual) {
  $expectedValue = [decimal]$Expected
  $actualValue = [decimal]$Actual
  Add-Result $Name ($expectedValue -eq $actualValue) "期望 $expectedValue，实际 $actualValue"
}
function Check-Map([string]$Name, $Items, [string]$KeyField, [string]$ValueField, [hashtable]$Expected) {
  $actual = @{}
  foreach ($item in @($Items)) {
    $key = [string]$item.PSObject.Properties[$KeyField].Value
    $actual[$key] = [decimal]$item.PSObject.Properties[$ValueField].Value
  }
  $keys = @(@($actual.Keys) + @($Expected.Keys) | Sort-Object -Unique)
  $differences = @()
  foreach ($key in $keys) {
    $expectedValue = if ($Expected.ContainsKey($key)) { [decimal]$Expected[$key] } else { [decimal]0 }
    $actualValue = if ($actual.ContainsKey($key)) { [decimal]$actual[$key] } else { [decimal]0 }
    if ($expectedValue -ne $actualValue) { $differences += "$key 期望 $expectedValue 实际 $actualValue" }
  }
  Add-Result $Name ($differences.Count -eq 0) (($differences | Select-Object -First 5) -join '; ')
}

$data = $ledgers.workforce
Check-Value '在职结构/当前在职' (Db-Value 'workforce.active') $data.active
Check-Value '在职结构/试用期' (Db-Value 'workforce.probation') $data.probation
Check-Value '在职结构/待入职' (Db-Value 'workforce.pending') $data.pending
Check-Value '在职结构/离职中' (Db-Value 'workforce.offboarding') $data.offboarding
Check-Value '在职结构/明细总数' (Db-Value 'workforce.total') $data.page.total
Check-Map '在职结构/各直接组织在职人数' $data.byOrg 'key' 'value' (Db-Map 'workforceOrg')
Check-Map '在职结构/学历分布' $data.educations 'key' 'value' (Db-Map 'workforceEducation')
Check-Map '在职结构/年龄分布' $data.ageGroups 'key' 'value' (Db-Map 'workforceAge')
Check-Map '在职结构/司龄分布' $data.seniorityGroups 'key' 'value' (Db-Map 'workforceSeniority')

$data = $ledgers.regularization
Check-Value '试用期转正/当前试用期' (Db-Value 'regularization.total') $data.current
Check-Value '试用期转正/已逾期' (Db-Value 'regularization.overdue') $data.overdue
Check-Value '试用期转正/三十天内' (Db-Value 'regularization.due30') $data.due30
Check-Value '试用期转正/审批中' (Db-Value 'regularization.approving') $data.approving
Check-Value '试用期转正/明细总数' (Db-Value 'regularization.total') $data.page.total
Check-Map '试用期转正/未来6个月预计转正' $data.monthly 'month' 'value' (Db-Map 'regularizationMonthly')
Check-Map '试用期转正/组织已逾期' $data.byOrg 'org' 'overdue' (Db-Map 'regularizationOrgOverdue')
Check-Map '试用期转正/组织三十天内' $data.byOrg 'org' 'due30' (Db-Map 'regularizationOrgDue30')
Check-Map '试用期转正/审批状态分布' $data.approvalStatuses 'key' 'value' (Db-Map 'regularizationStatus')

$data = $ledgers.leave
Check-Value '请假统计/有效请假人数' (Db-Value 'leave.effectivePeople') $data.effectivePeople
Check-Value '请假统计/有效请假天数' (Db-Value 'leave.effectiveDays') $data.effectiveDays
Check-Value '请假统计/待审批天数' (Db-Value 'leave.pendingDays') $data.pendingDays
Check-Value '请假统计/待审批单数' (Db-Value 'leave.pendingCount') $data.pendingCount
Check-Value '请假统计/明细总数' (Db-Value 'leave.total') $data.page.total
Check-Map '请假统计/月度有效请假天数' $data.monthly 'month' 'value' (Db-Map 'leaveMonthly')
Check-Map '请假统计/类型有效单数' $data.types 'key' 'count' (Db-Map 'leaveTypeCount')
Check-Map '请假统计/类型有效天数' $data.types 'key' 'days' (Db-Map 'leaveTypeDays')
Check-Map '请假统计/组织有效请假天数' $data.byOrg 'org' 'value' (Db-Map 'leaveOrg')

$data = $ledgers.overtime
Check-Value '加班调休/已审批小时' (Db-Value 'overtime.approvedHours') $data.approvedHours
Check-Value '加班调休/有效调休小时' (Db-Value 'overtime.effectiveHours') $data.effectiveHours
Check-Value '加班调休/未结算小时' (Db-Value 'overtime.unsettledHours') $data.unsettledHours
Check-Value '加班调休/待审批单数' (Db-Value 'overtime.pendingCount') $data.pendingCount
Check-Value '加班调休/明细总数' (Db-Value 'overtime.total') $data.page.total
Check-Map '加班调休/月度已审批加班小时' $data.monthly 'month' 'value' (Db-Map 'overtimeMonthly')
Check-Map '加班调休/类型已审批小时' $data.types 'key' 'value' (Db-Map 'overtimeTypes')
Check-Map '加班调休/组织已审批小时' $data.byOrg 'org' 'hours' (Db-Map 'overtimeOrgHours')
Check-Map '加班调休/组织未结算小时' $data.byOrg 'org' 'unsettledHours' (Db-Map 'overtimeOrgUnsettled')

$data = $ledgers.recruitment
Check-Value '招聘转化/期间新增候选人' (Db-Value 'recruitment.newCandidates') $data.newCandidates
Check-Value '招聘转化/期间完成面试判定' (Db-Value 'recruitment.interviewedCandidates') $data.interviewedCandidates
Check-Value '招聘转化/期间确认Offer' (Db-Value 'recruitment.confirmedOffers') $data.confirmedOffers
Check-Value '招聘转化/期间招聘入职' (Db-Value 'recruitment.recruitedEmployees') $data.recruitedEmployees
Check-Value '招聘转化/明细总数' (Db-Value 'recruitment.total') $data.page.total
Check-Map '招聘转化/来源分布' $data.sources 'key' 'value' (Db-Map 'recruitmentSources')
Check-Map '招聘转化/当前阶段' $data.stages 'key' 'value' (Db-Map 'recruitmentStages')
Check-Map '招聘转化/职位候选人数' $data.positions 'position' 'candidates' (Db-Map 'recruitmentPositionCandidates')
Check-Map '招聘转化/职位确认Offer数' $data.positions 'position' 'confirmedOffers' (Db-Map 'recruitmentPositionOffers')

$data = $ledgers.profileQuality
Check-Value '档案质量/问题人数' (Db-Value 'profileQuality.problemPeople') $data.problemPeople
Check-Value '档案质量/问题项数' (Db-Value 'profileQuality.problemItems') $data.problemItems
Check-Value '档案质量/在职未开通账号' (Db-Value 'profileQuality.noAccount') $data.noAccount
Check-Value '档案质量/关键档案不完整' (Db-Value 'profileQuality.incomplete') $data.incomplete
Check-Value '档案质量/司龄起算日缺失' (Db-Value 'profileQuality.noSeniority') $data.noSeniority
Check-Value '档案质量/无有效合同' (Db-Value 'profileQuality.noContract') $data.noContract
Check-Value '档案质量/明细总数' (Db-Value 'profileQuality.problemPeople') $data.page.total
$issueExpected = @{ NO_ACCOUNT = Db-Value 'profileQuality.noAccount'; INCOMPLETE = Db-Value 'profileQuality.incomplete'; NO_SENIORITY = Db-Value 'profileQuality.noSeniority'; NO_CONTRACT = Db-Value 'profileQuality.noContract' }
Check-Map '档案质量/问题类型分布' $data.issueTypes 'key' 'value' $issueExpected
Check-Map '档案质量/组织问题人数' $data.byOrg 'key' 'value' (Db-Map 'profileQualityOrg')

$data = $ledgers.employeeChanges
Check-Value '人员信息变更/审批中' (Db-Value 'employeeChanges.approving') $data.approving
Check-Value '人员信息变更/已生效' (Db-Value 'employeeChanges.effective') $data.effective
Check-Value '人员信息变更/已驳回' (Db-Value 'employeeChanges.rejected') $data.rejected
Check-Value '人员信息变更/已撤销' (Db-Value 'employeeChanges.canceled') $data.canceled
Check-Value '人员信息变更/明细总数' (Db-Value 'employeeChanges.total') $data.page.total
Check-Map '人员信息变更/月度变更单量' $data.monthly 'month' 'value' (Db-Map 'employeeChangesMonthly')
Check-Map '人员信息变更/高频字段' $data.topFields 'key' 'value' (Db-Map 'employeeChangesFields')
Check-Map '人员信息变更/组织变更单量' $data.byOrg 'key' 'value' (Db-Map 'employeeChangesOrg')
$contentExpected = @{ BASE = Db-Value 'employeeChanges.baseChanges'; ADD = Db-Value 'employeeChanges.addCount'; MOD = Db-Value 'employeeChanges.modCount'; DEL = Db-Value 'employeeChanges.delCount' }
$contentActual = @(
  [pscustomobject]@{ key = 'BASE'; value = $data.baseChanges },
  [pscustomobject]@{ key = 'ADD'; value = $data.addCount },
  [pscustomobject]@{ key = 'MOD'; value = $data.modCount },
  [pscustomobject]@{ key = 'DEL'; value = $data.delCount }
)
Check-Map '人员信息变更/基本字段与子表增删改' $contentActual 'key' 'value' $contentExpected

$failed = @($results | Where-Object { -not $_.Passed })
Write-Host "=== 对账完成：$($results.Count - $failed.Count) 通过，$($failed.Count) 失败 ===" -ForegroundColor $(if ($failed.Count -eq 0) { 'Green' } else { 'Red' })
if ($failed.Count -gt 0) { exit 1 }
