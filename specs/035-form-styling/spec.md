# Feature Specification: Job Market and Application Form Styling

**Status**: Implemented locally

## Scope

Improve the search forms on Recruitment Market and My Applications, the application editor, and the application list layout. Preserve the current filters, application actions, API contracts, and backend behavior.

## Requirements

- Give both search forms a clear heading, consistently sized controls, a prominent primary action, and a calm secondary reset action.
- Keep filter fields in sync with URL changes, and clear draft values when the user resets a form.
- Group application entry fields by position, progress, and notes without changing what is saved.
- Give the application selection column its own width so dates remain legible. Present all row fields as readable cards on narrow screens.
- Preserve keyboard focus, labels, responsive layout, and reduced-motion behavior.

## Verification

Run frontend lint, tests, build, coverage, and performance checks. Inspect both pages and the editor at desktop and phone widths using mocked data. No production account or live database is needed for visual inspection.
