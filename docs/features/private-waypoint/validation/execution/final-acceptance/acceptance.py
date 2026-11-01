from pathlib import Path
import sys,os,json,shutil,subprocess,time,hashlib,contextlib
ROOT=Path('/Volumes/ssd/sw-private-final-20261031')
PREP=Path('/Volumes/ssd/sw-private-task8-20261031/prepared')
sys.path.insert(0,'/Volumes/ssd/fabric_mods_repo/server_waypoint/tools/live-game-test')
from runner import OwnedProcess,attach,read_launch_script,isolated_launch,write_options,wait_result
rows=json.loads((PREP/'prepared.json').read_text())['profiles'];row=rows[0]
clients={};servers={};n=0;events=[]

def note(message):
 print(message,flush=True);events.append(message);(ROOT/'progress.json').write_text(json.dumps(events,indent=2))
def server(name):
 home=ROOT/name;log=(home/'acceptance-server.log').open('w')
 p=subprocess.Popen(['/opt/homebrew/opt/openjdk@25/bin/java','-Xms512M','-Xmx1536M','-jar','server.jar','--nogui'],cwd=home,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True)
 servers[name]=p;until=time.time()+120
 while time.time()<until:
  if p.poll()!=None:raise RuntimeError(name+' server exited')
  if 'Done (' in (home/'acceptance-server.log').read_text():note('READY '+name);return
  time.sleep(.25)
 raise RuntimeError(name+' startup timeout')
def console(cmd,name='paper'):
 p=servers[name];p.stdin.write(cmd+'\n');p.stdin.flush();time.sleep(.4)
def launch(name,vanilla=False):
 home=ROOT/name;home.mkdir(exist_ok=True);game=home/'game';game.mkdir(exist_ok=True);(game/'mods').mkdir(exist_ok=True)
 if not vanilla:
  for item in row['jars']:shutil.copy(item['path'],game/'mods'/item['name'])
 command,env=read_launch_script(row['launch_script']);args=isolated_launch(command,game,768,row['launcher_agents'])
 args[args.index('--username')+1]=name
 raw=hashlib.md5(('OfflinePlayer:'+name).encode()).digest();b=bytearray(raw);b[6]=(b[6]&15)|48;b[8]=(b[8]&63)|128
 import uuid
 args[args.index('--uuid')+1]=uuid.UUID(bytes=bytes(b)).hex
 if vanilla:args[args.index('net.fabricmc.loader.impl.launch.knot.KnotClient')]='net.minecraft.client.main.Main'
 write_options(game/'options.txt');(game/'options.txt').write_text((game/'options.txt').read_text()+'tutorialStep:none\n')
 env={**os.environ,**env};p=OwnedProcess(args,game,env);p.__enter__();clients[name]=(p,args[0],home)
 until=time.time()+120
 while time.time()<until:
  f=home/'title.txt';f.unlink(missing_ok=True);attach(args[0],PREP/'tools.jar',p,PREP/'agent.jar',f'screen|{f}',home/'title-attach.log')
  if f.exists() and 'title=true' in f.read_text() and 'overlay=null' in f.read_text():note('READY '+name+(' VANILLA' if vanilla else ' MODDED'));return
  time.sleep(3)
 raise RuntimeError(name+' client startup timeout')
def op(name,action):
 global n
 n+=1;p,java,home=clients[name];f=home/(f'{n:03}-'+action.split(':',1)[0]+'.result')
 attach(java,PREP/'tools.jar',p,PREP/'agent.jar',f'scenario|{f}|{action}|{ROOT / "checks.jar"}',f.with_suffix('.attach.log'))
 wait_result(p,f,100)
 (home/'actions.log').open('a').write(str(n)+' '+action+'\n')
 j=f.with_name(f.name+'.json');return json.loads(j.read_text()) if j.exists() else None

def text(value):return '\n'.join(m['text'] for m in value['chat'])
def links(value):return [c for m in value['chat'] for c in m['commands']]
def require(x,why):
 if not x:raise AssertionError(why)
def cmd(name,command,expected=None):
 result=op(name,'cmd:'+command)
 if expected:require(expected.casefold() in text(result).casefold(),name+' '+command+' expected '+expected+' got '+text(result))
 return result

