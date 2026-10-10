# Debug-only visual comparison of the original table and V3 on the same QA app/device.
# Build/install the isolated QA APK first; WindowDump comes from test-server-table-android.cjs.
# Required: TRAIDORES_QA_ADB, TRAIDORES_QA_DEVICE. Argument: evidence directory name.
from pathlib import Path
import subprocess,time,sys,re,os
adb=os.environ['TRAIDORES_QA_ADB'];dev=os.environ['TRAIDORES_QA_DEVICE'];pkg='com.traidores.juego.v3qa'
out=Path('output/paridad-visual')/sys.argv[1];out.mkdir(parents=True,exist_ok=True)
def call(*args):return subprocess.check_output([adb,'-s',dev,*args])
dimensions=re.findall(r'(\d+)x(\d+)',call('shell','wm','size').decode())[-1]
call('push',str(Path('output/android-qa/window-dump.jar')),'/data/local/tmp/traidores-window-dump.jar')
def xml():
 for attempt in range(5):
  try:
   call('shell','rm','-f','/sdcard/parity.xml')
   call('shell','CLASSPATH=/system/framework/uiautomator.jar:/data/local/tmp/traidores-window-dump.jar app_process /system/bin WindowDump /sdcard/parity.xml '+' '.join(dimensions))
   return call('shell','cat','/sdcard/parity.xml')
  except subprocess.CalledProcessError:
   if attempt==4: raise
   time.sleep(.4)

# APK is already installed
for label,phase,chat,expulsion in [('noche','NOCHE',False,False),('amanecer','AMANECER',False,False),('debate-cerrado','DIA_DEBATE',False,False),('debate-abierto','DIA_DEBATE',True,False),('votacion','VOTACION',False,False),('recuento','RECUENTO_VOTOS',False,False),('resultado','RESULTADO',False,False),('expulsion','RESULTADO',False,True),('victoria','FINALIZADA',False,False)]:
 if os.environ.get('TRAIDORES_QA_CAPTURE_ONLY') and label not in os.environ['TRAIDORES_QA_CAPTURE_ONLY'].split(','): continue
 for common in [True,False]:
  call('shell','am','force-stop',pkg)
  call('shell','am','start','-W','-n',pkg+'/com.traidores.juego.GameplayParityActivity','--es','phase',phase,'--ez','common',str(common).lower(),'--ez','chat',str(chat).lower(),'--ez','expulsion',str(expulsion).lower());time.sleep(1.6)
  if common and chat:
   tree=xml().decode();node=next((n for n in re.findall(r'<node\b[^>]*>',tree) if '/chatAmbientTitle"' in n),None)
   if node:
    b=[int(n) for n in re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',node).groups()];call('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2));time.sleep(.4)
  tree=xml()
  assert b'/phaseTitle' in tree or b'/winnerReveal' in tree or b'/voteResult' in tree, 'The table/result must be visible before capture'
  if label=='resultado': assert b'EL PUEBLO NO LLEG' in tree, 'Result fixture must stay in the no-expulsion window'
  if label=='expulsion':
   # Wait for the shared ceremony's sentence, rather than capturing its opening.
   for attempt in range(12):
    if b'EXPULS' in tree: break
    time.sleep(.5);tree=xml()
   assert b'EXPULS' in tree, 'Expulsion window missing'
  if label=='victoria': assert b'/winnerReveal' in tree, 'Victory window missing'
  (out/(label+('-comun' if common else '-v3')+'.png')).write_bytes(call('exec-out','screencap','-p'))
  (out/(label+('-comun' if common else '-v3')+'.xml')).write_bytes(xml())
  print(label,common,flush=True)
