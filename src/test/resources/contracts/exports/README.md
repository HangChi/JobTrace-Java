# Synthetic legacy export oracle

Source of truth is the current runtime in `JobTrace`:
`src/app/api/exports/{applications,interviews}/route.ts`,
`src/modules/data-transfer/application/export-{applications,interviews}.ts`,
`src/modules/data-transfer/infrastructure/{spreadsheet-writer,zip-writer}.ts`,
and `src/modules/interviews/application/interview-markdown.ts`.
The fixtures here contain invented people, companies and review text only.

Application export defaults to `scope=filtered` and `format=xlsx`; unknown values
use the same fallbacks. `all` ignores filters, `filtered` applies search, repeated
status/type/stage/city and inclusive date bounds, and `selected` uses provided IDs
(at most 100, at least one). SQL always binds the signed user's owner ID. Rows sort
by application date descending and ID ascending. Stage history sorts by occurrence
date, creation time and ID ascending. Empty scope is `not_found` (404).

Columns, in order: ID, 公司, 岗位, 城市, 职位链接, 投递日期, 类型, 状态, 最新日期, 阶段历史, 备注, 创建时间, 更新时间.
CSV has a UTF-8 BOM and quote-doubles quotes inside cells. Both formats prefix
strings starting `=`, `+`, `-`, `@`, tab or carriage return with an apostrophe.
Only HTTP(S) URLs become XLSX hyperlinks. The existing application route lacks an
explicit cache policy; 007 deliberately strengthens it to `private, no-store`.

Interview export requires 1–100 valid IDs, removes duplicates while keeping first
occurrence order and omits non-owned IDs. No owned result is `not_found` (404).
One resulting review is Markdown; multiple reviews form a ZIP of Markdown files.
Filenames sanitize unsafe characters, use company/position/stage/duration, and
suffix collisions. Plain one-question content is returned unchanged; structured
reviews use the established Markdown headings. Interview downloads are already
`private, no-store`. Tests inspect text and binary structure, not ZIP/XLSX byte
identity (archive timestamps and metadata may vary).
