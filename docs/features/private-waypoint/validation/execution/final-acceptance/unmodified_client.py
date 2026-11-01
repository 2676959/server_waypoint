from pathlib import Path
import sys,json,time,os
sys.path.insert(0,'/Volumes/ssd/fabric_mods_repo/server_waypoint/tools/live-game-test')
from runner import attach,wait_result
root=Path('/Volumes/ssd/sw-private-final-20261031');home=root/'proxy-check/WPProxy';connection_data=json.loads((home/'process.json').read_text());prep=Path('/Volumes/ssd/sw-private-task8-20261031/prepared');n=0
class Process:
 pid=connection_data['pid'];cwd=home/'game'
 def poll(self):
  try:os.kill(self.pid,0);return None
  except ProcessLookupError:return 1
 def check_cancelled(self):pass
p=Process()
def op(action):
 global n
 n+=1;f=home/f'extra8-{n:02}.result';attach(connection_data['java'],prep/'tools.jar',p,prep/'agent.jar',f'scenario|{f}|{action}|{root/"checks-5.jar"}',home/f'extra-{n:02}.attach.log');wait_result(p,f,180)
 (home/'extra-actions.log').open('a').write(str(n)+' '+action+'\n')
 j=Path(str(f)+'.json');return json.loads(j.read_text())if j.exists()else None
def text(x):return '\n'.join(m['text']for m in x['chat'])
def commands(x):return[c for m in x['chat'] for c in m['commands']]
def cmd(x):return op('cmd:'+x)
def console(command):
 global n
 n+=1;request=root/'rpc'/f'r7-{n:03}.request.json';request.write_text(json.dumps({'command':command}));until=time.time()+10
 while not request.with_suffix('.reply').exists():
  if time.time()>until:raise RuntimeError('console timeout')
  time.sleep(.1)
op('connect:127.0.0.1:25648');op('feature:local-marker');op('leave');op('connect:127.0.0.1:25648');op('feature:local-marker');op('feature:world-frame');op('leave')
print('PASS Local data and renderer survive two joins to server without Server Waypoint',flush=True)
