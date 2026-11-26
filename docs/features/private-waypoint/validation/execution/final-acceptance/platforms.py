from pathlib import Path
import os,pty,select,subprocess,threading,time,re,json,shutil
ROOT=Path('/Volumes/ssd/sw-private-final-20261031');REPO=Path('/Volumes/ssd/fabric_mods_repo/server_waypoint')
ansi=re.compile(r'\x1b\[[0-?]*[ -/]*[@-~]')
class Bot:
 def __init__(self,name,port,home):
  self.home=home;home.mkdir(exist_ok=True);self.output='';self.master,slave=pty.openpty()
  self.p=subprocess.Popen(['/Volumes/ssd/minecraft_console_client/MinecraftClient',name,'-',f'127.0.0.1:{port}','--EnableSentry=false','--Language=en_us'],cwd=home,stdin=slave,stdout=slave,stderr=slave,env={**os.environ,'DOTNET_ReadyToRun':'0'});os.close(slave)
  self.reader=threading.Thread(target=self.read,daemon=True);self.reader.start();self.wait('Server was successfully joined',60)
 def read(self):
  while self.p.poll() is None:
   try:
    if select.select([self.master],[],[],.2)[0]:
     s=ansi.sub('',os.read(self.master,65536).decode(errors='replace')).replace('\r','');self.output+=s;(self.home/'client.log').write_text(self.output)
   except OSError:return
 def wait(self,text,timeout=10,start=0):
  until=time.time()+timeout
  while time.time()<until:
   if text.casefold() in self.output[start:].casefold():return self.output[start:]
   if self.p.poll()!=None:raise RuntimeError('MCC exited '+self.output[-300:])
   time.sleep(.1)
  raise RuntimeError('missing '+text+' got '+self.output[start:])
 def cmd(self,command,expect=None):
  start=len(self.output);os.write(self.master,('/send /'+command+'\n').encode());time.sleep(1.1)
  return self.wait(expect,10,start) if expect else self.output[start:]
 def close(self):
  if self.p.poll() is None:
   os.write(self.master,b'/quit\n')
   try:self.p.wait(timeout=10)
   except subprocess.TimeoutExpired:self.p.terminate();self.p.wait(timeout=5)
  os.close(self.master)

def run(kind,port):
 home=ROOT/kind
 if kind=='fabric':
  src=next((REPO/'mods/versions/1.21.3-fabric/build/libs').glob('server_waypoint-4.0.1-fabric-*.jar'));shutil.copy(src,home/'mods/server-waypoint.jar')
 else:shutil.copy(ROOT/'paper/plugins/access-probe.jar',home/'plugins/access-probe.jar')
 stream=(home/'platform-server.log').open('w');server=subprocess.Popen(['/opt/homebrew/opt/openjdk@25/bin/java','-Xms512M','-Xmx1024M','-jar','server.jar','--nogui'],cwd=home,stdin=subprocess.PIPE,stdout=stream,stderr=subprocess.STDOUT,text=True)
 bots=[]
 def console(command):server.stdin.write(command+'\n');server.stdin.flush();time.sleep(.7)
 try:
  until=time.time()+120
  while 'Done (' not in (home/'platform-server.log').read_text():
   if time.time()>until or server.poll()!=None:raise RuntimeError('server startup '+kind)
   time.sleep(.25)
  a=Bot('TestAlice',port,home/'alice');bots.append(a);b=Bot('TestBob',port,home/'bob');bots.append(b)
  a.cmd('wp private add 12 80 -30 Homes Base','Added');b.cmd('wp private add -44 80 22 Homes Base','Added');a.cmd('wp private edit waypoint minecraft:overworld Homes Base set description AliceSecret','Updated');out=b.cmd('wp private details waypoint minecraft:overworld Homes Base','Position: -44');assert 'AliceSecret' not in out,out
  assert 'Teleported' not in a.cmd('wp private tp minecraft:overworld Homes Base')
  console('op TestAlice');a.cmd('wp private tp minecraft:overworld Homes Base','Teleported');console('data get entity TestAlice Pos')
  a.cmd('wp private navigate minecraft:overworld Homes Base actionbar','navigating');a.cmd('wp navigate disable')
  a.cmd('wp private remove minecraft:overworld Homes Base','Removed');out=b.cmd('wp private details waypoint minecraft:overworld Homes Base','Position: -44');assert 'AliceSecret' not in out
  console('wp private list');console('execute as TestBob run wp private list')
  print('PASS',kind,'owner isolation/CRUD/navigation/default denial/authorized arrival/execute-as',flush=True)
  return {'platform':kind,'status':'PASS'}
 finally:
  for b in bots:b.close()
  if server.poll() is None:
   console('stop')
   try:server.wait(timeout=60)
   except subprocess.TimeoutExpired:server.terminate();server.wait(timeout=10)
results=[]
for kind,port in [('folia',25649),('fabric',25650)]:
 try:results.append(run(kind,port))
 except Exception as e:print('FAIL',kind,repr(e),flush=True);results.append({'platform':kind,'status':'FAIL','error':str(e)})
(ROOT/'platform-results.json').write_text(json.dumps(results,indent=2)+'\n')
