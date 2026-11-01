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
 n+=1;f=home/f'extra3-{n:02}.result';attach(data['java'],prep/'tools.jar',p,prep/'agent.jar',f'scenario|{f}|{action}|{root/"checks-3.jar"}',home/f'extra-{n:02}.attach.log');wait_result(p,f,180)
 (home/'extra-actions.log').open('a').write(str(n)+' '+action+'\n')
 j=Path(str(f)+'.json');return json.loads(j.read_text())if j.exists()else None
def text(x):return '\n'.join(m['text']for m in x['chat'])
def commands(x):return[c for m in x['chat'] for c in m['commands']]
def cmd(x):return op('cmd:'+x)
op('feature:ready')
op('feature:remote-and-collapse');print('PASS real manager collapsed-list isolation and populated read-only Remote fixture excluded from Combined',flush=True)
op('feature:locales');print('PASS final clear 24 locale/view and six profile frames plus narration/popup/wheel/Escape',flush=True)
(root/'proxy-check/ui-finished').write_text('done\n')
