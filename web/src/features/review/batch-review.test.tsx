// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useBatchReview } from './batch-review'
import type { ReviewTask } from '@/api/types'
import { reviewApi } from '@/api/client'
import zh from '@/i18n/locales/zh.json'
vi.mock('@/api/client', () => ({ reviewApi: { batchPreview: vi.fn(), batchDecide: vi.fn(), get: vi.fn() } }))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string, args: Record<string, unknown> = {}) => {
  const template = zh.reviewBatch[key.split('.').at(-1) as keyof typeof zh.reviewBatch] || key
  return template.replace(/\{\{(\w+)\}\}/g, (_, name: string) => String(args[name] ?? name))
} }) }))
const task = (id: number): ReviewTask => ({ id, skillVersionId: id, skillSlug: `skill-${id}`, namespace: 'team', version: '1.0.0', status: 'PENDING', subjectType: 'SKILL_VERSION', submittedBy: 'author', submittedAt: '2026-11-17T00:00:00Z' })
function Harness({ page = 0 }: { page?: number }) {
  const b = useBatchReview('same-filter')
  const tasks = Array.from({length:20},(_,i)=>task(page*20+i+1))
  return <><h2 id="review-results" tabIndex={-1}>results</h2><button onClick={()=>b.selectPage(tasks)}>select page</button><button onClick={()=>b.guardFilter(()=>{})}>change filter</button>{b.toolbar}{b.dialogs}</>
}
function mount() { const client = new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}}); const view=render(<QueryClientProvider client={client}><Harness /></QueryClientProvider>); return {...view,setPage:(page:number)=>view.rerender(<QueryClientProvider client={client}><Harness page={page} /></QueryClientProvider>)} }
afterEach(()=>{cleanup();vi.resetAllMocks()})
describe('batch review flow',()=>{
  it('restores only IDs that remain authorized and pending after returning from detail', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    client.setQueryData(['reviews', 'selection', 'same-filter'], [1, 2])
    vi.mocked(reviewApi.get).mockImplementation(async id => { if (id === 2) throw { status: 403 }; return task(id) })
    render(<QueryClientProvider client={client}><Harness /></QueryClientProvider>)
    await waitFor(() => expect(client.getQueryData(['reviews', 'selection', 'same-filter'])).toEqual([1]))
    expect(reviewApi.get).toHaveBeenCalledWith(1)
    expect(reviewApi.get).toHaveBeenCalledWith(2)
    expect(screen.getByRole('button', { name: '批量通过' })).toBeTruthy()
  })
  it('keeps cached IDs on a transient restore failure instead of silently losing selection', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    client.setQueryData(['reviews', 'selection', 'same-filter'], [1])
    vi.mocked(reviewApi.get).mockRejectedValue(new Error('network unavailable'))
    render(<QueryClientProvider client={client}><Harness /></QueryClientProvider>)
    expect(await screen.findByRole('alert')).toBeTruthy()
    expect(client.getQueryData(['reviews', 'selection', 'same-filter'])).toEqual([1])
    expect(screen.queryByRole('button', { name: '批量通过' })).toBeNull()
  })
  it('returns focus to the initiating button when a batch is cancelled',async()=>{
    vi.mocked(reviewApi.batchPreview).mockResolvedValue({items:[{id:1,outcome:'READY',reason:'ready',message:'ready'}]})
    mount();fireEvent.click(screen.getByText('select page'))
    const trigger=screen.getByRole('button',{name:'批量通过'});trigger.focus();fireEvent.click(trigger)
    await screen.findByText('通过 1 项')
    fireEvent.click(screen.getByRole('button',{name:'取消'}))
    await waitFor(()=>expect(document.activeElement).toBe(trigger))
  })
  it('focuses the results heading after the completed batch removes its initiating button',async()=>{
    const items=Array.from({length:20},(_,i)=>({id:i+1,outcome:'READY',reason:'ready',message:'ready'}))
    vi.mocked(reviewApi.batchPreview).mockResolvedValue({items})
    vi.mocked(reviewApi.batchDecide).mockResolvedValue({items:items.map(item=>({...item,outcome:'APPROVED'}))})
    mount();fireEvent.click(screen.getByText('select page'))
    const trigger=screen.getByRole('button',{name:'批量通过'});trigger.focus();fireEvent.click(trigger)
    fireEvent.click(await screen.findByText('通过 20 项'))
    fireEvent.click(await screen.findByRole('button',{name:'返回列表'}))
    expect(trigger.isConnected).toBe(false)
    await waitFor(()=>expect(document.activeElement).toBe(screen.getByRole('heading',{name:'results'})))
  })
  it('focuses the results heading when cancelling while preview keeps the initiating button disabled',async()=>{
    let resolve!: (value: {items:[]})=>void
    vi.mocked(reviewApi.batchPreview).mockImplementation(()=>new Promise(r=>{resolve=r}))
    mount();fireEvent.click(screen.getByText('select page'))
    const trigger=screen.getByRole('button',{name:'批量通过'});trigger.focus();fireEvent.click(trigger)
    await screen.findByText(zh.reviewBatch.checking)
    fireEvent.click(screen.getByRole('button',{name:'取消'}))
    await waitFor(()=>expect(document.activeElement).toBe(screen.getByRole('heading',{name:'results'})))
    await act(async()=>resolve({items:[]}))
  })
  it('retains explicit selections across pages and caps a long queue at 50',()=>{
    const view=mount();fireEvent.click(screen.getByText('select page'));view.setPage(1);fireEvent.click(screen.getByText('select page'))
    expect(screen.getByText('已选 40 项')).toBeTruthy();view.setPage(2);fireEvent.click(screen.getByText('select page'))
    expect(screen.getByText('已选 50 项')).toBeTruthy();view.setPage(0);fireEvent.click(screen.getByText('select page'))
    expect(screen.getByText('已选 30 项')).toBeTruthy()
  })
  it('previews selection, submits only ready IDs, prevents a duplicate click, shows partial results',async()=>{
    vi.mocked(reviewApi.batchPreview).mockResolvedValue({items:Array.from({length:20},(_,i)=>({id:i+1,outcome:i<2?'READY':'SKIPPED',reason:'scan',message:i<2?'ready':'scan pending'}))})
    let resolve!: (value: {items:{id:number;outcome:string;reason:string;message:string}[]})=>void
    vi.mocked(reviewApi.batchDecide).mockImplementation(()=>new Promise(r=>{resolve=r}))
    mount();fireEvent.click(screen.getByText('select page'));fireEvent.click(screen.getByText('批量通过'))
    await waitFor(()=>expect(screen.getByText('通过 2 项')).toBeTruthy())
    const submit = screen.getByText('通过 2 项'); fireEvent.click(submit); fireEvent.click(submit)
    await waitFor(()=>expect(reviewApi.batchDecide).toHaveBeenCalledTimes(1))
    expect(reviewApi.batchDecide).toHaveBeenCalledWith({ids:[1,2],action:'APPROVE',comment:undefined})
    await act(async()=>resolve({items:[{id:1,outcome:'APPROVED',reason:'approved',message:'approved'},{id:2,outcome:'SKIPPED',reason:'conflict',message:'already handled'}]}))
    expect(await screen.findByText('already handled')).toBeTruthy()
    fireEvent.click(screen.getByText('返回列表'))
    expect(screen.getByText('已选 19 项')).toBeTruthy()
  })
  it('requires a rejection reason before dispatch and focuses its error',async()=>{
    vi.mocked(reviewApi.batchPreview).mockResolvedValue({items:[{id:1,outcome:'READY',reason:'ready',message:'ready'}]})
    mount();fireEvent.click(screen.getByText('select page'));fireEvent.click(screen.getByText('批量驳回'))
    fireEvent.click(await screen.findByText('驳回 1 项'))
    expect(await screen.findByText('请填写驳回原因。')).toBeTruthy();expect(reviewApi.batchDecide).not.toHaveBeenCalled()
    expect(document.activeElement).toBe(screen.getByLabelText('驳回原因（必填）'))
  })
  it('never automatically retries a lost response and offers status reconciliation',async()=>{
    vi.mocked(reviewApi.batchPreview).mockResolvedValue({items:[{id:1,outcome:'READY',reason:'ready',message:'ready'}]})
    vi.mocked(reviewApi.batchDecide).mockRejectedValue(new Error('timeout'))
    mount();fireEvent.click(screen.getByText('select page'));fireEvent.click(screen.getByText('批量通过'));fireEvent.click(await screen.findByText('通过 1 项'))
    expect(await screen.findByText('部分结果暂不确定。请先刷新任务状态，勿直接重复提交。')).toBeTruthy()
    expect(screen.getByText('刷新任务状态')).toBeTruthy();expect(reviewApi.batchDecide).toHaveBeenCalledTimes(1)
  })
  it('asks before clearing cross-page selection when filters change',async()=>{
    mount();fireEvent.click(screen.getByText('select page'));fireEvent.click(screen.getByText('change filter'))
    expect(await screen.findByText('更改筛选条件会清空当前跨页选择。')).toBeTruthy()
    fireEvent.click(screen.getByText('取消'));expect(screen.getByText('已选 20 项')).toBeTruthy()
  })
  it('does not change selection context while a batch preview is in flight', async () => {
    let resolve!: (value: { items: [] }) => void
    vi.mocked(reviewApi.batchPreview).mockImplementation(() => new Promise(done => { resolve = done }))
    mount()
    fireEvent.click(screen.getByText('select page'))
    fireEvent.click(screen.getByRole('button', { name: '批量通过' }))
    await screen.findByText(zh.reviewBatch.checking)
    fireEvent.click(screen.getByText('change filter'))
    expect(screen.queryByText(zh.reviewBatch.clearWarning)).toBeNull()
    expect(screen.getByText('已选 20 项')).toBeTruthy()
    await act(async () => resolve({ items: [] }))
  })
})
