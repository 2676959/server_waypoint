from pathlib import Path
import subprocess,time,json,shutil
root=Path('/Volumes/ssd/sw-private-final-20261031');home=root/'paper';control=root/'rpc';control.mkdir(exist_ok=True)
log=(home/'followup-server.log').open('w');p=subprocess.Popen(['/opt/homebrew/opt/openjdk@25/bin/java','-Xms512M','-Xmx1024M','-jar','server.jar','--nogui'],cwd=home,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True)
try:
 until=time.time()+120
 while 'Done (' not in (home/'followup-server.log').read_text():
  if p.poll()!=None or time.time()>until:raise RuntimeError('server startup')
  time.sleep(.2)
 (control/'ready').write_text('ready')
 while not(control/'stop').exists():
  for request in control.glob('*.request.json'):
   data=json.loads(request.read_text());p.stdin.write(data['command']+'\n');p.stdin.flush();time.sleep(.8);request.rename(request.with_suffix('.done'));request.with_suffix('.reply').write_text('sent')
  time.sleep(.1)
finally:
 if p.poll()==None:p.stdin.write('stop\n');p.stdin.flush();p.wait(timeout=60)
