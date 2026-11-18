"""Checks server time enforcement against an isolated local fixture database."""
from integration import request, signup, uid
import subprocess
u,v=signup(),signup(); t=u['token']
s,c=request('chats/ensure.php',{'other_id':v['user']['id'],'action_uuid':uid()},t); assert s==200
m=uid();assert request('messages/send.php',{'id':m,'chat_id':c['chat_id'],'body':'old message','action_uuid':uid()},t)[0]==200
sql="UPDATE messages SET created_at=DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 6 MINUTE) WHERE id='"+m+"'"
subprocess.run(['docker','exec','kinnect-mysql-a3','mysql','-ukinnect','-plocalpass','kinnect','-e',sql],check=True,capture_output=True)
assert request('messages/edit.php',{'id':m,'body':'forged edit','action_uuid':uid()},t)[0]==403
assert request('messages/delete.php',{'id':m,'action_uuid':uid()},t)[0]==403
print('PASS: server rejects edit and delete after five minutes')
