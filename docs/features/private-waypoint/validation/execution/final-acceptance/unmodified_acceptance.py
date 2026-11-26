from pathlib import Path
import time,subprocess,shutil,os,signal
root=Path('/Volumes/ssd/sw-private-final-20261031');home=root/'paper';control=root/'rpc'
# Wait for the preceding owned fixture to complete its graceful stop.
until=time.time()+60
while subprocess.run(['lsof','-t','-iTCP:25648','-sTCP:LISTEN'],capture_output=True,text=True).stdout.strip():
 if time.time()>until:raise RuntimeError('previous server did not stop')
 time.sleep(.2)
source=root/'proxy-check/WPProxy/game/screenshots/visible-collection-markers.png'
if source.exists():shutil.copy(source,root/'visible-collection-markers.png')
plugin=home/'plugins/server-waypoint.jar';disabled=plugin.with_suffix('.jar.disabled');plugin.rename(disabled)
for p in control.glob('*'):p.unlink()
controller=None
try:
 log=(root/'unmodified-server-driver.log').open('w');controller=subprocess.Popen(['python3',str(root/'rpc_server.py')],stdout=log,stderr=subprocess.STDOUT)
 until=time.time()+90
 while not(control/'ready').exists():
  if controller.poll()!=None or time.time()>until:raise RuntimeError('unmodified startup')
  time.sleep(.2)
 source=(root/'world_frame.py').read_text();source=source[:source.index("op('feature:world-frame')")];source=source.replace("f'extra7-{n:02}.result'","f'extra8-{n:02}.result'")
 source+='''op('connect:127.0.0.1:25648');op('feature:local-marker');op('leave');op('connect:127.0.0.1:25648');op('feature:local-marker');op('feature:world-frame');op('leave')
print('PASS Local data and renderer survive two joins to server without Server Waypoint',flush=True)
'''
 (root/'unmodified_client.py').write_text(source)
 with (root/'unmodified-client.log').open('w') as output:r=subprocess.run(['python3',str(root/'unmodified_client.py')],stdout=output,stderr=subprocess.STDOUT,timeout=120)
 if r.returncode:raise RuntimeError('unmodified client assertions failed')
 print('PASS unmodified server Local persistence/markers',flush=True)
finally:
 (control/'stop').write_text('stop')
 if controller:
  try:controller.wait(timeout=60)
  except subprocess.TimeoutExpired:controller.terminate();controller.wait(timeout=10)
 if disabled.exists():disabled.rename(plugin)
 # Resume the owned proxy driver so it quits its client and servers.
 os.kill(int((root/'held-controller.pid').read_text()),signal.SIGCONT)
