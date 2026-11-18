from integration import request,signup,uid
A,B,C=signup(),signup(),signup();a=A['token'];b=B['token'];peer=B['user']['id']
name='user_'+uid().replace('-','')[:15]
p={'name':'Test Person','username':name,'bio':'Profile setup','action_uuid':uid()}
assert request('profiles/update.php',p,a)[0]==200
assert request('profiles/update.php',{**p,'action_uuid':uid()},b)[0]==409
assert request('auth/me.php',token=a)[1]['user']['profile_setup_complete']==1
assert request('friends/action.php',{'other_id':peer,'operation':'send','action_uuid':uid()},a)[1]['state']=='requested'
assert request('search/users.php?filter=requests&q=',token=a)[1]['users'][0]['id']==peer
assert request('friends/action.php',{'other_id':A['user']['id'],'operation':'accept','action_uuid':uid()},b)[1]['state']=='friends'
assert len(request('friends/list.php',token=a)[1]['friends'])==1
assert len(request('search/users.php?filter=friends&q=',token=a)[1]['users'])==1
assert request('friends/action.php',{'other_id':peer,'operation':'remove','action_uuid':uid()},a)[1]['state']=='none'
assert len(request('friends/list.php',token=a)[1]['friends'])==0
print('PASS: profile setup, username uniqueness, send/accept/remove, friend lists and relationship search filters')
