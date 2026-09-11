import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'

/** 配置 marked：代码高亮 + 链接新窗口打开 + 安全属性 */
const renderer = new marked.Renderer()

renderer.code = function ({ text, lang }) {
  const language = lang && hljs.getLanguage(lang) ? lang : 'plaintext'
  let highlighted
  try {
    highlighted = hljs.highlight(text, { language }).value
  } catch {
    highlighted = escapeHtml(text)
  }
  return `<pre><code class="hljs language-${language}">${highlighted}</code></pre>`
}

renderer.link = function ({ href, title, tokens }) {
  const text = this.parser.parseInline(tokens)
  const titleAttr = title ? ` title="${escapeHtml(title)}"` : ''
  return `<a href="${escapeHtml(href)}"${titleAttr} target="_blank" rel="noopener noreferrer">${text}</a>`
}

renderer.image = function ({ href, title, text }) {
  return `<img src="${escapeHtml(href)}" alt="${escapeHtml(text || '')}"${
    title ? ` title="${escapeHtml(title)}"` : ''
  } loading="lazy" />`
}

marked.setOptions({
  renderer,
  gfm: true,
  breaks: true
})

/**
 * 将 Markdown 渲染为 HTML
 * @param {string} source Markdown 原文
 * @returns {string} HTML 片段
 */
export function renderMarkdown(source) {
  if (!source) return ''
  try {
    return marked.parse(source)
  } catch {
    return `<p>${escapeHtml(source)}</p>`
  }
}

/** HTML 转义，防止 XSS */
export function escapeHtml(str) {
  if (str === null || str === undefined) return ''
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}
