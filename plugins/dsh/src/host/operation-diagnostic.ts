import type { NativeOperation } from './generated/native-contract.js';

export interface OperationDiagnostic {
  operationId: string;
  kind: NativeOperation['kind'];
  code: string;
}
const filesystemCodes = new Set(['EACCES', 'EPERM', 'ENOENT', 'EEXIST', 'ENOSPC', 'EBUSY', 'EXDEV', 'ENOTEMPTY', 'EIO']);
const networkCodes = new Set(['ECONNRESET', 'ECONNREFUSED', 'ETIMEDOUT', 'ENOTFOUND', 'EAI_AGAIN']);
/** Never copy exception messages, arbitrary codes, paths or request data into diagnostics. */
export function operationDiagnostic(operation: NativeOperation, error: unknown): OperationDiagnostic {
  let code = 'UNCLASSIFIED';
  if (error && typeof error === 'object') {
    const nativeCode = 'code' in error ? error.code : undefined;
    if (typeof nativeCode === 'string' && (filesystemCodes.has(nativeCode) || networkCodes.has(nativeCode))) code = nativeCode;
    else if ('exitCode' in error) {
      // The shared CLI core's public exit categories, not its potentially sensitive details.
      switch (error.exitCode) {
        case 2: code = 'AUTHORIZATION'; break;
        case 3: code = 'NETWORK'; break;
        case 4: code = 'FILESYSTEM'; break;
        case 5: code = 'INVALID_INPUT'; break;
        case 6: code = 'VALIDATION'; break;
      }
    }
  }
  return { operationId: operation.operationId, kind: operation.kind, code };
}
