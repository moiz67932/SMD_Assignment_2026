from media_stories import request, multipart, png, uid, A, B, C, a, b, c, fetch
media=uid();assert multipart('media/upload.php',{'id':media,'action_uuid':uid()},[('file','avatar.png',png)],a)[0]==200
p={'name':'Profile Test','username':'user_'+uid().replace('-','')[:12],'bio':'Ready','photo_media_id':media,'cover_media_id':media,'action_uuid':uid()}
assert request('profiles/update.php',p,a)[0]==200
assert request('profiles/get.php?id='+A['user']['id'],token=b)[1]['user']['photo_media_id']==media
assert request('profiles/update.php',{**p,'username':'user_'+uid().replace('-','')[:12],'action_uuid':uid()},b)[0]==403
post=uid();assert request('posts/create.php',{'id':post,'body':'image comments','action_uuid':uid()},a)[0]==200
comment=uid();assert request('comments/create.php',{'id':comment,'post_id':post,'media_id':media,'action_uuid':uid()},a)[0]==200
assert request('comments/list.php?post_id='+post,token=b)[1]['comments'][0]['media_id']==media
assert request('comments/delete.php',{'id':comment,'action_uuid':uid()},b)[0]==403
assert request('comments/delete.php',{'id':comment,'action_uuid':uid()},a)[0]==200
story=uid();assert request('stories/create.php',{'id':story,'media_id':media,'action_uuid':uid()},a)[0]==200
assert request('stories/view.php',{'id':story,'action_uuid':uid()},b)[0]==200
assert len(request('stories/viewers.php?id='+story,token=a)[1]['viewers'])==1
assert request('stories/viewers.php?id='+story,token=b)[0]==403
assert request('stories/react.php',{'id':story,'reaction':'Love','action_uuid':uid()},b)[0]==200
assert request('stories/react.php',{'id':story,'reaction':'Love','action_uuid':uid()},c)[0]==403
assert request('stories/delete.php',{'id':story,'action_uuid':uid()},b)[0]==403
assert request('stories/delete.php',{'id':story,'action_uuid':uid()},a)[0]==200
assert request('stories/react.php',{'id':story,'reaction':'Love','action_uuid':uid()},b)[0]==403
print('PASS: profile media ownership, image comments/deletion, story viewers/react/delete authorization')
