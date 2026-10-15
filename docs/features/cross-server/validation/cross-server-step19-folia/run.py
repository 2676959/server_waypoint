import subprocess,threading,sys,time
from pathlib import Path
root=Path(__file__).parent
procs={}
def launch(name,cmd,cwd):
 p=subprocess.Popen(cmd,cwd=cwd,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1)
 procs[name]=p
 log=(root/(name+'-live.log')).open('a')
 def pump():
  for line in p.stdout:
   log.write(line);log.flush()
   if name=='m' or any(s in line for s in ['Done (','Cross-server','coordinator','ERROR','WARN','CodexStep19','STEP16','STEP19','ServerWaypoint']):
    print(name+': '+line.rstrip(),flush=True)
  log.close()
 threading.Thread(target=pump,daemon=True).start()
java21='/opt/homebrew/opt/openjdk@21/bin/java'
java25='/Volumes/ssd/gradle_home/jdks/eclipse_adoptium-25-aarch64-os_x.2/jdk-25.0.3+9/Contents/Home/bin/java'
for name in ['a','b']:launch(name,[java21,'-Xms128M','-Xmx1024M','-jar','server.jar','nogui'],root/name)
launch('proxy',[java25,'-Xms128M','-Xmx512M','-jar','velocity.jar'],root/'proxy')
try:
 for line in sys.stdin:
  line=line.rstrip()
  if line=='mcc': launch('m',['/Volumes/ssd/minecraft_console_client/MinecraftClient','CodexStep19','-','127.0.0.1:25980'],root/'mcc');continue
  if line=='quit': break
  name,_,command=line.partition(' ')
  p=procs.get(name)
  if p and p.poll() is None: p.stdin.write(command+'\n');p.stdin.flush()
finally:
 for name,p in procs.items():
  if p.poll() is None:
   try:p.stdin.write(('/quit' if name=='m' else 'shutdown' if name=='proxy' else 'stop')+'\n');p.stdin.flush()
   except BrokenPipeError:pass
 for name,p in procs.items():
  try:p.wait(timeout=25)
  except subprocess.TimeoutExpired:p.terminate();p.wait(timeout=10)
 print('All disposable processes stopped',flush=True)
