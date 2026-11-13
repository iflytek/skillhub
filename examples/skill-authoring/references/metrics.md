# Metric definitions

The Python 3 helper counts bytes and ASCII whitespace-separated tokens.

| Metric       | Definition                                                       |
| ------------ | ---------------------------------------------------------------- |
| `lines`      | Newline characters in the input (`wc -l`). A file whose last line has no trailing newline counts one line less. |
| `words`      | Tokens separated by ASCII whitespace.                           |
| `characters` | Bytes in the file (`wc -c`), so a multi-byte UTF-8 character counts once per byte. |

The JSON object is emitted as a single line on stdout and mirrored to
`artifacts/report.json`. Invalid usage (wrong argument count or a missing
file) exits with code 2 and writes no new report, so a caller can distinguish
"computed" from "refused" without parsing error text.
