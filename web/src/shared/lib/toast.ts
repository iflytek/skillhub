import { toast as sonnerToast, type ExternalToast } from 'sonner'

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

export const toast = {
  success: (message: string, description?: string, options?: ExternalToast) => {
    sonnerToast.success(message, { description, ...options })
  },
  error: (message: string, description?: string, options?: ExternalToast) => {
    sonnerToast.error(message, { description, ...options })
  },
  warning: (message: string, description?: string, options?: ExternalToast) => {
    sonnerToast.warning(message, { description, ...options })
  },
  info: (message: string, description?: string, options?: ExternalToast) => {
    sonnerToast.info(message, { description, ...options })
  },
  promise: <T,>(
    promise: Promise<T>,
    options: {
      loading: string
      success: string | ((data: T) => string)
      error: string | ((error: Error) => string)
    }
  ) => {
    return sonnerToast.promise(promise, options)
  },
}
