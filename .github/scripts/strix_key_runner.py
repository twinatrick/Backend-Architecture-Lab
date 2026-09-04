#!/usr/bin/env python3
"""
Strix Key Pool & Automated Security Scan Dispatcher
- Discovers API keys from environment variables and local .env
- Probes and validates active, non-leaked keys
- Sanitizes and validates target inputs against strict whitelists
- Dynamically masks secrets in CI logs
- Invokes Strix CLI with safe subprocess array execution and multi-key fallback
"""

import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path
from typing import Dict, List, Optional, Tuple

SAFE_URL_PATTERN = re.compile(r"^https?://[a-zA-Z0-9.\-_:]+(/[a-zA-Z0-9.\-_~:/?#[\]@!$&'()*+,;=]*)?$")
SAFE_REF_PATTERN = re.compile(r"^[a-zA-Z0-9.\-_/]+$")


def mask_secret(secret: str) -> None:
    """Mask secret in GitHub Actions log output."""
    if secret and len(secret) > 4:
        print(f"::add-mask::{secret}", flush=True)


def load_env_file(repo_root: Path) -> Dict[str, str]:
    """Load key-value pairs from .env file if present."""
    env_file = repo_root / ".env"
    env_vars: Dict[str, str] = {}
    if not env_file.is_file():
        return env_vars

    try:
        with open(env_file, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    k, v = line.split("=", 1)
                    k = k.strip()
                    v = v.strip().strip("'\"")
                    if k:
                        env_vars[k] = v
    except Exception as e:
        print(f"[WARN] Failed to read .env file: {e}", file=sys.stderr)
    return env_vars


def collect_api_keys(local_env: Dict[str, str]) -> List[Tuple[str, str, str]]:
    """
    Collect all API keys from process environment and .env.
    Returns list of (provider, model_name, api_key).
    """
    keys: List[Tuple[str, str, str]] = []
    seen = set()

    # 1. Gemini Keys (Prioritize gemini-3.6-flash, gemini-3.7-flash, gemini-2.5-flash)
    gemini_keys: List[str] = []
    for var in ["GEMINI_API_KEY", "LLM_API_KEY"]:
        val = os.environ.get(var) or local_env.get(var)
        if val and val not in seen and val.startswith("AIzaSy"):
            gemini_keys.append(val)
            seen.add(val)

    for i in range(1, 11):
        var = f"GEMINI_API_KEY_{i}"
        val = os.environ.get(var) or local_env.get(var)
        if val and val not in seen:
            gemini_keys.append(val)
            seen.add(val)

    for k in gemini_keys:
        mask_secret(k)
        keys.append(("gemini", "gemini/gemini-3.6-flash", k))

    # 2. Groq Keys (Fallback provider)
    groq_keys: List[str] = []
    for var in ["GROQ_API_KEY"]:
        val = os.environ.get(var) or local_env.get(var)
        if val and val not in seen and val.startswith("gsk_"):
            groq_keys.append(val)
            seen.add(val)

    for i in range(1, 11):
        var = f"GROQ_API_KEY_{i}"
        val = os.environ.get(var) or local_env.get(var)
        if val and val not in seen:
            groq_keys.append(val)
            seen.add(val)

    for k in groq_keys:
        mask_secret(k)
        keys.append(("groq", "groq/llama-3.3-70b-versatile", k))

    # 3. OpenAI Keys
    openai_key = os.environ.get("OPENAI_API_KEY") or local_env.get("OPENAI_API_KEY")
    if openai_key and openai_key not in seen:
        mask_secret(openai_key)
        seen.add(openai_key)
        keys.append(("openai", "openai/gpt-4o", openai_key))

    return keys


def probe_key(provider: str, api_key: str) -> bool:
    """Perform lightweight HTTP health-check to verify if key is valid and not suspended."""
    if provider == "gemini":
        url = f"https://generativelanguage.googleapis.com/v1beta/models?key={api_key}"
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "StrixKeyRunner/1.0"})
            with urllib.request.urlopen(req, timeout=5) as resp:
                return resp.status == 200
        except urllib.error.HTTPError as e:
            # 403 indicates leaked or suspended key
            if e.code == 403:
                return False
            # 429 indicates rate limited, key is still fundamentally valid
            if e.code == 429:
                return True
            return False
        except Exception:
            return False
    elif provider == "groq":
        url = "https://api.groq.com/openai/v1/models"
        try:
            req = urllib.request.Request(
                url,
                headers={"Authorization": f"Bearer {api_key}", "User-Agent": "StrixKeyRunner/1.0"},
            )
            with urllib.request.urlopen(req, timeout=5) as resp:
                return resp.status == 200
        except Exception:
            return False
    return True


