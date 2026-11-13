"""Run a SIGKILL recovery drill against a bootJar, using a fresh journal."""

import argparse
from concurrent.futures import ThreadPoolExecutor
import json
from pathlib import Path
import signal
import subprocess
import tempfile
import time
from urllib.error import URLError
from urllib.request import Request, urlopen


def drill(java, jar, port):
    base = f"http://127.0.0.1:{port}"

    def request(path, payload=None, method="GET"):
        headers = {"Content-Type": "application/json"} if payload is not None else {}
        data = json.dumps(payload).encode() if payload is not None else None
        with urlopen(Request(base + path, data, headers, method=method), timeout=8) as response:
            return json.load(response)

    with tempfile.TemporaryDirectory(prefix="trading-recovery-") as folder:
        directory = Path(folder)
        journal = directory / "journal.log"

        def start(log_name):
            with (directory / log_name).open("wb") as log:
                process = subprocess.Popen(
                    [java, "-jar", str(jar.resolve()), "--server.address=127.0.0.1",
                     f"--server.port={port}", f"--trading.journal-path={journal}",
                     "--trading.journal-fsync=GROUP"], stdout=log, stderr=log,
                )
            for _ in range(100):
                if process.poll() is not None:
                    break
                try:
                    if request("/actuator/health")["status"] == "UP":
                        return process
                except (OSError, URLError):
                    pass
                time.sleep(0.1)
            process.kill()
            process.wait()
            raise RuntimeError((directory / log_name).read_text())

        process = start("first-start.log")
        try:
            def place(index):
                side = "SELL" if index % 2 == 0 else "BUY"
                client = "A" if side == "SELL" else "B"
                order = {"clientId": client, "side": side, "price": 10000, "quantity": 1,
                         "clientOrderId": f"crash-{index}"}
                result = request("/api/orders", order, "POST")
                if result["outcome"] != "SUCCESS" or result["duplicate"]:
                    raise RuntimeError(result)
                return order, result["order"]

            with ThreadPoolExecutor(max_workers=16) as pool:
                acknowledged = list(pool.map(place, range(64)))
            accounts = request("/api/accounts")
            process.kill()
            if process.wait(timeout=10) != -signal.SIGKILL:
                raise RuntimeError("Process did not exit via SIGKILL")
        finally:
            if process.poll() is None:
                process.terminate()
                process.wait(timeout=10)

        started_at = time.monotonic()
        process = start("restart.log")
        ready_ms = round((time.monotonic() - started_at) * 1000)
        try:
            ids = {order["orderId"] for _, order in acknowledged}
            if len(ids) != len(acknowledged):
                raise RuntimeError("Acknowledged orders share an ID")
            for payload, order in acknowledged:
                replayed = request("/api/orders", payload, "POST")
                if not replayed["duplicate"] or replayed["order"] != order:
                    raise RuntimeError(f"Original order was not recovered: {order['orderId']}")
                if request(f"/api/orders/{order['orderId']}")["orderId"] != order["orderId"]:
                    raise RuntimeError(f"Recovered order cannot be queried: {order['orderId']}")
            if request("/api/accounts") != accounts:
                raise RuntimeError("Account balances changed after recovery")
            recon = request("/api/recon/run", method="POST")
            if recon["report"]["anomalies"]:
                raise RuntimeError(recon)
            lines = len(journal.read_bytes().splitlines())
            if lines != 24 + len(acknowledged):
                raise RuntimeError(f"Unexpected journal record count: {lines}")
            return {"strategy": "GROUP", "workers": 16, "acknowledged": 64,
                    "recovered": 64, "idempotentRetries": 64, "journalRecords": lines,
                    "reconciliationAnomalies": 0, "restartToReadyMs": ready_ms}
        finally:
            process.terminate()
            process.wait(timeout=15)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--java", default="java")
    parser.add_argument("--jar", type=Path, default=Path("app/build/libs/app-0.1.0-SNAPSHOT.jar"))
    parser.add_argument("--port", type=int, default=18127)
    args = parser.parse_args()
    print(json.dumps(drill(args.java, args.jar, args.port), indent=2))
