from integration import request,signup,uid,BASE
import urllib.request, urllib.error, json, base64, subprocess
A,B,C=signup(),signup(),signup();a=A['token'];b=B['token'];c=C['token']
def multipart(path,fields,files,token):
 boundary='test'+uid();parts=[]
 for k,v in fields.items():parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode())
 for field,name,content in files:parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{field}"; filename="{name}"\r\nContent-Type: application/octet-stream\r\n\r\n'.encode()+content+b'\r\n')
 parts.append(f'--{boundary}--\r\n'.encode()); req=urllib.request.Request(BASE+path,data=b''.join(parts),headers={'Content-Type':'multipart/form-data; boundary='+boundary,'Authorization':'Bearer '+token})
 try:
  with urllib.request.urlopen(req) as r:return r.status,json.load(r)
 except urllib.error.HTTPError as e:return e.code,json.load(e)
def fetch(id,token,range=None):
 headers={'Authorization':'Bearer '+token}
 if range: headers['Range']=range
 try:
  with urllib.request.urlopen(urllib.request.Request(BASE+'media/get.php?id='+id,headers=headers)) as r:return r.status,r.read()
 except urllib.error.HTTPError as e:return e.code,e.read()
png=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=')
id=uid();form={'id':id,'action_uuid':uid()}
s,r=multipart('media/upload.php',form,[('file','pixel.png',png)],a);assert s==200,(s,r)
assert multipart('media/upload.php',form,[('file','pixel.png',png)],a)==(s,r)
assert fetch(id,a)[1]==png
assert fetch(id,c)[0]==404
assert fetch(id,a,'bytes=0-3')==(206,png[:4])
assert multipart('media/upload.php',{'id':uid(),'action_uuid':uid()},[('file','bad.php',b'<?php echo 1;')],a)[0]==403
request('friends/action.php',{'other_id':B['user']['id'],'operation':'send','action_uuid':uid()},a)
request('friends/action.php',{'other_id':A['user']['id'],'operation':'accept','action_uuid':uid()},b)
story=uid();assert request('stories/create.php',{'id':story,'media_id':id,'audience':'close_friends','label':'Private','action_uuid':uid()},a)[0]==200
assert len(request('stories/feed.php',token=b)[1]['stories'])==0
assert request('close-friends/set.php',{'member_id':B['user']['id'],'enabled':True,'action_uuid':uid()},a)[0]==200
assert len(request('stories/feed.php',token=b)[1]['stories'])==1
assert fetch(id,b)[0]==200 and fetch(id,c)[0]==404
assert request('stories/view.php',{'id':story,'action_uuid':uid()},b)[0]==200
sql="UPDATE stories SET expires_at=DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 SECOND) WHERE id='"+story+"'"
subprocess.run(['docker','exec','kinnect-mysql-a3','mysql','-ukinnect','-plocalpass','kinnect','-e',sql],check=True,capture_output=True)
assert len(request('stories/feed.php',token=b)[1]['stories'])==0
assert fetch(id,b)[0]==404 and fetch(id,a)[0]==404
postform={'id':uid(),'body':'multipart','action_uuid':uid()};files=[('images[]','one.png',png),('images[]','two.png',png)]
s,r=multipart('posts/create.php',postform,files,a);assert s==200,(s,r)
assert len(r['post']['media_ids'])==2
assert multipart('posts/create.php',postform,files,a)==(s,r)
assert fetch(r['post']['media_ids'][0],b)[0]==200
assert fetch(r['post']['media_ids'][0],c)[0]==404
print('PASS: filesystem upload/replay, protected downloads/ranges, script rejection, audience, story views/expiry, multipart multi-image posts/replay')
