import { useEffect, useState } from 'react'
import type { RefObject } from 'react'

type Entry = { id: string; label: string; nested: boolean }

export default function PublicReadingContents({ articleRef, documentId }: { articleRef: RefObject<HTMLElement | null>; documentId: string }) {
  const [entries, setEntries] = useState<Entry[]>([])
  const [expanded, setExpanded] = useState(() => window.innerWidth > 900)

  useEffect(() => {
    // Read rendered headings so code fences and inline Markdown match the article exactly.
    const headings = Array.from(articleRef.current?.querySelectorAll<HTMLElement>('.public-reading-body :is(h2,h3,h4,h5,h6)') ?? [])
    setEntries(headings.map((heading, index) => {
      const id = `reading-section-${index}`
      heading.id = id
      heading.tabIndex = -1
      return { id, label: heading.textContent ?? '', nested: heading.tagName !== 'H2' }
    }))
    return () => { headings.forEach(heading => { heading.removeAttribute('id'); heading.removeAttribute('tabindex') }) }
  }, [articleRef, documentId])

  return <details className="public-reading-contents" open={expanded} onToggle={event => setExpanded(event.currentTarget.open)}>
    <summary>面经目录</summary>
    <nav aria-label="面经目录"><a href="#reading-body">面经正文</a>{entries.map(entry => <a key={entry.id} className={entry.nested ? 'nested' : undefined} href={`#${entry.id}`}>{entry.label}</a>)}<a href="#reading-comments">评论</a></nav>
  </details>
}
