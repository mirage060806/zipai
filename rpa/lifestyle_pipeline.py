"""공식 생활 CSV를 정규화한 뒤 DB에 반영하고 실행 기록을 남깁니다."""
import argparse
import os
from datetime import date
from pathlib import Path
from rpa_runtime import load_env, start_run, execute


def main():
    load_env()
    parser = argparse.ArgumentParser()
    parser.add_argument('--trigger', choices=['manual','scheduled','unknown'], default='unknown')
    args = parser.parse_args()
    run = start_run('lifestyle', args.trigger, 'build_import')
    def action():
        import lifestyle_public_data_builder as builder
        from lifestyle_score_importer import import_csv, DEFAULT_CSV
        run.emit(stage='collect')
        source_date = os.getenv("LIFESTYLE_SOURCE_DATE", "").strip()
        if not source_date:
            raise ValueError("LIFESTYLE_SOURCE_DATE에 원본 자료 기준일(YYYY-MM-DD)을 지정하세요.")
        date.fromisoformat(source_date)
        builder.build()
        run.emit(stage='upload')
        imported = import_csv(Path(os.getenv('LIFESTYLE_SCORE_CSV', DEFAULT_CSV)))
        run.emit(collected=imported)
        return 0
    return execute(run, action)


if __name__ == '__main__':
    raise SystemExit(main())
