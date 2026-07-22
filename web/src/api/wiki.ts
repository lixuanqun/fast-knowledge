import request from '@/utils/request'

export interface WikiPage {
  id: number
  kbId: number
  slug: string
  title: string
  contentMd: string
  status: string
  version: number
  updatedAt: string
}

export function listWikiPages(kbId: number, status?: string) {
  return request.get<any, { data: WikiPage[] }>(`/kbs/${kbId}/wiki/pages`, {
    params: status ? { status } : undefined
  })
}

export function getWikiPage(kbId: number, slug: string) {
  return request.get<any, { data: WikiPage }>(`/kbs/${kbId}/wiki/pages/${slug}`)
}

export function publishWikiPage(kbId: number, pageId: number) {
  return request.post<any, { data: WikiPage }>(`/kbs/${kbId}/wiki/pages/${pageId}/publish`)
}

export function rejectWikiPage(kbId: number, pageId: number) {
  return request.post<any, { data: WikiPage }>(`/kbs/${kbId}/wiki/pages/${pageId}/reject`)
}

export function rebuildWikiIndex(kbId: number) {
  return request.post<any, { data: WikiPage }>(`/kbs/${kbId}/wiki/index/rebuild`)
}
