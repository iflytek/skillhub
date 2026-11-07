# Text statistics example Skill

This package demonstrates a SKILL.md, a helper script, reference material and
a validation.yaml with four behavior tests. It can be inspected and run locally
with a POSIX shell and Python 3.

From this directory, run:

```sh
sh scripts/text-stats.sh references/sample.txt
```

The helper emits one JSON object to stdout and saves the same JSON to
artifacts/report.json. The sample contains 2 newlines, 20 whitespace-separated
words and 144 bytes. Filenames are JSON-escaped.

## Validation cases

| Task | Expected result |
| --- | --- |
| fixture-stats | Exit 0, expected counters and one script invocation |
| output-shape | One JSON object with the documented fields |
| report-artifact | artifacts/report.json exists after execution |
| rejects-missing-file | Exit 2 when the input is missing |

The declaration exercises exit_code, stdout_contains, stdout_json,
stdout_matches, artifact_exists and tool_call_count assertions. The runtime
executing it must provide sh and python3.

The upstream draft API currently supports storing these files and checking
package structure. Executing validation.yaml requires the authoring execution
layer tracked in [#1003](https://github.com/iflytek/skillhub/pull/1003).
The script command above can be used independently to reproduce the fixture
counters. A structure check alone does not run these behavior assertions.

## Files

- SKILL.md describes when and how to use the skill.
- scripts/text-stats.sh implements the statistics operation.
- references/metrics.md defines the counters.
- references/sample.txt supplies deterministic input.
- validation.yaml describes behavior checks.

Run the helper in a disposable workspace when checking error cases.
It writes the report relative to its working directory. A failed invocation
leaves any earlier report in place, so use its exit status to determine success.
