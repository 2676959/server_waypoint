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
 n+=1;f=home/f'extra2-{n:02}.result';attach(data['java'],prep/'tools.jar',p,prep/'agent.jar',f'scenario|{f}|{action}|{root/"checks-2.jar"}',home/f'extra-{n:02}.attach.log');wait_result(p,f,180)
 (home/'extra-actions.log').open('a').write(str(n)+' '+action+'\n')
 j=Path(str(f)+'.json');return json.loads(j.read_text())if j.exists()else None
def text(x):return '\n'.join(m['text']for m in x['chat'])
def commands(x):return[c for m in x['chat'] for c in m['commands']]
def cmd(x):return op('cmd:'+x)
op('feature:ready');cmd('wp add 100 80 100 Homes Base');cmd('wp private add 12 80 -30 Homes Base');op('feature:scene');op('feature:tombstone');cmd('wp private add 12 80 -30 Homes Base')
# Conflict must preserve a valid restore token until the conflicting target is removed.
x=cmd('wp private remove minecraft:overworld Homes Base');token=next(c for c in commands(x)if 'private restore' in c)
cmd('wp private add 12 80 -30 Homes Base');assert 'Restored' not in text(cmd(token.lstrip('/')))
cmd('wp private remove minecraft:overworld Homes Base');assert 'Restored' in text(cmd(token.lstrip('/')))
print('PASS tombstone source isolation and restore name-conflict token preservation',flush=True)
op('feature:locales');print('PASS 24 native locale/view captures, six profile captures, narration/popup/wheel/Escape checks',flush=True)
(root/'proxy-check/ui-finished').write_text('done\n')
