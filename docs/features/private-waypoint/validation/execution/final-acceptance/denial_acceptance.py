from pathlib import Path
import sys,json,time,os
sys.path.insert(0,'/Volumes/ssd/fabric_mods_repo/server_waypoint/tools/live-game-test')
from runner import attach,wait_result
root=Path('/Volumes/ssd/sw-private-final-20261031');home=root/'proxy-check/WPProxy';data=json.loads((home/'process.json').read_text());prep=Path('/Volumes/ssd/sw-private-task8-20261031/prepared');n=0
class Process:
 pid=data['pid'];cwd=home/'game'
 def poll(self):
  try:os.kill(self.pid,0);return None
  except ProcessLookupError:return 1
 def check_cancelled(self):pass
p=Process()
def op(action):
 global n
 n+=1;f=home/f'extra5-{n:02}.result';attach(data['java'],prep/'tools.jar',p,prep/'agent.jar',f'scenario|{f}|{action}|{root/"checks-4.jar"}',home/f'extra-{n:02}.attach.log');wait_result(p,f,180)
 (home/'extra-actions.log').open('a').write(str(n)+' '+action+'\n')
 j=Path(str(f)+'.json');return json.loads(j.read_text())if j.exists()else None
def text(x):return '\n'.join(m['text']for m in x['chat'])
def commands(x):return[c for m in x['chat'] for c in m['commands']]
def cmd(x):return op('cmd:'+x)
def console(command):
 global n
 n+=1;request=root/'rpc'/f'{n:03}.request.json';request.write_text(json.dumps({'command':command}));until=time.time()+10
 while not request.with_suffix('.reply').exists():
  if time.time()>until:raise RuntimeError('console timeout')
  time.sleep(.1)
while not (root/'rpc/ready').exists():time.sleep(.1)
op('connect:127.0.0.1:25648');op('feature:ready');op('feature:seed-local');cmd('wp private add 12 80 -30 Homes Base');cmd('wp add 100 80 100 Homes Base')
console('acceptance permission WPProxy private.add false');op('feature:copy-denied');console('acceptance permission WPProxy private.add true')
config=root/'paper/plugins/ServerWaypoint/config.json';data=json.loads(config.read_text());data['privateWaypoints']['maxWaypointsPerPlayer']=1;config.write_text(json.dumps(data));console('wp reload');op('feature:copy-quota')
cmd('wp private navigate minecraft:overworld Homes Base bossbar');assert op('feature:bossbar-count')['count']==1
cmd('wp navigate minecraft:overworld Homes Base actionbar');assert op('feature:bossbar-count')['count']==0
x=cmd('wp navigate');assert 'Base' in text(x);assert any('/wp navigate ' in command for command in commands(x)) and not any('/wp private' in command for command in commands(x)),commands(x)
print('PASS copy permission/quota denial creates no entry; Shared retarget removes Private bossbar and retains Shared action links',flush=True)
op('leave');(root/'rpc/stop').write_text('stop')