def tests():
 server('paper')
 for name in ['WPAlice','WPBob','WPVanilla']:launch(name,name=='WPVanilla');op(name,'connect:127.0.0.1:25648')
 for name in ['WPAlice','WPBob']:op(name,'feature:ready')
 for name,x in [('WPAlice',12),('WPBob',-44),('WPVanilla',70)]:cmd(name,f'wp private add {x} 80 -30 Homes Base','Added')
 cmd('WPAlice','wp add 100 80 100 Homes Base','Added');cmd('WPAlice','wp private add 18 80 20 OnlyAlice AliceSecret','Added')
 state=op('WPBob','feature:state');require(all(r['name']!='AliceSecret' for r in state['PRIVATE']['rows']),'Bob private cache contains Alice')
 require('AliceSecret' not in text(cmd('WPBob','wp private list all')),'Bob list leaked')
 require('OnlyAlice' not in op('WPBob','suggest:wp private details list minecraft:overworld '),'Bob list suggestions leaked')
 require('AliceSecret' not in op('WPBob','suggest:wp private details waypoint minecraft:overworld Homes '),'Bob waypoint suggestions leaked')
 note('PASS owner-only menus/suggestions/caches across two modded identities')
 op('WPBob','clear');before=op('WPBob','feature:state');cmd('WPAlice','wp private edit waypoint minecraft:overworld Homes Base set description AliceOnly','Updated');after=op('WPBob','feature:state')
 require(before==after,'Alice edit changed Bob collections');require('AliceOnly' not in text(op('WPBob','snapshot')),'private announcement leaked')
 note('PASS owner-only edit delivery/no Bob announcement or state mutation')
 for command in ['wp private','wp private help','wp private details waypoint minecraft:overworld Homes Base','wp private list all limit 1 view flat page 1']:
  result=cmd('WPVanilla',command);commands=links(result);require(commands,'vanilla clickable links absent '+command)
  bad=[c for c in commands if c.startswith('/wp ') and not c.startswith('/wp private')]
  require(not bad,'private prefix lost '+str(bad))
 note('PASS graphical vanilla private menu/help/details/pagination click-event prefixes')
 # Wrong-owner tokens are not consumed; successful restore is single use.
 removed=cmd('WPVanilla','wp private remove minecraft:overworld Homes Base','Removed');token=next(c for c in links(removed) if ' private restore ' in c)
 denied=cmd('WPBob',token.lstrip('/'));require('Restored' not in text(denied),'wrong-owner restore accepted')
 cmd('WPVanilla',token.lstrip('/'),'Restored');require('Restored' not in text(cmd('WPVanilla',token.lstrip('/'))),'restore reused')
 note('PASS live wrong-owner restore, owner restore, single-use')
 cmd('WPVanilla','wp private navigate minecraft:overworld Homes Base actionbar','navigating')
 op('WPVanilla','leave');op('WPVanilla','connect:127.0.0.1:25648');nav=cmd('WPVanilla','wp private navigate');require('Base' in text(nav),'navigation not restored');require(all(c.startswith('/wp private') for c in links(nav) if c.startswith('/wp ')),'navigation links lost scope')
 note('PASS vanilla private navigation reconnect/prefix')
 # Default private teleport blocked; a narrow plugin grant enables actual arrival.
 denied=cmd('WPVanilla','wp private tp minecraft:overworld Homes Base');require('Teleported' not in text(denied),'default teleport allowed')
 console('acceptance permission WPVanilla private.tp true');cmd('WPVanilla','wp private tp minecraft:overworld Homes Base','Teleported');position=op('WPVanilla','snapshot')['position'];require(abs(position[0]-70.5)<1 and abs(position[2]+29.5)<1,'vanilla wrong arrival '+str(position))
 note('PASS graphical vanilla default tp denial, permission grant and arrival')
 op('WPAlice','feature:seed-local');op('WPAlice','feature:manager');op('WPAlice','feature:copy');op('WPAlice','feature:copy-conflict');op('WPAlice','feature:edit-captured');op('WPAlice','feature:stale-form');op('WPAlice','feature:scene');op('WPAlice','feature:resync')
 note('PASS manager defaults/search/grouping, copy metadata/cancel/conflict, captured edit, stale form, source-owned native markers/resync')
 # Revocation must clear only Private while Local remains usable.
 before=op('WPAlice','feature:state');console('acceptance permission WPAlice private.list false');denied=op('WPAlice','feature:request-private');require(denied['PRIVATE']['state']=='DENIED' and not denied['PRIVATE']['rows'],'Private did not clear on denial');require(denied['LOCAL']==before['LOCAL'] and denied['SHARED']==before['SHARED'],'denial cleared another source')
 console('acceptance permission WPAlice private.list true');op('WPAlice','feature:request-private');op('WPAlice','feature:ready')
 note('PASS live read-permission revocation clears Private only and regrant recovers')
 note('COMPLETE main acceptance')

try:tests()
except BaseException as exc:
 note('FAIL '+repr(exc));raise
finally:
 for name,(p,java,home) in clients.items():
  try:
   if p.poll() is None:attach(java,PREP/'tools.jar',p,PREP/'agent.jar',f'quit|{home/"quit.result"}|{ROOT/"checks.jar"}',home/'quit.attach.log');p.process.wait(timeout=30)
  except Exception:pass
  p.__exit__()
 for name,p in servers.items():
  if p.poll() is None:
   p.stdin.write('stop\n');p.stdin.flush()
   try:p.wait(timeout=60)
   except subprocess.TimeoutExpired:p.terminate();p.wait(timeout=15)
