import os,json,re,subprocess,time,shutil,hashlib,tarfile
from pathlib import Path
from urllib.parse import urlsplit
# Requires a fresh, already installed isolated Profile with the discovery observer.
import argparse
import tempfile
parser=argparse.ArgumentParser(description='Verify dual-target upgrade recovery across a real DSH restart')
parser.add_argument('--root', required=True, type=Path)
parser.add_argument('--node', required=True, type=Path)
parser.add_argument('--chromium', required=True, type=Path)
parser.add_argument('--success', action='store_true', help='Verify completed dual-target upgrade history across Host restart')
parser.add_argument('--crash', action='store_true', help='Kill the isolated Host while its upgrade transaction is paused')
parser.add_argument('--unsupported-dsh-bin', type=Path, help='Boot an isolated unsupported DSH installation before returning to the original runtime')
parser.add_argument('--replacement-tarball', type=Path, help='Replace the plugin from a retained local tarball while the Host is stopped')
args=parser.parse_args()
assert not (args.success and args.crash)
root=args.root.resolve()
assert root != Path(tempfile.gettempdir()).resolve() and root.is_relative_to(Path(tempfile.gettempdir()).resolve()), 'Root must be an isolated temporary directory'
assert (root/'home/profiles/skillhub-m0/package.json').is_file(), 'Initialize and install the isolated Profile first'
assert not (root/'discovery-report.json').exists(), 'Use a fresh fixture; do not replay setup on existing installations'
repo=Path(__file__).resolve().parents[4]
node=str(args.node.resolve())
assert Path(node).is_file() and args.chromium.resolve().is_file()
unsupported_version=None
if args.unsupported_dsh_bin:
 args.unsupported_dsh_bin=args.unsupported_dsh_bin.resolve()
 assert args.unsupported_dsh_bin.is_relative_to(Path(tempfile.gettempdir()).resolve()) and args.unsupported_dsh_bin.is_file()
 manifest=json.loads((args.unsupported_dsh_bin.parent.parent/'package.json').read_text())
 assert manifest['name']=='@deepseek-ai/dsh' and manifest['version']!='0.2.0-rc.2'
 unsupported_version=manifest['version']
replacement=None
if args.replacement_tarball:
 args.replacement_tarball=args.replacement_tarball.resolve()
 assert args.replacement_tarball.is_file()
 with tarfile.open(args.replacement_tarball) as archive:
  replacement=json.load(archive.extractfile('package/package.json'))
  assert replacement['name']=='@astron-team/skillhub-dsh'
(root/'update-state').write_text('current')
out=root/'recovery-result';out.mkdir(exist_ok=False)
children=[]
def wait_file(path,process,timeout=60):
 deadline=time.monotonic()+timeout
 while not path.exists():
  assert process.poll() is None,f'Process exited {process.returncode}'
  assert time.monotonic()<deadline,'Expected fixture file unavailable'
  time.sleep(.2)
 return json.loads(path.read_text())
def start_host(port,first,bin_path=None,log_name=None):
 env=dict(os.environ,HOME=str(root/'native-install-home'),USERPROFILE=str(root/'native-install-home'),DSH_HOME=str(root/'home'),DSH_AGENTS_HOME=str(root/'agents'),DSH_TELEMETRY_DISABLED='1')
 if first:
  env.update(DSH_UPGRADE_FIXTURE='1',DSH_UPGRADE_TWO_TARGETS='1')
  if not args.success:env['DSH_UPGRADE_RECOVERY_FIXTURE']='1'
 else:env['DSH_PROBE_SKIP_DISCOVERY']='1'
 if first and args.crash:env['DSH_UPGRADE_CRASH_FIXTURE']='1'
 logpath=out/(log_name or ('host-initial.log' if first else 'host-restarted.log'))
 log=open(logpath,'w')
 installed=root/'home/profiles/skillhub-m0/node_modules/@astron-team/skillhub-dsh'
 ids=re.findall(r'^\s*- id: (\S+)$',(installed/'cordis.patch.yml').read_text(),re.M)
 assert len(ids)==1 and ids[0] in ['skillhub','astron-skillhub']
 overlay=out/'source.yml';overlay.write_text(f'- id: {ids[0]}\n  config:\n    registryUrl: http://127.0.0.1:43219\n')
 p=subprocess.Popen([node,str(bin_path or 'node_modules/@deepseek-ai/dsh/lib/bin.js'),'--profile','skillhub-m0','--patch',str(overlay),'--host','127.0.0.1','--port',str(port),'--no-open'],cwd=root,env=env,stdout=log,stderr=log);children.append(p)
 deadline=time.monotonic()+60
 while True:
  assert p.poll() is None,f'Host exited {p.returncode}'
  m=re.search(r'dsh web: (http://127\.0\.0\.1:\d+/\?token=\S+)',logpath.read_text())
  if m:return p,m.group(1)
  assert time.monotonic()<deadline,'Host not ready'
  time.sleep(.2)
