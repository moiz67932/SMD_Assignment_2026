"""Checks server time enforcement against an isolated local fixture database."""
from integration import request, signup, uid
import subprocess
u,v=signup(),signup(); t=u['token']

def run_sql(sql):
    """Runs SQL on the test database: XAMPP's MariaDB (backend/.env) or the old Docker fixture."""
    import os, shutil
    env_file = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), '.env')
    if os.path.exists('/opt/lampp/bin/mysql') and os.path.exists(env_file):
        cfg = dict(line.strip().split('=', 1) for line in open(env_file) if '=' in line)
        subprocess.run(['/opt/lampp/bin/mysql', '-h', cfg.get('DB_HOST', '127.0.0.1'), '-P', cfg.get('DB_PORT', '3306'), '-u' + cfg['DB_USER'],
                        '-p' + cfg['DB_PASSWORD'], cfg.get('DB_NAME', 'kinnect'), '-e', sql], check=True, capture_output=True)
    else:
        subprocess.run(['docker','exec','kinnect-mysql-a3','mysql','-ukinnect','-plocalpass','kinnect','-e',sql],check=True,capture_output=True)

s,c=request('chats/ensure.php',{'other_id':v['user']['id'],'action_uuid':uid()},t); assert s==200
m=uid();assert request('messages/send.php',{'id':m,'chat_id':c['chat_id'],'body':'old message','action_uuid':uid()},t)[0]==200
sql="UPDATE messages SET created_at=DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 6 MINUTE) WHERE id='"+m+"'"
run_sql(sql)
assert request('messages/edit.php',{'id':m,'body':'forged edit','action_uuid':uid()},t)[0]==403
assert request('messages/delete.php',{'id':m,'action_uuid':uid()},t)[0]==403
print('PASS: server rejects edit and delete after five minutes')
