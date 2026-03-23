/**
 * Preserves a feature-local import path for dashboard namespace screens while
 * the underlying query implementation still lives in the shared hook module.
 */
export { useMyNamespaces } from '@/shared/hooks/use-namespace-queries'
