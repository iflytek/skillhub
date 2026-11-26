import { spawnSync } from 'node:child_process';
import { join } from 'node:path';

// Both pipes contain ASCII Base64, independent of Windows PowerShell's code page.
const script = `$ErrorActionPreference='Stop';try{
Add-Type -AssemblyName System.Security
$r=[Console]::In.ReadToEnd()|ConvertFrom-Json
$d=[Convert]::FromBase64String($r.data)
$e=[Convert]::FromBase64String($r.entropy)
if($r.action -eq 'protect'){$v=[Security.Cryptography.ProtectedData]::Protect($d,$e,[Security.Cryptography.DataProtectionScope]::CurrentUser)}
elseif($r.action -eq 'unprotect'){$v=[Security.Cryptography.ProtectedData]::Unprotect($d,$e,[Security.Cryptography.DataProtectionScope]::CurrentUser)}
else{exit 1}
[Console]::Out.Write([Convert]::ToBase64String($v))
}catch{exit 1}`;

/** The Node Host cannot use Electron Main's safeStorage. No secret enters argv. */
export function windowsAccountProtection(action: 'protect' | 'unprotect', data: Buffer, entropy: Buffer): Buffer {
  if (process.platform !== 'win32' || !data.length || data.length > 16 * 1024) throw new Error('Account protection unavailable.');
  const result = spawnSync(join(process.env.SystemRoot ?? 'C:\\Windows', 'System32', 'WindowsPowerShell', 'v1.0', 'powershell.exe'),
    ['-NoProfile', '-NonInteractive', '-Command', script], {
      input: JSON.stringify({ action, data: data.toString('base64'), entropy: entropy.toString('base64') }),
      encoding: 'utf8', windowsHide: true, timeout: 10000, maxBuffer: 64 * 1024, stdio: ['pipe', 'pipe', 'ignore'], shell: false,
    });
  if (result.error || result.status !== 0 || !result.stdout) throw new Error('Account protection unavailable.');
  const encoded = result.stdout.trim();
  const decoded = Buffer.from(encoded, 'base64');
  if (!decoded.length || encoded !== decoded.toString('base64') || decoded.length > 16 * 1024) throw new Error('Account protection unavailable.');
  return decoded;
}
