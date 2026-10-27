import { useId, useLayoutEffect, useRef, useState } from 'react'
import { MarkdownPreview } from './MarkdownPreview'

export default function InterviewDocumentEditor({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  const [preview, setPreview] = useState(false)
  const contentId = useId()
  const editorRef = useRef<HTMLTextAreaElement>(null)
  const previewRef = useRef<HTMLDivElement>(null)
  const scrollProgress = useRef(0)

  function switchMode(next: boolean) {
    if (next === preview) return
    const current = preview ? previewRef.current! : editorRef.current!
    scrollProgress.current = current.scrollTop / Math.max(1, current.scrollHeight - current.clientHeight)
    setPreview(next)
  }

  useLayoutEffect(() => {
    const current = preview ? previewRef.current! : editorRef.current!
    current.scrollTop = scrollProgress.current * Math.max(0, current.scrollHeight - current.clientHeight)
  }, [preview])
  return <div className="interview-document-editor">
    <div className="interview-content-heading"><div><h3>面经内容</h3><p className="interview-document-note">记录这次面试的过程与复盘，支持 Markdown。</p></div><div className="interview-markdown-mode" role="group" aria-label="面经内容模式"><button type="button" aria-pressed={!preview} aria-controls={contentId} onClick={() => switchMode(false)}>编辑</button><button type="button" aria-label="预览 Markdown" aria-pressed={preview} aria-controls={contentId} onClick={() => switchMode(true)}>预览</button></div></div>
    <div id={contentId} className="interview-markdown-content interview-markdown-frame"><label hidden={preview}><span className="sr-only">面经正文</span><textarea ref={editorRef} rows={14} value={value} placeholder="自由记录这次面试的过程、问题、回答与复盘，支持 Markdown…" onChange={event => onChange(event.target.value)} /></label><div ref={previewRef} hidden={!preview} tabIndex={0} role="region" aria-label="面经预览" className="interview-markdown-preview">{preview && <>{value.trim() ? <MarkdownPreview value={value} /> : <p className="muted-note">还没有内容，切回编辑开始记录。</p>}</>}</div></div>
  </div>
}
