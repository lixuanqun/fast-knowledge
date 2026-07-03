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

export function listWikiPages(kbId: number) {
  return request.get<any, { data: WikiPage[] }>(`/kbs/${kbId}/wiki/pages`)
}

export function getWikiPage(kbId: number, slug: string) {
  return request.get<any, { data: WikiPage }>(`/kbs/${kbId}/wiki/pages/${slug}`)
}
