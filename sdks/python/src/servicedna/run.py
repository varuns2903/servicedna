"""servicedna-run: run a Python program with ServiceDNA telemetry, e.g.

    SERVICEDNA_URL=... SERVICEDNA_KEY=sdna_ik_... servicedna-run uvicorn app.main:app
"""

from __future__ import annotations

import os
import sys

from . import config as _config


def main() -> None:
    cfg = _config.resolve()
    if not cfg.enabled:
        print("[servicedna] SERVICEDNA_URL and SERVICEDNA_KEY are not set; not sending telemetry", file=sys.stderr)
    else:
        for key, value in _config.otel_environment(cfg).items():
            os.environ.setdefault(key, value)
        print(
            f"[servicedna] sending {cfg.service_name}"
            + (f" ({cfg.environment})" if cfg.environment else "")
            + f" telemetry to {cfg.url}",
            file=sys.stderr,
        )

    # Same mechanism as opentelemetry-instrument: re-executes the command with auto-instrumentation
    # loaded via sitecustomize, which picks up the servicedna distro and configurator.
    from opentelemetry.instrumentation.auto_instrumentation import run

    run()
