"""Compare the isolated harness observations without treating old mod bugs as new regressions."""
import argparse
import json
from pathlib import Path
import re

def normalize(row):
    result = dict(row)
    # Timing is measured, not asserted equal. JVM identity and helpful-NPE local names are not stable.
    result.pop('observedMs', None)
    if 'error' in result:
        result['error'] = re.sub(r"of loader 'TRANSFORMER' @[0-9a-f]+", "of loader 'TRANSFORMER' @INSTANCE", result['error'])
        result['error'] = re.sub(r'because "(?:level|<local2>)" is null', 'because "DIMENSION_LOCAL" is null', result['error'])
    return result

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('root', type=Path)
    args = p.parse_args()
    records = [json.loads((args.root / label / 'audit-results.json').read_text(encoding='utf-8')) for label in ['original', 'rebuilt']]
    differences = [dict(index=i, original=a, rebuilt=b) for i, (a,b) in enumerate(zip(*records)) if normalize(a) != normalize(b)]
    exits = [(args.root / label / 'exit-code.txt').read_text().strip() for label in ['original', 'rebuilt']]
    complete = all(any(r['test']=='ab_complete' and r['status']=='DONE' for r in rows) for rows in records)
    harness_errors = [r for rows in records for r in rows if r['status']=='HARNESS_ERROR']
    passed = len(records[0]) == len(records[1]) and not differences and exits == ['0', '0'] and complete and not harness_errors
    result = dict(comparison_passed=passed, record_counts=list(map(len, records)), exit_codes=exits,
                  complete=complete, harness_errors=harness_errors, differences=differences,
                  ignored=['observedMs', 'TRANSFORMER instance identity', 'helpful NPE local name: level vs <local2>'],
                  scope='Short server-side observed behavior only; not universal equivalence or client gameplay.')
    (args.root / 'comparison.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(result, ensure_ascii=False))
    return 0 if passed else 1

if __name__ == '__main__':
    raise SystemExit(main())
