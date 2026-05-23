# Expected Result

The sample intentionally contains one critical finding.

`B002.receivable_amount` is `950`, while the rule expects
`contract_amount - discount_amount = 920`.

Expected CLI exit code for `run`: `2`.
Expected CLI exit code for `lint`: `0`.
