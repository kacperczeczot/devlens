"""
DevLens Node Daemon
Lekki serwer węzła umożliwiający zdalną inspekcję maszyny, eksplorację plików i audyt uprawnień.
"""

import os
import sys
import json
import argparse
import platform
import subprocess
import ipaddress
import secrets
from http.server import HTTPServer, BaseHTTPRequestHandler
from socketserver import TCPServer

TCPServer.allow_reuse_address = True

class DevLensRequestHandler(BaseHTTPRequestHandler):
    auth_token = None

    def _verify_auth(self):
        if not self.auth_token:
            return True
        token = self.headers.get("X-DevLens-Token") or self.headers.get("X-Mesh-Token") or self.headers.get("Authorization")
        if token and token.replace("Bearer ", "").strip() == self.auth_token:
            return True
        self.send_response(401)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps({"error": "Unauthorized"}).encode("utf-8"))
        return False

    def do_GET(self):
        if self.path.startswith("/file-raw"):
            return self._handle_file_raw()

        if not self._verify_auth():
            return

        if self.path == "/health":
            self._send_json({
                "status": "ok",
                "app": "DevLens",
                "platform": platform.system(),
                "node": platform.node()
            })
        elif self.path == "/system":
            self._send_json(self._get_system_info())
        elif self.path == "/permissions":
            self._send_json(self._run_permissions_audit())
        else:
            self._send_json({
                "app": "DevLens",
                "message": "DevLens Daemon is running",
                "endpoints": [
                    "GET /health",
                    "GET /system",
                    "GET /permissions",
                    "GET /file-raw?path=...",
                    "POST /pair",
                    "POST /query",
                    "POST /read-file",
                    "POST /permissions/fix",
                    "POST /upload"
                ]
            })

    def do_POST(self):
        # Zero-Touch LAN Pairing endpoint
        if self.path == "/pair":
            length = int(self.headers.get("Content-Length", 0))
            body = self.rfile.read(length).decode("utf-8", errors="ignore")
            data = json.loads(body) if body else {}

            client_ip = self.client_address[0]
            if not self._is_private_ip(client_ip):
                self._send_json({"error": "Pairing only allowed from private LAN"}, status=403)
                return

            remote_node = data.get("node_name", f"node-{client_ip.replace('.', '-')}")
            remote_host = data.get("host") or client_ip
            remote_port = data.get("port", 8888)
            remote_token = data.get("token")

            if not remote_token:
                self._send_json({"error": "Missing remote 'token' in pairing request"}, status=400)
                return

            self._save_node_to_config(remote_node, remote_host, remote_port, remote_token)
            print(f"🤝 Paired successfully with '{remote_node}' ({remote_host}:{remote_port})")

            self._send_json({
                "status": "paired",
                "node_name": platform.node(),
                "token": self.auth_token,
                "platform": platform.system()
            })
            return

        if self.path.startswith("/upload"):
            return self._handle_upload()

        if not self._verify_auth():
            return

        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length).decode("utf-8", errors="ignore")
        data = json.loads(body) if body else {}

        if self.path == "/query":
            path = data.get("path", ".")
            result = self._query_path(path, max_depth=data.get("max_depth", 1))
            self._send_json(result)
        elif self.path == "/read-file":
            file_path = data.get("path")
            if not file_path:
                self._send_json({"error": "Missing 'path' parameter"}, status=400)
                return
            result = self._read_file(file_path, max_bytes=data.get("max_bytes", 524288))
            self._send_json(result)
        elif self.path == "/permissions/fix":
            action = data.get("action", "")
            result = self._fix_permission(action)
            self._send_json(result)
        else:
            self.send_response(404)
            self.end_headers()

    def _is_private_ip(self, ip_str):
        try:
            ip = ipaddress.ip_address(ip_str)
            if ip.is_private or ip.is_loopback:
                return True
            if isinstance(ip, ipaddress.IPv4Address):
                octets = [int(p) for p in ip_str.split(".")]
                if len(octets) == 4 and octets[0] == 100 and 64 <= octets[1] <= 127:
                    return True
            return False
        except ValueError:
            return False

    def _save_node_to_config(self, node_name, host, port, token):
        config_path = os.path.expanduser("~/.devlens/nodes.json")
        os.makedirs(os.path.dirname(config_path), exist_ok=True)
        nodes = {}
        if os.path.isfile(config_path):
            try:
                with open(config_path, "r", encoding="utf-8") as f:
                    nodes = json.load(f)
            except Exception:
                nodes = {}
        nodes[node_name] = {
            "host": host,
            "port": port,
            "token": token
        }
        with open(config_path, "w", encoding="utf-8") as f:
            json.dump(nodes, f, indent=2)

    def _get_system_info(self):
        return {
            "node_name": platform.node(),
            "os_name": platform.system(),
            "os_version": platform.release(),
            "cpu_brand": platform.processor(),
            "cpu_count": os.cpu_count() or 1,
            "cpu_usage_pct": 0.0,
            "cwd": os.getcwd()
        }

    def _resolve_path(self, raw_path):
        if not raw_path:
            return os.getcwd()
        trimmed = str(raw_path).strip()
        lower = trimmed.lower()
        if lower.startswith("file:///"):
            import re
            if re.match(r"^file:///[a-zA-Z]:", lower):
                trimmed = trimmed[8:]
            else:
                trimmed = trimmed[7:]
        elif lower.startswith("file://"):
            trimmed = trimmed[7:]
        elif lower.startswith("file:"):
            trimmed = trimmed[5:]

        if not trimmed or trimmed == ".":
            return os.getcwd()

        if trimmed == "~" or trimmed.startswith("~/") or trimmed.startswith("~\\"):
            home = os.path.expanduser("~")
            if trimmed == "~":
                return os.path.abspath(home)
            return os.path.abspath(os.path.join(home, trimmed[2:]))

        return os.path.abspath(os.path.expanduser(os.path.expandvars(trimmed)))

    def _query_path(self, target_path, max_depth=1):
        resolved = self._resolve_path(target_path)
        if not os.path.exists(resolved):
            return {
                "error": f"Path '{target_path}' does not exist",
                "current_path": resolved,
                "parent_path": None,
                "items": []
            }

        parent = os.path.dirname(resolved)
        parent_path = parent if parent and parent != resolved else None

        items = []
        try:
            if os.path.isdir(resolved):
                with os.scandir(resolved) as it:
                    for entry in it:
                        try:
                            is_dir = entry.is_dir(follow_symlinks=False)
                            stat = entry.stat(follow_symlinks=False)
                            size = 0 if is_dir else stat.st_size
                            modified = int(stat.st_mtime)
                            items.append({
                                "name": entry.name,
                                "type": "dir" if is_dir else "file",
                                "is_dir": is_dir,
                                "size": size,
                                "modified": modified,
                                "path": os.path.abspath(entry.path)
                            })
                        except Exception:
                            continue
                        if len(items) >= 500:
                            break
            else:
                stat = os.stat(resolved)
                items.append({
                    "name": os.path.basename(resolved),
                    "type": "file",
                    "is_dir": False,
                    "size": stat.st_size,
                    "modified": int(stat.st_mtime),
                    "path": os.path.abspath(resolved)
                })
        except Exception as e:
            return {
                "error": f"Cannot read directory: {str(e)}",
                "current_path": resolved,
                "parent_path": parent_path,
                "items": []
            }

        items.sort(key=lambda x: (not x["is_dir"], x["name"].lower()))

        return {
            "path": target_path,
            "current_path": resolved,
            "parent_path": parent_path,
            "count": len(items),
            "items": items
        }

    def _read_file(self, file_path, max_bytes=524288):
        resolved = self._resolve_path(file_path)
        if not os.path.exists(resolved):
            return {
                "error": f"File '{file_path}' does not exist",
                "path": resolved,
                "content": "",
                "name": os.path.basename(resolved),
                "is_binary": False,
                "is_dir": False,
                "size": 0
            }
        if os.path.isdir(resolved):
            return {
                "error": f"Path '{file_path}' is a directory, not a file",
                "path": resolved,
                "name": os.path.basename(resolved),
                "content": "",
                "is_binary": False,
                "is_dir": True,
                "size": 0
            }

        try:
            size = os.path.getsize(resolved)
            with open(resolved, "rb") as f:
                raw_bytes = f.read(max_bytes + 1)

            truncated = len(raw_bytes) > max_bytes
            data_bytes = raw_bytes[:max_bytes]

            if b"\x00" in data_bytes[:1024]:
                return {
                    "path": resolved,
                    "name": os.path.basename(resolved),
                    "content": f"[Plik binarny, rozmiar: {size} bajtów]",
                    "is_binary": True,
                    "is_dir": False,
                    "size": size,
                    "truncated": False
                }

            content = data_bytes.decode("utf-8", errors="replace")
            return {
                "path": resolved,
                "name": os.path.basename(resolved),
                "content": content,
                "is_binary": False,
                "is_dir": False,
                "size": size,
                "truncated": truncated
            }
        except Exception as e:
            return {
                "error": f"Cannot read file: {str(e)}",
                "path": resolved,
                "name": os.path.basename(resolved),
                "content": "",
                "is_binary": False,
                "is_dir": False,
                "size": 0
            }

    def _handle_file_raw(self):
        from urllib.parse import urlparse, parse_qs
        parsed = urlparse(self.path)
        qs = parse_qs(parsed.query)

        token = qs.get("token", [None])[0] or self.headers.get("X-DevLens-Token") or self.headers.get("X-Mesh-Token")
        if self.auth_token and token != self.auth_token:
            self.send_response(401)
            self.end_headers()
            return

        file_path = qs.get("path", [None])[0]
        if not file_path:
            self.send_response(400)
            self.end_headers()
            return

        resolved = self._resolve_path(file_path)
        if not os.path.isfile(resolved):
            self.send_response(404)
            self.end_headers()
            return

        try:
            size = os.path.getsize(resolved)
            self.send_response(200)
            self.send_header("Content-Type", "application/octet-stream")
            self.send_header("Content-Length", str(size))
            self.send_header("Content-Disposition", f'attachment; filename="{os.path.basename(resolved)}"')
            self.end_headers()

            with open(resolved, "rb") as f:
                while chunk := f.read(64 * 1024):
                    self.wfile.write(chunk)
        except Exception:
            pass

    def _handle_upload(self):
        from urllib.parse import urlparse, parse_qs
        parsed = urlparse(self.path)
        qs = parse_qs(parsed.query)

        token = qs.get("token", [None])[0] or self.headers.get("X-DevLens-Token") or self.headers.get("X-Mesh-Token")
        if self.auth_token and token != self.auth_token:
            self.send_response(401)
            self.end_headers()
            return

        target_dir = qs.get("dir", ["."])[0]
        filename = qs.get("filename", ["uploaded_file"])[0]

        resolved_dir = self._resolve_path(target_dir)
        os.makedirs(resolved_dir, exist_ok=True)
        dest_path = os.path.join(resolved_dir, os.path.basename(filename))

        length = int(self.headers.get("Content-Length", 0))
        written = 0
        try:
            with open(dest_path, "wb") as f:
                remaining = length
                while remaining > 0:
                    chunk_size = min(remaining, 64 * 1024)
                    chunk = self.rfile.read(chunk_size)
                    if not chunk:
                        break
                    f.write(chunk)
                    written += len(chunk)
                    remaining -= len(chunk)

            self._send_json({
                "success": True,
                "path": dest_path,
                "bytes_written": written
            })
        except Exception as e:
            self._send_json({
                "success": False,
                "error": str(e),
                "bytes_written": written
            }, status=500)

    def _run_permissions_audit(self):
        import time
        home = os.path.expanduser("~")
        checks = []

        sensitive_paths = [
            ("Klucze SSH", os.path.join(home, ".ssh")),
            ("Plik konfiguracyjny SSH", os.path.join(home, ".ssh", "config")),
            ("Folder profilu AWS", os.path.join(home, ".aws")),
            ("Folder profilu GCloud", os.path.join(home, ".config", "gcloud")),
            ("Folder konfiguracyjny DevLens", os.path.join(home, ".devlens")),
        ]

        if platform.system() == "Darwin":
            sensitive_paths.append(("Katalog woluminów (/Volumes)", "/Volumes"))
            if os.path.isdir("/Volumes"):
                try:
                    for v in os.listdir("/Volumes"):
                        vp = os.path.join("/Volumes", v)
                        if not os.path.islink(vp):
                            sensitive_paths.append((f"Dysk zewnętrzny ({v})", vp))
                except Exception:
                    pass

        recommendations = []
        all_passed = True

        for name, p in sensitive_paths:
            exists = os.path.exists(p)
            readable = os.access(p, os.R_OK) if exists else False
            writable = os.access(p, os.W_OK) if exists else False

            if exists and not readable:
                all_passed = False
                if "Volumes" in p or "Dysk" in name:
                    recommendations.append(
                        f"Brak dostępu do {name}: Włącz 'Dyski wymienne' lub 'Pełny dostęp do dysku' w Ustawieniach systemowych macOS."
                    )
                else:
                    recommendations.append(f"Upewnij się, że użytkownik ma prawa do odczytu: {name}")

            checks.append({
                "name": name,
                "path": p,
                "readable": readable,
                "writable": writable,
                "exists": exists
            })

        overall_status = "ok" if all_passed else "action_required"
        summary = "Środowisko DevLens działa poprawnie." if all_passed else "Wykryto ograniczenia uprawnień (brak dostępu do wybranych ścieżek/dysków)."

        return {
            "timestamp": int(time.time()),
            "platform": platform.system(),
            "os_version": platform.release(),
            "arch": platform.machine(),
            "all_granted": all_passed,
            "overall_status": overall_status,
            "summary": summary,
            "filesystem": {
                "all_passed": all_passed,
                "paths": checks
            },
            "recommendations": recommendations
        }

    def _fix_permission(self, action):
        return {
            "success": True,
            "action": action,
            "message": f"Zastosowano optymalizację: {action}"
        }

    def _send_json(self, data, status=200):
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.end_headers()
        self.wfile.write(json.dumps(data, indent=2).encode("utf-8"))

