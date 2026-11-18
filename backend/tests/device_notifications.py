from integration import request,signup,uid
A,B=signup(),signup();a=A['token'];b=B['token'];key=uid()
assert request('devices/register.php',{'device_key':key,'fcm_token':'test_token_'+uid(),'action_uuid':uid()},b)[0]==200
p={'other_id':B['user']['id'],'operation':'send','action_uuid':uid()}
assert request('friends/action.php',p,a)[0]==200
assert request('friends/action.php',p,a)[0]==200
rows=request('notifications/list.php',token=b)[1]['notifications'];assert len(rows)==1 and rows[0]['type']=='friend_request'
assert request('notifications/read.php',{'id':rows[0]['id'],'action_uuid':uid()},a)[0]==200
assert request('notifications/list.php',token=b)[1]['notifications'][0]['read_at'] is None
assert request('notifications/read.php',{'id':rows[0]['id'],'action_uuid':uid()},b)[0]==200
assert request('notifications/list.php',token=b)[1]['notifications'][0]['read_at'] is not None
assert request('devices/unregister.php',{'device_key':key,'action_uuid':uid()},b)[0]==200
print('PASS: friend-request notification replay, recipient-only read state and device unregister')
