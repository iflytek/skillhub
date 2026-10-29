import base64, json, os, pathlib, subprocess, tempfile, unittest
ROOT = pathlib.Path(__file__).resolve().parents[2]
class AnalyticsStartup(unittest.TestCase):
 def render(self, raw='', upstream='https://stats.example.com'):
  with tempfile.TemporaryDirectory() as tmp:
   p=pathlib.Path(tmp)
   e=dict(os.environ, SKILLHUB_WEB_ANALYTICS_CONFIG=raw, SKILLHUB_WEB_ANALYTICS_UPSTREAM=upstream, SKILLHUB_WEB_ROOT=tmp, SKILLHUB_NGINX_ANALYTICS_CONFIG=str(p/'proxy.conf'))
   r=subprocess.run(['sh',str(ROOT/'web/docker-entrypoint.d/40-analytics.sh')],env=e,text=True,capture_output=True)
   self.assertEqual(r.returncode,0,r.stderr)
   return (p/'runtime-config.js').read_text(),(p/'proxy.conf').read_text(),r.stderr
 def config(self, **kwargs):
  return json.dumps(dict(enabled=True,adapter='umami',consent='granted',adapterConfig={'websiteId':'4398de8e-40df-4483-9eea-ed213643525f'},**kwargs))
 def test_default(self):
  runtime,proxy,_=self.render()
  self.assertNotIn('proxy_pass',proxy)
  self.assertIn('return 404',proxy)
  self.assertNotIn('script.js',proxy)
 def test_proxy(self):
  runtime,proxy,_=self.render(self.config())
  self.assertIn('location = /_skillhub-analytics/script.js',proxy)
  self.assertIn('location = /_skillhub-analytics/api/send',proxy)
  for token in ['proxy_ssl_verify on','proxy_ssl_server_name on','proxy_ssl_name stats.example.com','proxy_set_header Cookie ""','proxy_set_header Authorization ""','proxy_set_header Referer ""','proxy_pass $skillhub_analytics_upstream']:
   self.assertIn(token,proxy)
  self.assertNotIn('stats.example.com',runtime)
 def test_hostile_json_transport(self):
  raw=self.config(rules={'PRIVATE</script>\"\\\n\u2028$(touch /never)': 'click'})
  runtime,proxy,diagnostics=self.render(raw)
  encoded=runtime.split('atob("')[1].split('"')[0]
  self.assertEqual(base64.b64decode(encoded).decode(),raw)
  self.assertNotIn('PRIVATE',runtime+proxy+diagnostics)
 def test_invalid(self):
  for raw in ['PRIVATE', '{"enabled":true,', '[]', '{"enabled":"PRIVATE"}', self.config().replace('umami','PRIVATE'), self.config().replace('4398de8e-40df-4483-9eea-ed213643525f','PRIVATE')]:
   runtime,proxy,diagnostics=self.render(raw)
   self.assertNotIn('proxy_pass',proxy)
   self.assertIn('analytics',diagnostics)
   self.assertNotIn('PRIVATE',runtime+proxy+diagnostics)
 def test_upstream(self):
  for upstream in ['https://user:PRIVATE@stats.example.com','https://stats.example.com/','http://stats.example.com;PRIVATE','https://stats.example.com?PRIVATE','https://stats.example.com#PRIVATE','https://stats.example.com\nPRIVATE','http://stats.example.com:65536','http://','ftp://stats.example.com', 'http://[:::]', 'https://[12345::1]', 'http://stats.-example.com', 'http://999.999.999.999', 'http://'+'a'*64+'.example.com']:
   runtime,proxy,diagnostics=self.render(self.config(),upstream)
   self.assertNotIn('proxy_pass',proxy)
   self.assertIn('upstream',diagnostics)
   self.assertNotIn('PRIVATE',runtime+proxy+diagnostics)
 def test_disabled_denied_and_duplicate_keys(self):
  for raw in [self.config().replace('true','false'),self.config().replace('"enabled": true','"enabled": false, "enabled": true')]:
   runtime,proxy,_=self.render(raw)
   self.assertNotIn('proxy_pass',proxy)
 def test_javascript_execution_preserves_existing_keys(self):
  raw=self.config(rules={'PRIVATE</script>\"\\\n\u2028你好': 'click'})
  runtime,_,_=self.render(raw)
  code='globalThis.window={__SKILLHUB_RUNTIME_CONFIG__:{apiBaseUrl:"/existing",authDirectEnabled:"true"}};'+runtime+';process.stdout.write(JSON.stringify(window.__SKILLHUB_RUNTIME_CONFIG__));'
  result=json.loads(subprocess.check_output(['node','-e',code],text=True))
  self.assertEqual(result['analytics'],json.loads(raw))
  self.assertEqual(result['apiBaseUrl'],'/existing')
  self.assertEqual(result['authDirectEnabled'],'true')
 def test_denied_retains_routes_for_explicit_start(self):
  runtime,proxy,_=self.render(self.config().replace('granted','denied'))
  self.assertIn('proxy_pass',proxy)
  self.assertIn('atob(',runtime)
 def test_json_syntax_matrix(self):
  for raw in ['{"enabled":tru}', '{"enabled":true} PRIVATE', '{"enabled":true,}', '{"enabled":true,"rules":[1,]}', '{"enabled":01}', '{"enabled":false,"rules":"\\x"}', '{"enabled":false,"PRIVATE":1}', '{"enabled":false,"consent":true}', '{"enabled":false,"adapterConfig":[]}']:
   runtime,proxy,diagnostics=self.render(raw)
   self.assertNotIn('proxy_pass',proxy)
   self.assertIn('analytics',diagnostics)
   self.assertNotIn('PRIVATE',runtime+proxy+diagnostics)
 def test_size_depth_and_escaped_control_keys(self):
  for raw in [' '*65537, '{"rules":'+('['*34)+(']'*34)+'}', '{"\\u0065nabled":false,"enabled":true}']:
   runtime,proxy,diagnostics=self.render(raw)
   self.assertNotIn('proxy_pass',proxy)
   self.assertIn('analytics',diagnostics)
if __name__=='__main__': unittest.main()
