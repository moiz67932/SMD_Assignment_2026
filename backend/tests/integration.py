"""Run against isolated MySQL fixture: python3 backend/tests/integration.py."""
import json, urllib.request, urllib.error, uuid
BASE='http://127.0.0.1:8088/'
def request(path,data=None,token=None):
    headers={'Content-Type':'application/json'}
    if token: headers['Authorization']='Bearer '+token
    req=urllib.request.Request(BASE+path,data=json.dumps(data).encode() if data is not None else None,headers=headers)
    try:
        with urllib.request.urlopen(req) as r: return r.status,json.load(r)
    except urllib.error.HTTPError as e: return e.code,json.load(e)
def uid(): return str(uuid.uuid4())
def signup():
    email=uid()+'@example.test';s,u=request('auth/signup.php',{'email':email,'password':'Test-password-123!','name':'API Test'})
    assert s==201,(s,u)
    s,l=request('auth/login.php',{'email':email,'password':'Test-password-123!'})
    assert s==200 and l['user']['id']==u['user']['id']
    assert 'password_hash' not in l['user']
    assert request('auth/login.php',{'email':email,'password':'bad'})[0]==401
    return u
A,B,C=signup(),signup(),signup(); token=A['token']
assert request('auth/me.php',token=token)[0]==200
assert request('auth/me.php')[0]==401
s,c=request('chats/ensure.php',{'other_id':B['user']['id'],'action_uuid':uid()},token);assert s==200,(s,c)
chat=c['chat_id']; data={'id':uid(),'chat_id':chat,'body':'Durable replay test','action_uuid':uid()}
s,first=request('messages/send.php',data,token);assert s==200,(s,first)
assert request('messages/send.php',data,token)==(s,first)
assert request('messages/send.php',{**data,'body':'Different payload'},token)[0]==409
assert request('messages/list.php?chat_id='+chat,token=C['token'])[0]==403
s,rows=request('messages/list.php?chat_id='+chat,token=B['token']);assert s==200 and len(rows['messages'])==1
assert request('messages/edit.php',{'id':data['id'],'body':'Forged','action_uuid':uid()},B['token'])[0]==403
assert request('messages/edit.php',{'id':data['id'],'body':'Edited','action_uuid':uid()},token)[0]==200
assert request('messages/delete.php',{'id':data['id'],'action_uuid':uid()},token)[0]==200
assert request('auth/logout.php',{},token)[0]==200
assert request('auth/me.php',token=token)[0]==401
print('PASS: signup, login, credential rejection, token lookup/revocation, chat membership, send replay, UUID conflict, sender authorization, edit, delete')
