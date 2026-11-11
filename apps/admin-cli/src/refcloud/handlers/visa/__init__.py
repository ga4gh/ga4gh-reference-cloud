import click

# access request statuses (the postgres enum passport_visa_assertion_status). Expired and NotRequested
# exist in the enum but are never set by the admin CLI
REQUESTED = "Requested"
APPROVED = "Approved"
DENIED = "Denied"
REVOKED = "Revoked"
REQUEST_STATUSES = ["NotRequested", REQUESTED, APPROVED, DENIED, REVOKED, "Expired"]


def echo_table(headers, rows):
    """Print rows as a left-aligned, space-padded table"""
    cells = [[("" if value is None else str(value)) for value in row] for row in rows]
    widths = [max(len(header), *(len(row[i]) for row in cells)) for i, header in enumerate(headers)]
    click.echo("  ".join(header.ljust(width) for header, width in zip(headers, widths, strict=True)).rstrip())
    click.echo("  ".join("-" * width for width in widths))
    for row in cells:
        click.echo("  ".join(value.ljust(width) for value, width in zip(row, widths, strict=True)).rstrip())
