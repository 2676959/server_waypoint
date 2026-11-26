from pathlib import Path
import subprocess, time, shutil, json
root=Path('/Volumes/ssd/sw-private-task8-20261031')
home=root/'multiplayer'
out=root/'multiplayer-results-3'
server=None
runner=None

def start(label):
 global server
 log=(root/(label+'-server.log')).open('w')
 server=subprocess.Popen(['/opt/homebrew/opt/openjdk@25/bin/java','-Xms512M','-Xmx1536M','-jar','server.jar','--nogui'],cwd=home,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True)
 deadline=time.time()+120
 while time.time()<deadline:
  if server.poll() is not None: raise RuntimeError(label+' server exited')
  if 'Done (' in (root/(label+'-server.log')).read_text(): return
  time.sleep(.25)
 raise RuntimeError(label+' server startup timeout')

def stop():
 global server
 if server and server.poll() is None:
  server.stdin.write('stop\n');server.stdin.flush()
  try: server.wait(timeout=45)
  except subprocess.TimeoutExpired: server.terminate();server.wait(timeout=15)

try:
 for jar in (home/'plugins').glob('*.jar'): jar.unlink()
 props=home/'server.properties';props.write_text(props.read_text().replace('level-name=incompatible','level-name=unsupported'))
 start('unsupported')
 with (root/'multiplayer-run.log').open('w') as log:
  runner=subprocess.Popen(['python3',str(root/'multiplayer-tool/run.py'),'run','--prepared',str(root/'multiplayer-prepared-3'),'--output',str(out),'--heap','1024'],stdout=log,stderr=subprocess.STDOUT)
  deadline=time.time()+300
  while not (out/'restart-request').exists():
   if runner.poll() is not None: raise RuntimeError('runner ended before restart request')
   if time.time()>deadline: raise RuntimeError('restart request timeout')
   time.sleep(.25)
  stop()
  shutil.copy('/Volumes/ssd/fabric_mods_repo/server_waypoint/paper/versions/1.21.11-paper/build/libs/server_waypoint-4.0.0-paper-mc1.21.11-26.1.2.jar',home/'plugins/old-server-waypoint.jar')
  props=home/'server.properties';props.write_text(props.read_text().replace('level-name=unsupported','level-name=incompatible'))
  start('incompatible')
  (out/'restart-ready').write_text('ready\n')
  print('runner exit',runner.wait(timeout=300),flush=True)
finally:
 if runner and runner.poll() is None: runner.terminate();runner.wait(timeout=30)
 stop()
