# MySQL Main Path Regression Validation

Validated on `2026-05-04` for `US-103`.

## Runtime

- Backend: `http://127.0.0.1:19180`
- Web: `http://127.0.0.1:13100`
- Mock UASS: `http://127.0.0.1:13101/mock-uass`
- Database: `jdbc:mysql://127.0.0.1:33306/skillhub_us103_regression`
- Search provider: `local-file-index`
- Runtime state provider: `memory`

## Results

- Health check passed: `GET /actuator/health` returned `{"status":"UP"}`.
- Local account path passed: opened `/login`, verified the password form, established a session for `admin / ChangeMe!2026`, and confirmed `/dashboard` shows `Local Admin`, `local-admin`, and `Logged in via local`.
- Mock UASS redirect path passed: triggered enterprise login from `/login`, reached `http://127.0.0.1:13101/mock-uass`, completed login as `uass-admin-003`, and returned to `/dashboard` with `Logged in via uass` and `SUPER_ADMIN`.
- Publish-related browser verification passed: `/dashboard/publish` loaded correctly, namespace selection and zip upload were available, and a real global publish for `us103-browser-global@1.0.0` was confirmed on `/dashboard/skills`.
- Search flow passed: `/search?q=mysql-runtime-fixture&sort=relevance&page=0&starredOnly=false` returned `Local MySQL Search Fixture`, and switching to `sort=downloads` kept the page healthy.
- Review flow passed: `/dashboard/reviews` showed the pending task for `us103-review-team-1777886817/us103-review-user-skill@1.0.0`, and `/dashboard/reviews/1` rendered the detail page with approve/reject actions and review-version content.

## Notes

- The current frontend review-management entry is `/dashboard/reviews`. Opening `/admin/reviews` in this checkout returns `Not Found`.
- `agent-browser` needed browser-context request fallbacks for password login, mock UASS submit, and the final publish submit on the hidden file input path. The runtime endpoints themselves returned successful responses, and the authenticated/published end states were confirmed in the UI.