def sanitize_inputs() -> Dict[str, str]:
    """Validate and sanitize environment inputs against strict whitelists."""
    scan_type = os.environ.get("SCAN_TYPE", "white-box").strip().lower()
    if scan_type not in ["white-box", "black-box", "gray-box"]:
        scan_type = "white-box"

    scan_mode = os.environ.get("SCAN_MODE", "quick").strip().lower()
    if scan_mode not in ["quick", "standard", "deep"]:
        scan_mode = "quick"

    target_url = os.environ.get("TARGET_URL", "").strip()
    if target_url and not SAFE_URL_PATTERN.match(target_url):
        print(f"[WARN] Invalid TARGET_URL rejected: '{target_url}'", file=sys.stderr)
        target_url = ""

    diff_base = os.environ.get("DIFF_BASE", "origin/master").strip()
    if diff_base and not SAFE_REF_PATTERN.match(diff_base):
        diff_base = "origin/master"

    auth_token = os.environ.get("AUTH_TOKEN", "").strip()
    if auth_token:
        mask_secret(auth_token)

    return {
        "scan_type": scan_type,
        "scan_mode": scan_mode,
        "target_url": target_url,
        "diff_base": diff_base,
        "auth_token": auth_token,
    }


def build_strix_command(inputs: Dict[str, str]) -> List[str]:
    """Build sanitized Strix command-line argument list."""
    cmd = ["strix", "-n", "--scan-mode", inputs["scan_mode"]]

    scan_type = inputs["scan_type"]
    target_url = inputs["target_url"]
    diff_base = inputs["diff_base"]
    auth_token = inputs["auth_token"]

    if scan_type == "black-box":
        if not target_url:
            target_url = "http://localhost:8000"
        cmd.extend(["-t", target_url])
    elif scan_type == "gray-box":
        if not target_url:
            target_url = "http://localhost:8000"
        cmd.extend(["-t", target_url])
        if auth_token:
            cmd.extend(["--auth-token", auth_token])
        else:
            cmd.extend(
                [
                    "--instruction",
                    "Probe authenticated API endpoints, IDOR and role escalation using test credentials",
                ]
            )
    else:  # white-box
        cmd.extend(["-t", "./"])
        if inputs["scan_mode"] == "quick":
            cmd.extend(["--scope-mode", "diff", "--diff-base", diff_base])

    return cmd


def main() -> int:
    repo_root = Path(__file__).resolve().parent.parent.parent
    local_env = load_env_file(repo_root)

    inputs = sanitize_inputs()
    candidates = collect_api_keys(local_env)

    if not candidates:
        print("[ERROR] No API keys (Gemini, Groq, or OpenAI) found in environment or .env!", file=sys.stderr)
        return 1

    print(f"[INFO] Discovered {len(candidates)} candidate API key(s). Probing active keys...")
    valid_candidates: List[Tuple[str, str, str]] = []

    for provider, model, key in candidates:
        is_active = probe_key(provider, key)
        if is_active:
            print(f"   [OK] Key valid: provider={provider}, model={model}")
            valid_candidates.append((provider, model, key))
        else:
            print(f"   [SKIP] Key suspended or unauthorized: provider={provider}")

    if not valid_candidates:
        print("[WARN] All probed keys failed validation. Attempting first configured key as fallback...", file=sys.stderr)
        valid_candidates = [candidates[0]]

    if len(sys.argv) > 1:
        strix_cmd = ["strix"] + sys.argv[1:]
    else:
        strix_cmd = build_strix_command(inputs)
    print(f"[INFO] Strix Command: {' '.join(strix_cmd)}")

    # Execute Strix with fallback across valid keys
    last_returncode = 1
    for provider, model, api_key in valid_candidates:
        print(f"\n[RUN] Launching Strix with {provider} ({model})...")
        exec_env = os.environ.copy()
        exec_env["STRIX_LLM"] = model
        exec_env["LLM_API_KEY"] = api_key

        try:
            res = subprocess.run(strix_cmd, env=exec_env, shell=False, check=False)
            last_returncode = res.returncode
            if last_returncode == 0:
                print(f"[SUCCESS] Strix scan completed successfully with {model}.")
                return 0
            print(f"[WARN] Strix exited with code {last_returncode}. Rotating to next key in pool...")
        except Exception as e:
            print(f"[ERROR] Failed to execute Strix: {e}", file=sys.stderr)

    return last_returncode


if __name__ == "__main__":
    sys.exit(main())
