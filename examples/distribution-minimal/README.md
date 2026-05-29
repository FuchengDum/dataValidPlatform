# Minimal CLI Distribution Example

This directory is a portable sample for release packages. It contains only the assets needed to run the CLI:

- `validator.yml`: main CLI config.
- `source.yml`: inline sample data.
- `rules.yml`: generic rule package.
- `expected-result.md`: expected outcome and exit codes.

From the repository root after building the jar:

```bash
bin/data-validator lint --config examples/distribution-minimal/validator.yml
bin/data-validator run --config examples/distribution-minimal/validator.yml
```

From a release package, keep the same layout:

```text
bin/data-validator
backend/target/data-validator-0.1.0.jar
examples/distribution-minimal/
```

The sample writes generated reports to `reports/` at runtime. Generated reports are intentionally not included in source control.
