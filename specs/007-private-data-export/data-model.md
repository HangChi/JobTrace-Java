# Data Model: Private Data Export

This feature creates no persistent entity, migration, or state transition. All objects below are immutable, short-lived read projections or download results. The existing service remains the only writer of applications, stages, interviews, questions, action items, and import batches.

## ApplicationExportSelection

| Field | Meaning and validation |
| --- | --- |
| `scope` | `all`, `filtered`, or `selected`; omitted/unknown defaults to `filtered` |
| `format` | `csv` or `xlsx`; omitted/unknown defaults to `xlsx` |
| `ids` | Zero to 100 valid UUIDs at request parsing; `selected` requires at least one |
| `q` | Optional search text, at most the current 200-character input slice |
| `status`, `type`, `stage`, `city` | Repeated filter values; applied only for `filtered` |
| `appliedFrom`, `appliedTo` | Optional inclusive application-date bounds; applied only for `filtered` |

An authenticated owner is supplied separately by the trusted bridge, never by this selection. The selection cannot expand owner scope. All/selected ignore filtered criteria.

## ApplicationExportRow

One owner-bound application with these output columns in order: `ID`, `公司`, `岗位`, `城市`, `职位链接`, `投递日期`, `类型`, `状态`, `最新日期`, `阶段历史`, `备注`, `创建时间`, `更新时间`. Nullable city, link and notes become empty cells. Type, status and stage labels use the established Chinese display values; stage history is a semicolon-separated sequence of code/label/date tokens. Application rows are ordered by application date descending, then identifier ascending. Stage history is ordered by occurrence date, creation time, and occurrence identifier ascending.

The text serialization layer quotes CSV delimiters/newlines and prefixes formula-like text; XLSX stores literal values and safe HTTP(S) links only. ID and system timestamps remain audit columns and do not grant import identity.

## InterviewExportSelection

An ordered list of 1–100 valid UUIDs. Duplicate IDs are removed while keeping first occurrence order. Owner resolution may yield fewer reviews than requested; zero owned results produces the same safe not-found outcome as the current service. The selection is not an owner claim.

## InterviewExportDocument

One owner-bound review with owner-consistent application names, stored-stage fallback if a stage link is missing, ordered questions and action items, and the text fields used by the current Markdown writer. Its preferred filename combines sanitized company, position, stage and duration. For collisions, a stable numeric suffix precedes `.md`. Content is UTF-8; a plain single-question review retains its direct question text, while structured reviews use the established headings and section order.

## ExportDownload

| Case | Body | Attachment metadata |
| --- | --- | --- |
| Application CSV | UTF-8 BOM plus CSV | CSV media type and dated filename |
| Application XLSX | Workbook bytes | Spreadsheet media type and dated filename |
| One owned interview | UTF-8 Markdown | Markdown media type and sanitized descriptive filename |
| Multiple owned interviews | ZIP with one Markdown entry per review | ZIP media type and dated filename |

Successful downloads are private and non-cacheable. Errors use the project's safe problem shape and request ID. No download result is persisted by Java.
