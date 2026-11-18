from integration import request,signup,uid
u=signup(); t=u['token']; post=uid(); comment=uid()
assert request('posts/create.php',{'id':post,'body':'comments fixture','action_uuid':uid()},t)[0]==200
p={'id':comment,'post_id':post,'body':'parent','action_uuid':uid()}
assert request('comments/create.php',p,t)[0]==200
assert request('comments/create.php',p,t)[0]==200
assert request('comments/create.php',{'id':uid(),'post_id':post,'body':'reply','parent_id':comment,'action_uuid':uid()},t)[0]==200
assert request('comments/like.php',{'comment_id':comment,'liked':True,'action_uuid':uid()},t)[0]==200
s,r=request('comments/list.php?post_id='+post,token=t);assert s==200 and len(r['comments'])==2 and r['comments'][0]['likes']==1
outsider=signup()
assert request('comments/list.php?post_id='+post,token=outsider['token'])[0]==403
assert request('comments/like.php',{'comment_id':comment,'liked':False,'action_uuid':uid()},t)[0]==200
print('PASS: text comments, replies, replay, likes/removal and outsider denial')
