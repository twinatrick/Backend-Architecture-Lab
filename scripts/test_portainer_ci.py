"""以替身命令驗證 CI 部署流程；不接觸真實 Docker 或 Portainer。"""

import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

import yaml


PROJECT_ROOT = Path(__file__).resolve().parents[1]
SERVICES = (
    "gateway", "iam-service", "competency-service", "job-service",
    "external-api-service", "alert-service",
)
FAKE_COMMAND = r'''#!/usr/bin/env python3
import json
import os
from pathlib import Path
import sys

name = Path(sys.argv[0]).name
args = sys.argv[1:]
scenario = os.environ.get("SCENARIO", "success")
state = Path(os.environ["FAKE_STATE"])
services = ["gateway", "iam-service", "competency-service", "job-service",
    "external-api-service", "alert-service"]
original = {
    "services": {service: {"image": "old:" + service, "mem_limit": 536870912,
        "environment": ["SECRET=test-only-secret"], "volumes": ["existing:/data"]}
        for service in services + ["postgres", "redis", "nacos", "loki"]},
    "volumes": {"existing": {"external": True, "name": "unchanged-volume"}},
    "networks": {"existing": {"external": True}},
}
with (state / "calls.txt").open("a") as stream:
    stream.write(name + " " + ("PUT" if "PUT" in args else "READ") + "\n")
if name == "git":
    print(("old" if scenario == "stale" else "abc123") + "\trefs/heads/master")
elif name == "sleep":
    pass
elif name == "curl":
    url = next(value for value in args if value.startswith("https://"))
    if "PUT" in args:
        payload_path = args[args.index("--data-binary") + 1][1:]
        payload = json.loads(Path(payload_path).read_text())
        (state / "request.json").write_text(json.dumps(payload))
        if scenario == "api-failure":
            sys.exit(22)
        output = {"Id": 5}
    elif url.endswith("/stacks"):
        output = [{"Id": 5, "Name": "backend-managed", "EndpointId": 3, "Type": 2,
            "Status": 1, "Env": [{"name": "EXISTING", "value": "keep-me"}]}]
        if scenario == "missing-stack":
            output = []
    else:
        content = "services:\n  gateway: {}" if scenario == "yaml-stack" else json.dumps(original)
        output = {"StackFileContent": content}
    text = json.dumps(output)
    if "-o" in args:
        Path(args[args.index("-o") + 1]).write_text(text)
    else:
        print(text)
elif name == "docker":
    if args[:2] == ["image", "inspect"]:
        if scenario == "missing-image":
            sys.exit(1)
        print("sha256:expected" if "-f" in args else "[]")
    elif "-f" in args:
        print("healthy")
    elif args[0] == "inspect":
        print(json.dumps([{"Image": "sha256:expected", "State": {"Running": True},
            "Config": {"Labels": {"com.docker.compose.project": "backend-managed"}}}]))
    else:
        print("containers")
'''


@unittest.skipUnless(shutil.which("bash") and shutil.which("jq"), "需要 Bash 與 jq")
class PortainerDeploymentTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.bin_directory = self.directory / "bin"
        self.bin_directory.mkdir()
        for name in ("git", "docker", "curl", "sleep"):
            command = self.bin_directory / name
            command.write_text(FAKE_COMMAND, encoding="utf-8")
            command.chmod(0o700)
        configuration_path = PROJECT_ROOT / ".gitlab-ci.yml"
        configuration = yaml.safe_load(configuration_path.read_text(encoding="utf-8"))
        self.script = self.directory / "deploy.sh"
        script_content = "\n".join(configuration["deploy-cluster"]["script"])
        self.script.write_text(script_content, encoding="utf-8")
        self.environment = dict(os.environ,
            PATH=str(self.bin_directory) + os.pathsep + os.environ["PATH"],
            FAKE_STATE=str(self.directory), CI_COMMIT_SHA="abc123", CI_DEFAULT_BRANCH="master",
            CI_REPOSITORY_URL="https://example.invalid/repository.git",
            PORTAINER_API_URL="https://example.invalid/api",
            PORTAINER_API_KEY="test-token-not-real",
            PORTAINER_STACK_NAME="backend-managed", PORTAINER_ENDPOINT_ID="3")

    def run_scenario(self, scenario="success"):
        self.environment["SCENARIO"] = scenario
        return subprocess.run(["bash", "-eu", str(self.script)], env=self.environment,
            capture_output=True, text=True, timeout=30)

    def test_updates_only_application_images_and_preserves_stack_environment(self):
        result = self.run_scenario()
        self.assertEqual(result.returncode, 0, result.stderr)
        payload = json.loads((self.directory / "request.json").read_text())
        self.assertFalse(payload["Prune"])
        self.assertFalse(payload["PullImage"])
        self.assertEqual(payload["Env"], [{"name": "EXISTING", "value": "keep-me"}])
        stack = json.loads(payload["StackFileContent"])
        for service in SERVICES:
            expected_image = f"backend-architecture-lab-{service}:abc123"
            self.assertEqual(stack["services"][service]["image"], expected_image)
            self.assertEqual(stack["services"][service]["volumes"], ["existing:/data"])
            self.assertEqual(stack["services"][service]["environment"], ["SECRET=test-only-secret"])
            self.assertEqual(stack["services"][service]["mem_limit"], 536870912)
        self.assertEqual(stack["services"]["nacos"]["image"], "old:nacos")
        self.assertEqual(stack["volumes"]["existing"]["name"], "unchanged-volume")
        self.assertEqual(stack["networks"], {"existing": {"external": True}})
        self.assertNotIn("test-only-secret", result.stdout + result.stderr)

    def test_rejects_unsafe_inputs_before_any_update(self):
        for scenario in ("stale", "missing-image", "missing-stack", "yaml-stack"):
            with self.subTest(scenario=scenario):
                result = self.run_scenario(scenario)
                self.assertNotEqual(result.returncode, 0)
                self.assertFalse((self.directory / "request.json").exists())

    def test_api_failure_is_not_retried(self):
        result = self.run_scenario("api-failure")
        self.assertNotEqual(result.returncode, 0)
        calls = (self.directory / "calls.txt").read_text().splitlines()
        self.assertEqual(calls.count("curl PUT"), 1)

    def test_missing_token_fails_before_api_access(self):
        del self.environment["PORTAINER_API_KEY"]
        result = self.run_scenario()
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse((self.directory / "calls.txt").exists())

    def test_build_cleanup_only_prunes_unused_default_builder_cache(self):
        configuration = yaml.safe_load(
            (PROJECT_ROOT / ".gitlab-ci.yml").read_text(encoding="utf-8"))
        job = configuration["docker-images-build"]
        self.assertEqual(job["resource_group"], "backend-image-build")
        self.assertEqual(job["after_script"], [
            "docker buildx prune --builder default --all --force"])
        result = subprocess.run(["bash", "-eu", "-c", "\n".join(job["after_script"])],
            env=self.environment, capture_output=True, text=True, timeout=30)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual((self.directory / "calls.txt").read_text().splitlines(), ["docker READ"])


if __name__ == "__main__":
    unittest.main()
