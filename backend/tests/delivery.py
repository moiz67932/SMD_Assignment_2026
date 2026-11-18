from integration import request,signup,uid
A,B=signup(),signup();a=A['token'];b=B['token']
assert request('devices/register.php',{'device_key':uid(),'fcm_token':'test_token_'+uid(),'action_uuid':uid()},b)[0]==200
assert request('presence/heartbeat.php',{},a)[0]==200
assert request('presence/get.php?id='+A['user']['id'],token=b)[1]['presence']['online']==1
chat=request('chats/ensure.php',{'other_id':B['user']['id'],'action_uuid':uid()},a)[1]['chat_id']
p={'id':uid(),'chat_id':chat,'body':'notified','action_uuid':uid()}
assert request('messages/send.php',p,a)[0]==200
assert request('messages/send.php',p,a)[0]==200
rows=request('notifications/list.php',token=b)[1]['notifications'];assert len(rows)==1 and rows[0]['type']=='message'
screenshot={'chat_id':chat,'action_uuid':uid()}
s,r=request('chats/screenshot.php',screenshot,a);assert s==200 and 'took a screenshot' in r['body']
assert request('chats/screenshot.php',screenshot,a)==(s,r)
assert len(request('notifications/list.php',token=b)[1]['notifications'])==2
assert len(request('messages/list.php?chat_id='+chat,token=b)[1]['messages'])==2
assert request('auth/logout.php',{},a)[0]==200
assert request('presence/get.php?id='+A['user']['id'],token=b)[1]['presence']['online']==0
print('PASS: device registration, presence/logout, message notification deduplication and atomic screenshot/system-message replay')
