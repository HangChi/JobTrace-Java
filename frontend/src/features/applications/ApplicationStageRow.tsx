import { useState } from 'react'
import type { FormEvent } from 'react'
import type { StageOccurrence } from './api'

const stages = [['screening', '筛选'], ['assessment', '测评'], ['written_test', '笔试'], ['interview_1', '一面'], ['interview_2', '二面'], ['interview_3', '三面'], ['hr_interview', 'HR 面'], ['final_interview', '终面']]

export default function ApplicationStageRow({ item, minimumDate, busy, onSave, onDelete }: { item: StageOccurrence; minimumDate: string; busy: boolean; onSave: (stage: string, occurredOn: string) => void; onDelete: () => void }) {
  const [editing, setEditing] = useState(false)
  const [stage, setStage] = useState(item.stage)
  const [date, setDate] = useState(item.occurredOn)

  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onSave(stage, date)
    setEditing(false)
  }

  return <li>{editing ? <form className="stage-row-form" onSubmit={save}><label>阶段<select value={stage} onChange={event => setStage(event.target.value)}>{stages.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label><label>日期<input type="date" min={minimumDate} value={date} onChange={event => setDate(event.target.value)} required /></label><button disabled={busy} type="submit">保存阶段</button><button type="button" className="quiet-button" onClick={() => setEditing(false)}>取消</button></form> : <><span>{stages.find(([value]) => value === item.stage)?.[1] ?? item.stage} · {item.occurredOn}</span><span className="stage-row-actions"><button className="quiet-button" disabled={busy} onClick={() => setEditing(true)}>编辑</button><button className="quiet-button" disabled={busy} onClick={onDelete}>删除</button></span></>}</li>
}
