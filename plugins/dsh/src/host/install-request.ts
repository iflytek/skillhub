import { z } from 'zod';

export const installRequest = z.object({
  detailToken: z.string().min(1).max(2048),
  targetToken: z.string().min(1).max(2048),
  requestKey: z.uuid(),
  hostInstanceId: z.uuid(),
  mode: z.enum(['new', 'retry']),
  initiatedFrom: z.enum(['plaza', 'conversation']),
}).strict();

export const operationQuery = z.object({ operationId: z.uuid(), hostInstanceId: z.uuid() }).strict();
export const executePlanRequest = z.object({ planId: z.uuid(), requestKey: z.uuid() }).strict();
