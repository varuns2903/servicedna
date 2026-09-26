"""ServiceDNA SDK for Python.

Run a program with telemetry (recommended — instruments libraries before they're imported):

    servicedna-run uvicorn app.main:app

Or start it from code, first thing in your entry point:

    import servicedna
    servicedna.start()
"""

from __future__ import annotations

import logging
import os

from . import config as _config
from .bodies import capture, tag

__all__ = ["start", "capture", "tag"]
__version__ = "0.1.0"

log = logging.getLogger("servicedna")


def start() -> bool:
    """Configures tracing, instruments installed libraries, and starts heartbeats. Returns False
    (and does nothing) when SERVICEDNA_URL and SERVICEDNA_KEY aren't set."""
    cfg = _config.resolve()
    if not cfg.enabled:
        log.warning("[servicedna] SERVICEDNA_URL and SERVICEDNA_KEY are not set; not sending telemetry")
        return False
    for key, value in _config.otel_environment(cfg).items():
        os.environ.setdefault(key, value)

    from opentelemetry.instrumentation.auto_instrumentation import initialize

    initialize()  # loads the servicedna distro (which starts the heartbeat) and configurator
    return True
