from integration import request,signup,uid
A,B=signup(),signup();a=A['token'];b=B['token'];id=uid()
p={'id':id,'body':'SQL feed','action_uuid':uid()}
assert request('posts/create.php',p,a)[0]==200
assert request('posts/create.php',p,a)[0]==200
assert len(request('posts/feed.php',token=a)[1]['posts'])==1
assert len(request('posts/feed.php',token=b)[1]['posts'])==0
assert request('posts/react.php',{'post_id':id,'reaction':'Love','action_uuid':uid()},b)[0]==403
request('friends/action.php',{'other_id':B['user']['id'],'operation':'send','action_uuid':uid()},a)
request('friends/action.php',{'other_id':A['user']['id'],'operation':'accept','action_uuid':uid()},b)
assert len(request('posts/feed.php',token=b)[1]['posts'])==1
for r in ['Like','Love','Laugh','Wow','Sad','Angry',None]:
 assert request('posts/react.php',{'post_id':id,'reaction':r,'action_uuid':uid()},b)[0]==200
print('PASS: text post replay, own/friend feed visibility, outsider denial and all six reaction mutations/removal')
