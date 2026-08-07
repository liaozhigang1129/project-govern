#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""P5-告警发送器 (飞书 / 钉钉 / 企业微信 群机器人 webhook)
用法:
  python3 alerter.py --title "模型监控告警" --level danger \
    --message "新模型 accuracy 0.78 较上周下降 8.5%"

支持:
  - 飞书 (PMO_FEISHU_WEBHOOK + PMO_FEISHU_SECRET)
  - 钉钉 (PMO_DINGTALK_WEBHOOK + PMO_DINGTALK_SECRET)
  - 企业微信群机器人 (PMO_WECOM_BOT_WEBHOOK, 无加签)

退出码: 0=全部成功  1=部分失败  2=全部失败
"""
import argparse
import os
import sys
import json
import hmac
import hashlib
import base64
import time
import urllib.parse
import urllib.request
import ssl
import datetime

LEVEL_COLOR = {
    "info":    "blue",
    "warning": "orange",
    "danger":  "red",
    "success": "green",
}

def _http_post(url, payload, timeout=8):
    req = urllib.request.Request(
        url, data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"}, method="POST"
    )
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    with urllib.request.urlopen(req, timeout=timeout, context=ctx) as r:
        body = r.read().decode("utf-8")
    return r.status, body

def send_feishu(title, message, level, at_mobiles=None):
    webhook = os.environ.get("PMO_FEISHU_WEBHOOK", "")
    secret = os.environ.get("PMO_FEISHU_SECRET", "")
    if not webhook:
        return False, "PMO_FEISHU_WEBHOOK not set"
    # 加签
    if secret:
        ts = str(round(time.time()))
        s = f"{ts}\n{secret}"
        h = hmac.new(s.encode("utf-8"), digestmod=hashlib.sha256).digest()
        sign = urllib.parse.quote_plus(base64.b64encode(h).decode())
        webhook = f"{webhook}&timestamp={ts}&sign={sign}"
    color = LEVEL_COLOR.get(level, "blue")
    payload = {
        "msg_type": "interactive",
        "card": {
            "header": {"title": {"tag": "plain_text", "content": title}, "template": color},
            "elements": [
                {"tag": "div", "text": {"tag": "lark_md", "content": message}},
                {"tag": "note", "elements": [{"tag": "plain_text", "content": "PMO AI Monitor  " + datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")}]},
            ],
        },
    }
    try:
        status, body = _http_post(webhook, payload)
        return status == 200, body
    except Exception as e:
        return False, str(e)

def send_dingtalk(title, message, level, at_mobiles=None):
    webhook = os.environ.get("PMO_DINGTALK_WEBHOOK", "")
    secret = os.environ.get("PMO_DINGTALK_SECRET", "")
    if not webhook:
        return False, "PMO_DINGTALK_WEBHOOK not set"
    if secret:
        ts = str(round(time.time() * 1000))
        s = f"{ts}\n{secret}"
        h = hmac.new(s.encode("utf-8"), digestmod=hashlib.sha256).digest()
        sign = urllib.parse.quote_plus(base64.b64encode(h).decode())
        webhook = f"{webhook}&timestamp={ts}&sign={sign}"
    color_word = {"info":"blue","warning":"orange","danger":"red","success":"green"}.get(level,"blue")
    payload = {
        "msgtype": "markdown",
        "markdown": {"title": title, "text": f"## {title}\n\n{message}\n\n> {datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')}"},
        "at": {"atMobiles": at_mobiles or [], "isAtAll": False},
    }
    try:
        status, body = _http_post(webhook, payload)
        return status == 200, body
    except Exception as e:
        return False, str(e)

def send_wecom(title, message, level, at_mobiles=None):
    webhook = os.environ.get("PMO_WECOM_BOT_WEBHOOK", "")
    if not webhook:
        return False, "PMO_WECOM_BOT_WEBHOOK not set"
    color_word = {"info":"info","warning":"warning","danger":"danger","success":"info"}.get(level,"info")
    mention = ""
    if at_mobiles:
        mention = " @" + " @".join(at_mobiles) + " "
    payload = {
        "msgtype": "markdown",
        "markdown": {"content": f"## {title}\n\n{message}{mention}\n\n> {datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')}"},
    }
    try:
        status, body = _http_post(webhook, payload)
        return status == 200, body
    except Exception as e:
        return False, str(e)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--title", required=True)
    parser.add_argument("--message", required=True)
    parser.add_argument("--level", default="warning", choices=["info","warning","danger","success"])
    parser.add_argument("--at", default=None, help="at mobile list, comma separated")
    parser.add_argument("--channels", default="feishu,dingtalk,wecom", help="comma separated")
    args = parser.parse_args()
    at_list = [x.strip() for x in args.at.split(",")] if args.at else []
    channels = [c.strip() for c in args.channels.split(",") if c.strip()]
    funcs = {"feishu": send_feishu, "dingtalk": send_dingtalk, "wecom": send_wecom}
    ok, fail = 0, 0
    print(f"[alerter] sending title={args.title!r} level={args.level} channels={channels}")
    for ch in channels:
        if ch not in funcs:
            print(f"  [{ch}] unknown channel, skip")
            continue
        success, detail = funcs[ch](args.title, args.message, args.level, at_list)
        flag = "✓" if success else "✗"
        print(f"  [{ch}] {flag} {detail[:200]}")
        if success: ok += 1
        else:        fail += 1
    print(f"[alerter] ok={ok} fail={fail}")
    sys.exit(0 if fail == 0 else (1 if ok > 0 else 2))

if __name__ == "__main__":
    main()
