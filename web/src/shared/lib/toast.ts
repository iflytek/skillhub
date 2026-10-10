import { toast as sonnerToast, type ExternalToast } from 'sonner'
import { truncateErrorMessage } from './error-display'

export const CENTER_TOASTER_ID = 'top-center'

export function centeredToastOptions(options?: ExternalToast): ExternalToast {
  return {
    toasterId: CENTER_TOASTER_ID,
    classNames: {
      title: 'text-center font-semibold',
      description: 'text-center',
      ...options?.classNames,
    },
    ...options,
  }
}

function withDefaultToaster(options?: ExternalToast): ExternalToast {
  return {
    toasterId: CENTER_TOASTER_ID,
    ...options,
  }
}

/**
 * Sonner applies toast state with ReactDOM.flushSync. Calling that mid-commit
 * (e.g. QueryCache onError while /search remounts cards) races React 19 DOM
 * reconciliation → removeChild / insertBefore. Defer past the current turn.
 */
function scheduleToast(run: () => void) {
  if (typeof globalThis.window === 'undefined') {
    run()
    return
  }
  globalThis.setTimeout(run, 0)
}

export const toast = {
  success: (message: string, description?: string, options?: ExternalToast) => {
    scheduleToast(() => {
      sonnerToast.success(message, { description, ...withDefaultToaster(options) })
    })
  },
  error: (message: string, description?: string, options?: ExternalToast) => {
    scheduleToast(() => {
      sonnerToast.error(truncateErrorMessage(message) ?? message, {
        description: truncateErrorMessage(description),
        ...withDefaultToaster(options),
      })
    })
  },
  warning: (message: string, description?: string, options?: ExternalToast) => {
    scheduleToast(() => {
      sonnerToast.warning(message, { description, ...withDefaultToaster(options) })
    })
  },
  info: (message: string, description?: string, options?: ExternalToast) => {
    scheduleToast(() => {
      sonnerToast.info(message, { description, ...withDefaultToaster(options) })
    })
  },
  promise: <T,>(
    promise: Promise<T>,
    options: {
      loading: string
      success: string | ((data: T) => string)
      error: string | ((error: Error) => string)
    }
  ) => {
    return sonnerToast.promise(promise, { ...options, toasterId: CENTER_TOASTER_ID })
  },
}
