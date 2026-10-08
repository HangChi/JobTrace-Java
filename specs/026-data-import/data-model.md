# Data Model: Application Data Import

## Import batch

Existing `import_batches` columns: `id`, `owner_id`, `status`, `file_name`, `format`, `columns`, counts, `created_at`, `expires_at`, and `completed_at`. Status moves from `previewed` to `processing` to `completed`, or from `previewed` to `expired`. Only the owner may replace or confirm a preview.

## Import row

Existing `import_rows` columns: `batch_id`, `row_number`, `normalized_data`, `errors`, `duplicate_application_ids`, `decision`, `result`, and `application_id`. A preview row has either validated normalized data or nonempty errors. Confirmation marks every row `created`, `skipped` or `failed`; invalid rows always skip.

## Application relationship

The current `create_application_for_owner` function creates each selected valid application and its `imported` event. The confirmation function isolates per-row business failures. Existing application export remains unchanged and can supply a compatible spreadsheet for import.
