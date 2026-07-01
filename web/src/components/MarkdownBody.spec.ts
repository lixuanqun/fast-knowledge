import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import MarkdownBody from '@/components/MarkdownBody.vue'

describe('MarkdownBody', () => {
  it('renders markdown as sanitized html', () => {
    const wrapper = mount(MarkdownBody, {
      props: { content: '**hello**' }
    })
    expect(wrapper.find('.markdown-body').html()).toContain('<strong>hello</strong>')
  })

  it('strips script tags from raw html', () => {
    const wrapper = mount(MarkdownBody, {
      props: { content: '<script>alert("xss")</script>safe' }
    })
    const html = wrapper.find('.markdown-body').html()
    expect(html).not.toContain('<script')
    expect(html).toContain('safe')
  })
})
