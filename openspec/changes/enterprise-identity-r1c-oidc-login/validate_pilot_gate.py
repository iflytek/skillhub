"""Validate the R1-C one-time simulated pilot gate fixture."""

import json
from datetime import datetime
from pathlib import Path


ROOT = Path(__file__).parent
fixture = json.loads((ROOT / "pilot-gate.simulation.json").read_text())

assert fixture["simulation"] is True
assert fixture["realExternalPilot"] is False
organization = fixture["organization"]
assert organization["slug"].startswith("sim-")
assert organization["owner"].startswith("sim-")
assert organization["members"]
assert organization["loginConnection"].startswith("sim-")
window = fixture["window"]
assert datetime.fromisoformat(window["start"]) < datetime.fromisoformat(window["end"])
checks = fixture["checks"]
for name in ("unauthorizedSession", "suspendedConnectionSession", "sensitiveLogScan", "crossOrganizationBinding"):
    assert checks[name]["status"] == "passed"
assert checks["failureRate"]["failed"] == 0
assert checks["failureRate"]["total"] > 0
assert checks["sensitiveLogScan"]["matches"] == 0
assert checks["crossOrganizationBinding"]["bound"] is False
assert checks["ownerStopRequest"]["triggered"] is False
assert fixture["conclusion"] == "SIMULATED_PASS"
print("R1-C simulated pilot gate: passed")
