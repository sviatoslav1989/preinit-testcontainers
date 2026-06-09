#!/usr/bin/env python3
"""Generate PostgreSQL init DDL/DML fixtures with configurable table and row counts."""

import argparse
import random
import string
from pathlib import Path


def escape_sql_string(value: str) -> str:
    return value.replace("'", "''")


def random_alphanumeric(length: int, rng: random.Random) -> str:
    alphabet = string.ascii_lowercase + string.digits
    return "".join(rng.choice(alphabet) for _ in range(length))


def generate_ddl(table_name: str) -> str:
    return f"""CREATE TABLE {table_name}
(
    id INT NOT NULL,
    col1 VARCHAR(64) NOT NULL,
    col2 VARCHAR(64) NOT NULL,
    col3 VARCHAR(64) NOT NULL,
    col4 VARCHAR(64) NOT NULL,
    col5 VARCHAR(64) NOT NULL,
    PRIMARY KEY (id)
);"""


def generate_insert(table_name: str, row_id: int, rng: random.Random) -> str:
    cols = [random_alphanumeric(rng.randint(8, 32), rng) for _ in range(5)]
    values = ", ".join(f"'{escape_sql_string(col)}'" for col in cols)
    return (
        f"INSERT INTO {table_name} (id, col1, col2, col3, col4, col5)\n"
        f"VALUES ({row_id}, {values});"
    )


def table_name(index: int) -> str:
    return f"table_{index:03d}"


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate PostgreSQL init SQL fixtures.")
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("modules/postgresql/src/test/resources/postgresql"),
        help="Directory for output SQL files",
    )
    parser.add_argument("--tables", type=int, default=100, help="Number of tables to generate")
    parser.add_argument(
        "--rows-per-table", type=int, default=20, help="Number of rows per table"
    )
    parser.add_argument("--seed", type=int, default=42, help="Random seed for reproducible output")
    parser.add_argument(
        "--suffix",
        type=str,
        default="100",
        help="Filename suffix (e.g. 100 -> init.tables.expected100.sql)",
    )
    args = parser.parse_args()

    rng = random.Random(args.seed)

    ddl_lines: list[str] = []
    dml_lines: list[str] = []

    for i in range(1, args.tables + 1):
        name = table_name(i)
        ddl_lines.append(generate_ddl(name))
        for row_id in range(1, args.rows_per_table + 1):
            dml_lines.append(generate_insert(name, row_id, rng))

    tables_file = args.output_dir / f"init.tables.expected{args.suffix}.sql"
    data_file = args.output_dir / f"init.data.expected{args.suffix}.sql"

    args.output_dir.mkdir(parents=True, exist_ok=True)
    tables_file.write_text("\n\n".join(ddl_lines) + "\n", encoding="utf-8")
    data_file.write_text("\n".join(dml_lines) + "\n", encoding="utf-8")

    print(f"Wrote {args.tables} CREATE TABLE statements to {tables_file}")
    print(f"Wrote {args.tables * args.rows_per_table} INSERT statements to {data_file}")
    print(f"Seed: {args.seed}")


if __name__ == "__main__":
    main()
