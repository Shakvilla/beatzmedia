#!/usr/bin/env python3
"""
sms-capture-stub — the in-repo SMS capture stub referenced by notifications ADD §4.2/§5.2 and
PRD OQ-9. Dev/test-only "MailHog-for-SMS": accepts POST /send (the HttpSmsSender payload shape),
stores messages in memory, and exposes a tiny read UI/API to inspect what was "sent" — analogous
to Mailpit for email, but zero external dependencies (stdlib only, no pip install needed).

NEVER used in prod (docker-compose.yml only starts this in the dev/local stack; prod points
beatz.sms.endpoint at a real provider's HTTP API instead — config/secrets human deploy gate).

Endpoints
---------
POST /send            Accepts {"to": str, "body": str, "idempotencyKey": str}. Stores the message,
                       de-duplicating by idempotencyKey (a resend with the same key does not create
                       a second stored message — mirrors real provider idempotency-key support so
                       HttpSmsSender's retry-dedup story is exercised end-to-end in dev). Returns
                       200 {"status": "queued", "id": "<n>"}.
GET  /messages         Returns the JSON array of all captured messages (newest first).
DELETE /messages       Clears the in-memory store (useful between manual test runs).
GET  /livez            Liveness for the Compose healthcheck.
GET  /                 Minimal HTML UI listing captured messages (auto-refreshing), analogous to
                       Mailpit's web UI.
"""

import json
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

_LOCK = threading.Lock()
_MESSAGES = []  # list of dicts, newest first
_SEEN_KEYS = set()  # idempotencyKey values already stored (send-idempotency dedup)
_NEXT_ID = 1


def _store(payload):
    global _NEXT_ID
    with _LOCK:
        key = payload.get("idempotencyKey")
        if key and key in _SEEN_KEYS:
            return None  # dedup: same idempotency key already stored once
        msg_id = _NEXT_ID
        _NEXT_ID += 1
        if key:
            _SEEN_KEYS.add(key)
        record = {
            "id": msg_id,
            "to": payload.get("to"),
            "body": payload.get("body"),
            "idempotencyKey": key,
        }
        _MESSAGES.insert(0, record)
        return record


class Handler(BaseHTTPRequestHandler):
    server_version = "sms-capture-stub/1.0"

    def log_message(self, fmt, *args):  # quieter default logging; no PII in access logs beyond path
        pass

    def _json(self, status, obj):
        body = json.dumps(obj).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        path = urlparse(self.path).path
        if path == "/livez":
            self._json(200, {"status": "ok"})
        elif path == "/messages":
            with _LOCK:
                self._json(200, list(_MESSAGES))
        elif path == "/":
            self._html_index()
        else:
            self._json(404, {"error": "not found"})

    def do_POST(self):
        path = urlparse(self.path).path
        if path != "/send":
            self._json(404, {"error": "not found"})
            return
        length = int(self.headers.get("Content-Length", "0") or "0")
        raw = self.rfile.read(length) if length else b"{}"
        try:
            payload = json.loads(raw or b"{}")
        except json.JSONDecodeError:
            self._json(400, {"error": "invalid json"})
            return
        record = _store(payload)
        if record is None:
            self._json(200, {"status": "duplicate-ignored"})
            return
        self._json(200, {"status": "queued", "id": str(record["id"])})

    def do_DELETE(self):
        path = urlparse(self.path).path
        if path == "/messages":
            with _LOCK:
                _MESSAGES.clear()
                _SEEN_KEYS.clear()
            self._json(200, {"status": "cleared"})
        else:
            self._json(404, {"error": "not found"})

    def _html_index(self):
        with _LOCK:
            rows = "".join(
                "<tr><td>{id}</td><td>{to}</td><td>{body}</td><td>{key}</td></tr>".format(
                    id=m["id"],
                    to=_escape(m["to"]),
                    body=_escape(m["body"]),
                    key=_escape(m.get("idempotencyKey") or ""),
                )
                for m in _MESSAGES
            )
        html = (
            "<html><head><title>SMS capture stub</title>"
            "<meta http-equiv='refresh' content='5'></head><body>"
            "<h1>SMS capture stub (dev only)</h1>"
            "<table border='1' cellpadding='4'>"
            "<tr><th>id</th><th>to</th><th>body</th><th>idempotencyKey</th></tr>"
            + rows
            + "</table></body></html>"
        )
        body = html.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


def _escape(value):
    return (
        str(value)
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
    )


if __name__ == "__main__":
    server = ThreadingHTTPServer(("0.0.0.0", 8026), Handler)
    print("sms-capture-stub listening on :8026")
    server.serve_forever()
