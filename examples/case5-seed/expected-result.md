# Case5 Seed CLI Expected Result

This sample runs the 30 Case5 rules against the V3 seed business tables.

Commands:

```bash
bin/data-validator lint --config examples/case5-seed/validator.yml
bin/data-validator run --config examples/case5-seed/validator.yml --json --no-report
```

Expected behavior:

- `lint` exits with `0`.
- `run` executes 30 rules.
- The source summary contains 5 tables and 70 rows.
- The current seed produces 68 findings: 60 critical and 8 warning.
- The sample data contains critical findings, so `run` exits with `2`.

The automated tests verify that the CLI Case5 YAML package stays in sync with the packaged Case5 resource. Web Excel import uses built-in template bindings instead of this YAML package.