def hashes(path):return {str(p.relative_to(path)):hashlib.sha256(p.read_bytes()).hexdigest() for p in path.rglob('*') if p.is_file()}
try:
 log=open(out/'registry.log','w')
 reg=subprocess.Popen(['node','plugins/dsh/test/integration/catalog-fixture.mjs'],cwd=repo,env=dict(os.environ,DSH_FIXTURE_UPDATE_STATE=str(root/'update-state')),stdout=log,stderr=log);children.append(reg)
 old,entry=start_host(0,True)
 report=wait_file(root/'discovery-report.json',old,90);assert report['passed'],report
 targets=[root/'home/skills/document-helper',root/report['installFixtureWorkspace']/'.dsh/skills/document-helper']
 assert all(p.is_dir() for p in targets)
 before=[hashes(p) for p in targets];inventory=root/'native-install-home/.skillhub/inventory.json';inventory_before=inventory.read_bytes()
 shutil.copy2(repo/'plugins/dsh/test/integration/upgrade-recovery-probe.mjs',root/'upgrade-recovery-probe.mjs')
 env=dict(os.environ,DSH_PROBE_URL=entry,DSH_PROBE_OUTPUT=str(out),DSH_INSTALL_FIXTURE_TITLE=report['installFixtureTitle'],DSH_INSTALL_FIXTURE_WORKSPACE=report['installFixtureWorkspace'],DSH_PROBE_RESTART='1',DSH_UPGRADE_TWO_TARGETS='1',DSH_FIXTURE_UPDATE_STATE=str(root/'update-state'),DSH_PROBE_CHROMIUM=str(args.chromium.resolve()))
 if args.success:env['DSH_UPGRADE_SUCCESS']='1'
 if args.crash:env['DSH_UPGRADE_CRASH_FIXTURE']='1'
 log=open(out/'browser.log','w');browser=subprocess.Popen(['node','upgrade-recovery-probe.mjs'],cwd=root,env=env,stdout=log,stderr=log);children.append(browser)
 op=wait_file(out/'restart-ready.json',browser,90)['operationId'];journal=root/'home/skillhub/operations'/f'{op}.json';j=json.loads(journal.read_text())
 if args.crash:
  wait_file(root/'upgrade-crash-ready.json',old)
  assert j['operation']['status']['phase'] not in ['completed','failed','recovery-required']
 else:assert j['operation']['status']['phase']==('completed' if args.success else 'recovery-required')
 target_states=[p.exists() for p in targets];assert sum(target_states)==(2 if args.success else 1),target_states
 if args.crash:old.kill()
 else:old.terminate()
 old.wait(timeout=30)
 if args.crash:assert old.returncode==-9
 if replacement:
  pending_journal=journal.read_bytes();pending_inventory=inventory.read_bytes()
  env_install=dict(os.environ,HOME=str(root/'native-install-home'),USERPROFILE=str(root/'native-install-home'),DSH_HOME=str(root/'home'),DSH_AGENTS_HOME=str(root/'agents'))
  with (out/'replace-package.log').open('w') as install_log:
   subprocess.run([node,str(root/'node_modules/@deepseek-ai/dsh/lib/bin.js'),'plugin','--profile','skillhub-m0','add',str(args.replacement_tarball),'--ignore-scripts'],cwd=root,env=env_install,stdout=install_log,stderr=install_log,check=True,timeout=120)
  installed=root/'home/profiles/skillhub-m0/node_modules/@astron-team/skillhub-dsh'
  with tarfile.open(args.replacement_tarball) as archive:
   for relative in ['lib/index.js','lib/local-core.js','lib/client.js','lib/typert.host.js','lib/typert.remote-client.js','cordis.patch.yml']:
    assert (installed/relative).read_bytes()==archive.extractfile('package/'+relative).read()
  assert journal.read_bytes()==pending_journal and inventory.read_bytes()==pending_inventory
  assert [p.exists() for p in targets]==target_states
 if args.unsupported_dsh_bin:
  pending_journal=journal.read_bytes()
  pending_inventory=inventory.read_bytes()
  intermediate,_=start_host(0,False,args.unsupported_dsh_bin,'host-unsupported.log')
  rejection=(out/'host-unsupported.log').read_text()
  assert 'skipping profile bundle "@astron-team/skillhub-dsh"' in rejection and 'Exact-version exemption: not active' in rejection
  intermediate.terminate();intermediate.wait(timeout=30)
  assert journal.read_bytes()==pending_journal and inventory.read_bytes()==pending_inventory
  assert [p.exists() for p in targets]==target_states
 new,new_entry=start_host(urlsplit(entry).port,False);assert new.pid!=old.pid
 if args.crash:time.sleep(11) # Let the dead process's heartbeat leases expire; never delete locks manually.
 resume=out/'restart-resume.json';fd=os.open(resume,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
 with os.fdopen(fd,'w') as f:json.dump({'entry':new_entry},f)
 code=browser.wait(timeout=90)
 if code:print('Browser failed; inspect the private browser.log in the isolated result directory')
 assert code==0,'Browser failed'
 if args.success:
  assert all(json.loads((p/'.skillhub/metadata.json').read_text())['version']=='1.3.0' for p in targets)
  assert all(hashes(p)!=old_hash for p,old_hash in zip(targets,before))
 else:
  assert inventory.read_bytes()==inventory_before
  assert [hashes(p) for p in targets]==before
 j=json.loads(journal.read_text())
 assert j['operation']['status']['phase']=='completed' if args.success else j['operation']['status']['recovery']=='restored'
 result={'passed':True,'node':subprocess.check_output([node,'-p','process.versions.node'],text=True).strip(),'forcedProcessExit':args.crash,'oldHostExited':True,'newHostStarted':True,'distinctProcesses':True,'samePort':urlsplit(entry).port==urlsplit(new_entry).port,'targetCount':2,'mixedStateBeforeRestart':not args.success,'allTargetHashesRestored':not args.success,'inventoryUnchanged':inventory.read_bytes()==inventory_before,'operationRecovery':j['operation']['status']['recovery'],'completedUpgrade':args.success,'browser':json.loads((out/'upgrade-result.json').read_text())}
 if replacement:result.update(replacementVersion=replacement['version'],replacementSha256=hashlib.sha256(args.replacement_tarball.read_bytes()).hexdigest(),replacementFilesMatched=6,pendingJournalUnchangedDuringPackageReplacement=True)
 if unsupported_version:result.update(unsupportedHostVersion=unsupported_version,unsupportedBundleRejected=True,pendingJournalUnchangedAcrossUnsupportedHost=True)
 (out/'host-files-result.json').write_text(json.dumps(result,indent=2));print(json.dumps(result,indent=2))
finally:
 for p in reversed(children):
  if p.poll() is None:p.terminate();p.wait(timeout=30)
 resume=out/'restart-resume.json'
 if resume.exists():resume.unlink()
