from pathlib import Path
import subprocess,time,json,shutil,re,os,uuid,hashlib
ROOT=Path('/Volumes/ssd/sw-private-final-20261031');REPO=Path('/Volumes/ssd/fabric_mods_repo/server_waypoint')
def offline(name):
 b=bytearray(hashlib.md5(('OfflinePlayer:'+name).encode()).digest());b[6]=(b[6]&15)|48;b[8]=(b[8]&63)|128;return str(uuid.UUID(bytes=bytes(b)))
def run(kind,port):
 home=ROOT/kind
 if kind=='fabric':shutil.copy(REPO/'mods/versions/1.21.3-fabric/build/libs/server_waypoint-4.0.1-fabric-mc1.21.3-1.21.4.jar',home/'mods/server-waypoint.jar')
 (home/'ops.json').write_text(json.dumps([{'uuid':offline('ScriptAlice3'),'name':'ScriptAlice3','level':2,'bypassesPlayerLimit':False}]))
 stream=(home/'script-server.log').open('w');server=subprocess.Popen(['/opt/homebrew/opt/openjdk@25/bin/java','-Xms512M','-Xmx1024M','-jar','server.jar','--nogui'],cwd=home,stdin=subprocess.PIPE,stdout=stream,stderr=subprocess.STDOUT,text=True);children=[]
 def console(s):server.stdin.write(s+'\n');server.stdin.flush()
 try:
  until=time.time()+120
  while 'Done (' not in (home/'script-server.log').read_text():
   if server.poll()!=None or time.time()>until:raise RuntimeError('startup '+kind)
   time.sleep(.3)
  scripts={'ScriptAlice3':'''wait 3
send /wp private add 12 80 -30 Homes Base
wait 2
send /wp private edit waypoint minecraft:overworld Homes Base set description AliceSecret
wait 2
send /wp private tp minecraft:overworld Homes Base
wait 2
send /wp private navigate minecraft:overworld Homes Base actionbar
wait 2
send /wp private navigate disable
wait 2
send /wp private remove minecraft:overworld Homes Base
wait 2
exit
''','ScriptBob3':'''wait 4
send /wp private details waypoint minecraft:overworld Homes Base
wait 2
send /wp private add -44 80 22 Homes Base
wait 2
send /wp private details waypoint minecraft:overworld Homes Base
wait 2
send /wp private tp minecraft:overworld Homes Base
wait 3
send /wp private list all
wait 6
exit
'''}
  template=(ROOT/'folia/alice/MinecraftClient.ini').read_text()
  template=re.sub(r'\[ChatBot.ScriptScheduler\].*?(?=\n\[ChatBot\.)','',template,flags=re.S)
  for name,script in scripts.items():
   directory=home/name;directory.mkdir(exist_ok=True);(directory/'scenario.txt').write_text(re.sub(r'(?m)^wait (\d+)$',lambda m:'wait '+str(int(m[1])*20),script))
   config=template+'''\n[ChatBot.ScriptScheduler]
Enabled = true
[[ChatBot.ScriptScheduler.TaskList]]
Task_Name = "Private acceptance"
Trigger_On_First_Login = false
Trigger_On_Login = true
Trigger_On_Times = { Enable = false, Times = [] }
Trigger_On_Interval = { Enable = false, MinTime = 1.0, MaxTime = 1.0 }
Action = "script scenario.txt"
'''
   (directory/'MinecraftClient.ini').write_text(config);log=(directory/'client.log').open('w')
   p=subprocess.Popen(['/Volumes/ssd/minecraft_console_client/MinecraftClient',name,'-',f'127.0.0.1:{port}','--EnableSentry=false','--Language=en_us'],cwd=directory,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True,env={**os.environ,'DOTNET_ReadyToRun':'0'});children.append(p)
  for i in range(35):
   time.sleep(1)
   if i in [10,12,14]:console('acceptance position ScriptAlice3' if kind=='folia' else 'data get entity ScriptAlice3 Pos')
   if i==12:console('wp private list');console('execute as ScriptBob3 run wp private list')
   if all(p.poll()!=None for p in children):break
  for p in children:p.wait(timeout=20)
  a=re.sub(r'\x1b\[[0-?]*[ -/]*[@-~]','',(home/'ScriptAlice3/client.log').read_text());b=re.sub(r'\x1b\[[0-?]*[ -/]*[@-~]','',(home/'ScriptBob3/client.log').read_text())
  for expected in ['Added','Updated','Teleported','navigating','Removed']:assert expected.casefold() in a.casefold(),expected+' '+a[-1500:]
  assert 'AliceSecret' not in b and 'Teleported' not in b,b[-1500:]
  assert '-44' in b,b[-1500:]
  log=(home/'script-server.log').read_text();assert '12.5' in log and '-29.5' in log,'arrival missing'
  print('PASS',kind,flush=True);return {'platform':kind,'status':'PASS'}
 finally:
  for p in children:
   if p.poll()==None:
    p.stdin.write('/quit\n');p.stdin.flush()
    try:p.wait(timeout=5)
    except subprocess.TimeoutExpired:p.terminate();p.wait(timeout=5)
  if server.poll()==None:
   console('stop')
   try:server.wait(timeout=45)
   except subprocess.TimeoutExpired:server.terminate();server.wait(timeout=10)
results=[]
for kind,port in [('folia',25649)]:
 try:results.append(run(kind,port))
 except Exception as e:print('FAIL',kind,repr(e),flush=True);results.append({'platform':kind,'status':'FAIL','error':str(e)})
(ROOT/'platform-script-results.json').write_text(json.dumps(results,indent=2)+'\n')