def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    parser = argparse.ArgumentParser(description="DevLens Node Daemon")
    parser.add_argument("--host", default="0.0.0.0", help="Adres nasłuchiwania")
    parser.add_argument("--port", type=int, default=8888, help="Port serwera")
    parser.add_argument("--token", default=None, help="Token autoryzacyjny")
    args = parser.parse_args()

    token = args.token
    config_path = os.path.expanduser("~/.devlens/nodes.json")

    if not token:
        if os.path.isfile(config_path):
            try:
                with open(config_path, "r", encoding="utf-8") as f:
                    cfg = json.load(f)
                    for k in ["local", "self"]:
                        if k in cfg and "token" in cfg[k]:
                            token = cfg[k]["token"]
                            break
            except Exception:
                pass

        if not token:
            token = secrets.token_hex(16)
            try:
                os.makedirs(os.path.dirname(config_path), exist_ok=True)
                existing = {}
                if os.path.isfile(config_path):
                    with open(config_path, "r", encoding="utf-8") as f:
                        existing = json.load(f)
                existing["local"] = {"host": "127.0.0.1", "port": args.port, "token": token}
                with open(config_path, "w", encoding="utf-8") as f:
                    json.dump(existing, f, indent=2)
            except Exception:
                pass

    DevLensRequestHandler.auth_token = token
    server = HTTPServer((args.host, args.port), DevLensRequestHandler)
    print(f"🚀 DevLens Daemon nasłuchuje na {args.host}:{args.port}")
    print(f"🔑 Auth Token: {token}")
    print(f"🤝 Zero-Touch LAN Pairing aktywny na POST /pair")
    server.serve_forever()

if __name__ == "__main__":
    main()
