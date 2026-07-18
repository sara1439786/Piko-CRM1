<?php
declare(strict_types=1);
require_once dirname(__DIR__) . '/includes/config.php';

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

function respond(array $payload, int $status = 200): never {
    http_response_code($status);
    echo json_encode($payload, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    exit;
}

function body_json(): array {
    $raw = file_get_contents('php://input') ?: '';
    if ($raw === '') return [];
    $data = json_decode($raw, true);
    if (!is_array($data)) respond(['ok'=>false,'message'=>'Invalid JSON body.'], 400);
    return $data;
}

function bearer_token(): string {
    $header = $_SERVER['HTTP_AUTHORIZATION'] ?? '';
    if (!preg_match('/^Bearer\s+(.+)$/i', $header, $m)) respond(['ok'=>false,'message'=>'Authentication required.'], 401);
    return trim($m[1]);
}

function require_api_user(mysqli $db): array {
    $hash = hash('sha256', bearer_token());
    $stmt = $db->prepare('SELECT u.id,u.name,u.email,u.role,u.is_authorized,u.access_level FROM api_tokens t JOIN admin_users u ON u.id=t.admin_id WHERE t.token_hash=? AND t.expires_at>NOW() LIMIT 1');
    $stmt->bind_param('s', $hash);
    $stmt->execute();
    $user = $stmt->get_result()->fetch_assoc();
    $stmt->close();
    if (!$user || !(int)$user['is_authorized']) respond(['ok'=>false,'message'=>'Session expired or account disabled.'], 401);
    $up = $db->prepare('UPDATE api_tokens SET last_used_at=NOW() WHERE token_hash=?');
    $up->bind_param('s', $hash); $up->execute(); $up->close();
    return $user;
}

function db_datetime_from_ms($value): ?string {
    if ($value === null || $value === '' || $value === false) return null;
    $ms = (int)$value;
    if ($ms <= 0) return null;
    return date('Y-m-d H:i:s', (int)floor($ms / 1000));
}

function ms_from_datetime(?string $value): ?int {
    if (!$value) return null;
    $ts = strtotime($value);
    return $ts === false ? null : $ts * 1000;
}

function lead_payload(array $r): array {
    return [
        'id'=>(int)$r['id'], 'name'=>(string)($r['name'] ?? ''), 'phone'=>(string)($r['phone'] ?? ''),
        'email'=>(string)($r['email'] ?? ''), 'status'=>(string)$r['status'], 'source'=>(string)$r['source'],
        'company'=>(string)($r['company'] ?? ''),
        'assignedCaller'=>(string)($r['assigned_caller_name'] ?: ($r['assigned_user_name'] ?? 'Unassigned')),
        'lastContacted'=>ms_from_datetime($r['last_contacted_at'] ?? null) ?? ms_from_datetime($r['updated_at'] ?? null) ?? round(microtime(true)*1000),
        'scheduledFollowUp'=>ms_from_datetime($r['next_followup_at'] ?? null),
        'sentiment'=>(string)($r['sentiment'] ?? 'Neutral'), 'tagIds'=>(string)($r['tag_ids'] ?? '')
    ];
}

function fetch_lead(mysqli $db, int $id): array {
    $stmt = $db->prepare('SELECT l.*,u.name assigned_user_name FROM leads l LEFT JOIN admin_users u ON u.id=l.assigned_to WHERE l.id=? LIMIT 1');
    $stmt->bind_param('i',$id); $stmt->execute(); $row=$stmt->get_result()->fetch_assoc(); $stmt->close();
    if (!$row) respond(['ok'=>false,'message'=>'Lead not found.'],404);
    return lead_payload($row);
}

$db = get_db();
$action = (string)($_GET['action'] ?? 'health');
$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';

if ($action === 'health') {
    respond(['ok'=>true,'service'=>'Quick CRM API','database'=>'connected','time'=>date(DATE_ATOM)]);
}

if ($action === 'login' && $method === 'POST') {
    $in = body_json();
    $email = strtolower(trim((string)($in['email'] ?? '')));
    $password = (string)($in['password'] ?? '');
    if (!filter_var($email,FILTER_VALIDATE_EMAIL) || $password==='') respond(['ok'=>false,'message'=>'Enter a valid email and password.'],422);
    $stmt=$db->prepare('SELECT id,name,email,password_hash,role,is_authorized FROM admin_users WHERE LOWER(email)=? LIMIT 1');
    $stmt->bind_param('s',$email); $stmt->execute(); $u=$stmt->get_result()->fetch_assoc(); $stmt->close();
    if (!$u || !password_verify($password,$u['password_hash'])) respond(['ok'=>false,'message'=>'Wrong email or password.'],401);
    if (!(int)$u['is_authorized']) respond(['ok'=>false,'message'=>'This account is disabled.'],403);
    $plain=bin2hex(random_bytes(32)); $hash=hash('sha256',$plain); $expiry=date('Y-m-d H:i:s',time()+60*60*24*30); $uid=(int)$u['id'];
    $db->query('DELETE FROM api_tokens WHERE expires_at<=NOW()');
    $ins=$db->prepare('INSERT INTO api_tokens(admin_id,token_hash,expires_at) VALUES(?,?,?)');
    $ins->bind_param('iss',$uid,$hash,$expiry); $ins->execute(); $ins->close();
    respond(['ok'=>true,'token'=>$plain,'user'=>['id'=>$uid,'name'=>$u['name'],'email'=>$u['email'],'role'=>$u['role']]]);
}

$user = require_api_user($db);

if ($action === 'sync' && $method === 'GET') {
    $leads=[];
    $res=$db->query('SELECT l.*,u.name assigned_user_name FROM leads l LEFT JOIN admin_users u ON u.id=l.assigned_to ORDER BY l.updated_at DESC,l.id DESC');
    while($r=$res->fetch_assoc()) $leads[]=lead_payload($r);
    $tele=[];
    $res=$db->query("SELECT u.id,u.name,u.email,u.phone,u.role,u.is_authorized,u.access_level,u.last_active_at,(SELECT COUNT(*) FROM leads l WHERE l.assigned_to=u.id) assigned_count FROM admin_users u ORDER BY u.id");
    while($r=$res->fetch_assoc()) $tele[]=[
        'id'=>(int)$r['id'],'name'=>$r['name'],'email'=>$r['email'],'phone'=>$r['phone']??'',
        'status'=>(int)$r['is_authorized']?'Available':'Offline','accessLevel'=>$r['access_level']??'Full Edit',
        'isAuthorized'=>(bool)$r['is_authorized'],'assignedCount'=>(int)$r['assigned_count'],
        'lastAssignedTimestamp'=>0,'lastActiveSession'=>ms_from_datetime($r['last_active_at'])??0
    ];
    respond(['ok'=>true,'leads'=>$leads,'telecallers'=>$tele]);
}

if ($action === 'lead' && $method === 'POST') {
    $in=body_json();
    $name=trim((string)($in['name']??'')); $phone=trim((string)($in['phone']??''));
    if ($name==='' || $phone==='') respond(['ok'=>false,'message'=>'Name and phone are required.'],422);
    $email=trim((string)($in['email']??'')); $status=(string)($in['status']??'fresh'); $source=(string)($in['source']??'manual');
    $company=trim((string)($in['company']??'')); $caller=trim((string)($in['assignedCaller']??'Unassigned'));
    $last=db_datetime_from_ms($in['lastContacted']??null); $follow=db_datetime_from_ms($in['scheduledFollowUp']??null);
    $sentiment=(string)($in['sentiment']??'Neutral'); $tags=(string)($in['tagIds']??'');
    $stmt=$db->prepare('INSERT INTO leads(source,name,phone,email,status,company,assigned_caller_name,last_contacted_at,next_followup_at,sentiment,tag_ids) VALUES(?,?,?,?,?,?,?,?,?,?,?)');
    $stmt->bind_param('sssssssssss',$source,$name,$phone,$email,$status,$company,$caller,$last,$follow,$sentiment,$tags);
    $stmt->execute(); $id=$stmt->insert_id; $stmt->close();
    respond(['ok'=>true,'lead'=>fetch_lead($db,$id)],201);
}

if ($action === 'lead' && $method === 'PUT') {
    $id=(int)($_GET['id']??0); if($id<=0) respond(['ok'=>false,'message'=>'Invalid lead ID.'],422);
    $in=body_json();
    $name=trim((string)($in['name']??'')); $phone=trim((string)($in['phone']??'')); $email=trim((string)($in['email']??''));
    $status=(string)($in['status']??'fresh'); $source=(string)($in['source']??'manual'); $company=trim((string)($in['company']??''));
    $caller=trim((string)($in['assignedCaller']??'Unassigned')); $last=db_datetime_from_ms($in['lastContacted']??null); $follow=db_datetime_from_ms($in['scheduledFollowUp']??null);
    $sentiment=(string)($in['sentiment']??'Neutral'); $tags=(string)($in['tagIds']??'');
    $stmt=$db->prepare('UPDATE leads SET source=?,name=?,phone=?,email=?,status=?,company=?,assigned_caller_name=?,last_contacted_at=?,next_followup_at=?,sentiment=?,tag_ids=? WHERE id=?');
    $stmt->bind_param('sssssssssssi',$source,$name,$phone,$email,$status,$company,$caller,$last,$follow,$sentiment,$tags,$id);
    $stmt->execute(); $stmt->close();
    respond(['ok'=>true,'lead'=>fetch_lead($db,$id)]);
}

if ($action === 'lead' && $method === 'DELETE') {
    $id=(int)($_GET['id']??0); if($id<=0) respond(['ok'=>false,'message'=>'Invalid lead ID.'],422);
    $stmt=$db->prepare('DELETE FROM leads WHERE id=?'); $stmt->bind_param('i',$id); $stmt->execute(); $stmt->close();
    respond(['ok'=>true]);
}

if ($action === 'activity' && $method === 'POST') {
    $in=body_json(); $lead=(int)($in['leadId']??0); $type=(string)($in['type']??'note'); $content=trim((string)($in['content']??'')); $uid=(int)$user['id'];
    $allowed=['note','status_change','whatsapp_sent','whatsapp_received','call','email','system']; if(!in_array($type,$allowed,true))$type='note';
    if($lead<=0 || $content==='') respond(['ok'=>false,'message'=>'Lead and activity content are required.'],422);
    $stmt=$db->prepare('INSERT INTO lead_activity(lead_id,type,content,created_by) VALUES(?,?,?,?)'); $stmt->bind_param('issi',$lead,$type,$content,$uid); $stmt->execute(); $stmt->close();
    respond(['ok'=>true],201);
}

respond(['ok'=>false,'message'=>'Unknown API action.'],404);
