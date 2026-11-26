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
 env={**os.environ,**env};p=OwnedProcess(args,game,env);p.__enter__();clients[name]=(p,args[0],home);(home/"process.json").write_text(json.dumps({"pid":p.pid,"java":args[0]}))
 until=time.time()+120
 while time.time()<until:
  f=home/'title.txt';f.unlink(missing_ok=True);attach(args[0],PREP/'tools.jar',p,PREP/'agent.jar',f'screen|{f}',home/'title-attach.log')
  if f.exists() and 'title=true' in f.read_text() and 'overlay=null' in f.read_text():note('READY '+name+(' VANILLA' if vanilla else ' MODDED'));return
  time.sleep(3)
 raise RuntimeError(name+' client startup timeout')
def op(name,action):
 global n
 n+=1;p,java,home=clients[name];f=home/(f'{n:03}-'+action.split(':',1)[0]+'.result')
 attach(java,PREP/'tools.jar',p,PREP/'agent.jar',f'scenario|{f}|{action}|{Path("/Volumes/ssd/sw-private-final-20261031/checks.jar")}',f.with_suffix('.attach.log'))
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

