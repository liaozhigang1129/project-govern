#!/usr/bin/env python3
"""
IM Webhook Receiver — im-smoke 用

功能:
  - 起一个 HTTP server,监听 3 个路径:
      /webhook/wechat-work   → 写到 logs/wechat_work.jsonl
      /webhook/dingtalk      → 写到 logs/dingtalk.jsonl
      /webhook/feishu        → 写到 logs/feishu.jsonl
      /webhook/oauth/token   → 模拟企业微信 gettoken,返固定 access_token
  - 每个 POST 写一行 JSON: {ts, path, method, headers, body, query}
  - 企业微信的 markdown 用 markdown 协议;此 server 全部以 200 + errcode=0 响应

用法:
  python3 im_webhook_receiver.py [--port PORT] [--logdir DIR]
"""
import argparse
import http.server
import json
import os
import sys
import time
from datetime import datetime

LOG_DEFAULT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "logs")
LOG_DIR = os.path.abspath(os.environ.get("IM_SMOKE_LOG_DIR", LOG_DEFAULT))
PORT = int(os.environ.get("IM_SMOKE_RECEIVER_PORT", "18080"))

# WeChat Work gettoken 固定 mock
MOCK_WECHAT_TOKEN = "MOCK_WECHAT_TOKEN_OK"


def log_request_to_file(channel: str, request_handler) -> None:
    """把请求写到 logs/<channel>.jsonl
    兼容: Content-Length、Transfer-Encoding: chunked
    """
    cl_header = request_handler.headers.get("Content-Length")
    te = request_handler.headers.get("Transfer-Encoding", "").lower()
    if cl_header and cl_header.isdigit():
        raw = request_handler.rfile.read(int(cl_header))
    elif "chunked" in te:
        # 简单 chunked 解码(Java HttpURLConnection 行为)
        raw = b""
        while True:
            size_line = request_handler.rfile.readline().strip()
            if not size_line:
                continue
            try:
                size = int(size_line, 16)
            except ValueError:
                break
            if size == 0:
                request_handler.rfile.readline()  # 末尾 CRLF
                break
            chunk = request_handler.rfile.read(size)
            request_handler.rfile.readline()  # chunk 后的 CRLF
            raw += chunk
    else:
        # 兜底:读 1MB 上限
        raw = request_handler.rfile.read(1 * 1024 * 1024)

    try:
        body_text = raw.decode("utf-8")
        body_parsed = json.loads(body_text) if body_text else None
    except Exception:
        body_parsed = None
        body_text = raw.decode("utf-8", errors="replace")

    record = {
        "ts": datetime.utcnow().isoformat() + "Z",
        "path": request_handler.path,
        "method": request_handler.command,
        "query": dict(request_handler.query_params) if hasattr(request_handler, "query_params") else {},
        "headers": {k: v for k, v in request_handler.headers.items()},
        "body": body_parsed if body_parsed is not None else body_text,
    }
    path = os.path.join(LOG_DIR, f"{channel}.jsonl")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "a", encoding="utf-8") as f:
        f.write(json.dumps(record, ensure_ascii=False) + "\n")


class ImWebhookHandler(http.server.BaseHTTPRequestHandler):
    """所有路径统一处理"""

    def log_message(self, format, *args):
        # 关掉默认 stderr 噪声
        pass

    def _route(self):
        p = self.path
        # 解析 query string
        if "?" in p:
            path, qs = p.split("?", 1)
            self.query_params = {}
            for kv in qs.split("&"):
                if "=" in kv:
                    k, v = kv.split("=", 1)
                    self.query_params[k] = v
            p = path
        else:
            self.query_params = {}

        if p == "/healthz":
            return "_health"
        # wechat-work/gettoken 优先于 wechat-work/* 通用(因为 GET 也要 mock 返 token)
        if p.startswith("/wechat-work/gettoken") or p.startswith("/webhook/oauth/token"):
            return "_wechat_token"
        # alias for backend ImHttpClient path conventions
        if p.startswith("/wechat-work/"):
            return "wechat_work"
        if p.startswith("/dingtalk/"):
            return "dingtalk"
        if p.startswith("/feishu/"):
            return "feishu"
        if p.startswith("/webhook/wechat-work"):
            return "wechat_work"
        if p.startswith("/webhook/dingtalk"):
            return "dingtalk"
        if p.startswith("/webhook/feishu"):
            return "feishu"
        return None

    def do_GET(self):
        ch = self._route()
        if ch == "_wechat_token":
            qs = self.query_params or {}
            # 错误凭证模拟: corpsecret=WRONG → 返 40013
            if qs.get("corpsecret") == "WRONG":
                body = json.dumps({"errcode": 40013, "errmsg": "invalid corpid"}).encode()
            else:
                body = json.dumps({"errcode": 0, "errmsg": "ok", "access_token": MOCK_WECHAT_TOKEN,
                                    "expires_in": 7200}).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        if ch == "_health":
            body = b'{"status":"UP"}'
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        self.send_response(404)
        self.end_headers()

    def do_POST(self):
        ch = self._route()
        if ch in ("wechat_work", "dingtalk", "feishu"):
            log_request_to_file(ch, self)
            # 三个平台都返 errcode=0 (飞书是 code=0)
            if ch == "feishu":
                body = json.dumps({"code": 0, "msg": "ok", "data": {}}).encode()
            else:
                body = json.dumps({"errcode": 0, "errmsg": "ok"}).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        self.send_response(404)
        self.end_headers()


def main():
    global LOG_DIR
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=PORT)
    ap.add_argument("--logdir", default=LOG_DIR)
    args = ap.parse_args()

    LOG_DIR = os.path.abspath(args.logdir)  # noqa: PLW0603
    os.makedirs(LOG_DIR, exist_ok=True)
    print(f"[im-webhook] listening on :{args.port}, logs -> {LOG_DIR}", flush=True)

    srv = http.server.HTTPServer(("127.0.0.1", args.port), ImWebhookHandler)
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
