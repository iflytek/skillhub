import type { NativeOperation } from '../host/types.js';

/** Only a verified successful receipt without warnings may disappear behind a summary. */
export function canCollapseHistory(operation: Pick<NativeOperation, 'phase' | 'warningCount' | 'resultDetails'> | null | undefined, queryFailed: boolean): boolean {
  return !queryFailed && operation?.phase === 'completed' && operation.warningCount === 0
    && !operation.resultDetails?.warnings.length;
}
