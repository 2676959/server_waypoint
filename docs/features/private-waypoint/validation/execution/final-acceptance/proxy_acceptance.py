import acceptance_base as b
from pathlib import Path
import subprocess,time
b.ROOT=Path('/Volumes/ssd/sw-private-final-20261031/proxy-check')
proxy=None
try:
 b.server('a');b.server('b')
 home=b.ROOT/'velocity';log=(home/'proxy.log').open('w');proxy=subprocess.Popen(['/opt/homebrew/opt/openjdk@25/bin/java','-Xmx512M','-jar','server.jar'],cwd=home,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True)
 until=time.time()+60
 while 'Done (' not in (home/'proxy.log').read_text():
  if proxy.poll()!=None or time.time()>until:raise RuntimeError('proxy startup')
  time.sleep(.25)
 b.launch('WPProxy');b.op('WPProxy','connect:127.0.0.1:25653');b.op('WPProxy','feature:ready');a=b.op('WPProxy','feature:state');aid=a['profile']['id'];b.op('WPProxy','feature:seed-local');b.op('WPProxy','feature:capture-transfer')
 b.cmd('WPProxy','server b');time.sleep(2);b.op('WPProxy','feature:ready');second=b.op('WPProxy','feature:state');bid=second['profile']['id'];b.require(aid!=bid and not second['LOCAL']['rows'],'new backend reused Local store');b.op('WPProxy','feature:stale-transfer');b.op('WPProxy','feature:seed-local')
 manual=b.op('WPProxy','feature:new-profile');mid=manual['profile']['id'];b.console('acceptance handshake WPProxy 999999','b');time.sleep(2);after=b.op('WPProxy','feature:state');b.require(after['profile']['id']==mid,'late handshake overrode manual profile')
 b.note('PASS real Velocity transfer creates separate profile; stale source rejected; live late-handshake packet retains manual selection')
 b.cmd('WPProxy','server a');time.sleep(2);b.op('WPProxy','feature:ready');restored=b.op('WPProxy','feature:state');b.require(restored['profile']['id']==aid and len(restored['LOCAL']['rows'])==2,'return transfer lost saved A data')
 b.note('PASS return proxy transfer restores original profile/data without disconnect')
 b.op('WPProxy','feature:open-manager');b.note('WAIT_UI')
 until=time.time()+240
 while not (b.ROOT/'ui-finished').exists() and time.time()<until:time.sleep(.5)
 b.note('COMPLETE proxy acceptance')
finally:
 for name,(p,java,home) in b.clients.items():
  try:
   if p.poll()==None:b.attach(java,b.PREP/'tools.jar',p,b.PREP/'agent.jar',f'quit|{home/"quit.result"}|{b.ROOT.parent/"checks.jar"}',home/'quit.attach.log');p.process.wait(timeout=20)
  except Exception:pass
  p.__exit__()
 if proxy and proxy.poll()==None:proxy.stdin.write('shutdown\n');proxy.stdin.flush();proxy.wait(timeout=20)
 for p in b.servers.values():
  if p.poll()==None:p.stdin.write('stop\n');p.stdin.flush();p.wait(timeout=60)
