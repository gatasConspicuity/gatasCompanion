#!/usr/bin/env python3
"""Prepare shared mobile candidates and publish explicitly requested releases.

Only the publish, promote and record-ios commands modify GitHub. All remote
operations are restricted to the team repository; local preparation is offline.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlparse
from urllib.request import Request, urlopen

REPOSITORY = "gatasConspicuity/gatasCompanion"
API = "https://api.github.com/repos/" + REPOSITORY
ROOT = Path(__file__).resolve().parents[2]
FIELDS = ("MARKETING_VERSION", "CURRENT_PROJECT_VERSION", "GATAS_RELEASE_BASELINE")
META = re.compile(r"<!-- gatas-release\n(.*?)\n-->", re.S)
OVERVIEW = re.compile(r"\n<!-- gatas-overview -->.*?<!-- /gatas-overview -->", re.S)


def run(*args, cwd=None):
    return subprocess.check_output(args, cwd=cwd, text=True).strip()


def version_values(root):
    text = (Path(root) / "Version.xcconfig").read_text()
    values = {}
    for key in FIELDS:
        matches = re.findall(r"^" + key + r"\s*=\s*(\S+)\s*$", text, re.M)
        if len(matches) != 1:
            raise ValueError("Expected exactly one " + key)
        values[key] = matches[0]
    if not re.fullmatch(r"(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)", values[FIELDS[0]]):
        raise ValueError("Use a three-part numeric product version")
    for key in FIELDS[1:]:
        if not re.fullmatch(r"-?\d+", values[key]):
            raise ValueError(key + " must be an integer")
        values[key] = int(values[key])
    if not 1 <= values[FIELDS[1]] <= 2_100_000_000:
        raise ValueError("Build number must be between 1 and 2100000000")
    return values


def check_source(root, ready=False, clean=False):
    root = Path(root)
    values = version_values(root)
    config = (root / "iosApp/Configuration/Config.xcconfig").read_text()
    project = (root / "iosApp/GatasCompanion.xcodeproj/project.pbxproj").read_text()
    local = root / "iosApp/Configuration/Config.local.xcconfig"
    if '#include "../../Version.xcconfig"' not in config:
        raise ValueError("Apple configuration must include Version.xcconfig")
    for key in FIELDS[:2]:
        override = r'^\s*"?' + key + r'(?:\[[^\]]+\])*"?\s*='
        if re.search(override, config + "\n" + project, re.M):
            raise ValueError("Duplicate Apple version setting: " + key)
        if local.exists() and re.search(override, local.read_text(), re.M):
            raise ValueError("Local signing configuration must not override " + key)
        if os.environ.get("ACTION") == "install" and os.environ.get(key) != str(values[key]):
            raise ValueError("Archive settings do not match committed " + key)
    if ready and not 0 <= values[FIELDS[2]] < values[FIELDS[1]]:
        raise ValueError("Prepare a candidate after checking used/reserved numbers in both services")
    if clean and run("git", "status", "--porcelain", "--untracked-files=normal", cwd=root):
        raise ValueError("Distributed builds require a fully committed working tree")
    return values


def prepare(root, highest, version=None):
    values = check_source(root)
    if highest < 0:
        raise ValueError("Highest used or reserved number must not be negative")
    build = max(highest, values[FIELDS[1]]) + 1
    if build > 2_100_000_000:
        raise ValueError("Build counter exceeds Android's supported range")
    version = version or values[FIELDS[0]]
    if not re.fullmatch(r"(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)", version):
        raise ValueError("Use a three-part numeric product version")
    if tuple(map(int, version.split("."))) < tuple(map(int, values[FIELDS[0]].split("."))):
        raise ValueError("Product versions must not decrease")
    (Path(root) / "Version.xcconfig").write_text(
        "// Shared product version and continuously increasing mobile build counter.\n"
        f"MARKETING_VERSION = {version}\nCURRENT_PROJECT_VERSION = {build}\n"
        "// Highest checked number in the active shared sequence; see docs/RELEASES.md.\n"
        f"GATAS_RELEASE_BASELINE = {highest}\n")
    return build


def validate_manifest(data):
    if data.get("schema") != 1 or data.get("platform") not in ("android", "ios"):
        raise ValueError("Invalid release metadata")
    if data.get("channel") not in ("test", "stable"):
        raise ValueError("Invalid release channel")
    if not re.fullmatch(r"\d+\.\d+\.\d+", str(data.get("version", ""))):
        raise ValueError("Invalid release version")
    if type(data.get("build")) is not int or not 1 <= data["build"] <= 2_100_000_000:
        raise ValueError("Invalid release build")
    if not re.fullmatch(r"[0-9a-f]{40}", str(data.get("commit", ""))) or data.get("branch") != "main":
        raise ValueError("Invalid source revision")
    return data


def tag_for(data):
    suffix = f"-build.{data['build']}" if data["channel"] == "test" else ""
    return f"{data['platform']}/v{data['version']}{suffix}"


def records(releases):
    result = []
    for release in releases:
        tag = release.get("tag_name", "")
        if not tag.startswith(("android/v", "ios/v")):
            continue
        match = META.search(release.get("body") or "")
        if not match:
            raise ValueError("Managed release is missing metadata: " + tag)
        data = validate_manifest(json.loads(match[1]))
        if tag_for(data) != tag:
            raise ValueError("Release tag does not match metadata: " + tag)
        if not release.get("draft") and release.get("prerelease") != (data["channel"] == "test"):
            raise ValueError("Release channel flag does not match metadata: " + tag)
        result.append((release, data))
    return result


def check_allocation(data, existing):
    validate_manifest(data)
    same = False
    highest = 0
    for _, other in existing:
        highest = max(highest, other["build"])
        if other["build"] == data["build"]:
            if (other["version"], other["commit"]) != (data["version"], data["commit"]):
                raise ValueError("Build number is already assigned to another source revision")
            same = True
    if not same and data["build"] <= highest:
        raise ValueError("Unregistered candidate is older than an already allocated build")


def candidate(root, platform, existing):
    values = check_source(root, ready=True, clean=True)
    commit = run("git", "rev-parse", "HEAD", cwd=root)
    subprocess.run(["git", "merge-base", "--is-ancestor", commit, "origin/main"], cwd=root, check=True)
    data = dict(schema=1, version=values[FIELDS[0]], build=values[FIELDS[1]],
                commit=commit, branch="main", platform=platform, channel="test")
    check_allocation(data, existing)
    return data


class GitHub:
    def __init__(self):
        self.token = os.environ.get("GH_TOKEN")
        if not self.token:
            raise ValueError("GH_TOKEN is required for GitHub operations")

    def request(self, path, method="GET", data=None):
        body = json.dumps(data).encode() if data is not None else None
        request = Request(API + path, data=body, method=method, headers={
            "Authorization": "Bearer " + self.token,
            "Accept": "application/vnd.github+json", "Content-Type": "application/json",
            "X-GitHub-Api-Version": "2022-11-28"})
        with urlopen(request, timeout=60) as response:
            return json.load(response)

    def releases(self):
        result = []
        page = 1
        while True:
            batch = self.request(f"/releases?per_page=100&page={page}")
            result.extend(batch)
            if len(batch) < 100:
                return result
            page += 1

    def ensure_tag_matches(self, data):
        reference = quote("refs/tags/" + tag_for(data), safe="")
        try:
            commit = self.request("/commits/" + reference)
        except HTTPError as error:
            if error.code == 404:
                return
            raise
        if commit.get("sha") != data["commit"]:
            raise ValueError("Existing Git tag points to another source revision")

    def download_apk(self, release, data, directory):
        name = apk_name(data)
        assets = [a for a in release["assets"] if a["name"] == name]
        if len(assets) != 1:
            raise ValueError("Expected exactly one release APK")
        subprocess.run(["gh", "release", "download", release["tag_name"], "--repo", REPOSITORY,
                        "--pattern", name, "--dir", str(directory)], check=True)
        path = Path(directory) / name
        if sha256(path) != data.get("sha256"):
            raise ValueError("Published APK checksum does not match release metadata")
        return path


def sha256(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def apk_name(data):
    return f"GatasCompanion-{data['version']}-{data['build']}.apk"


def verify_apk(path, data, tools, fingerprint=None):
    badging = run(str(Path(tools) / "aapt"), "dump", "badging", str(path))
    package = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging, re.M)
    if not package or package.groups() != ("nl.rvt.gatas", str(data["build"]), data["version"]):
        raise ValueError("APK identity/version does not match release metadata")
    if fingerprint is not None:
        output = run(str(Path(tools) / "apksigner"), "verify", "--verbose", "--print-certs", str(path))
        found = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)", output)
        expected = fingerprint.replace(":", "").lower()
        if not re.fullmatch(r"[0-9a-f]{64}", expected) or [f.lower() for f in found] != [expected]:
            raise ValueError("APK is not signed with the expected project certificate")
    return path


def notes(data):
    url = data.get("availability_url", "")
    availability = f"\nAvailable at: {url}\n" if url else ""
    if not url and data["platform"] == "ios" and data["channel"] == "test":
        availability = "\nAvailable through TestFlight invitations; see the README to request access.\n"
    return (f"{data['platform'].capitalize()} {data['channel']} release: "
            f"**{data['version']} ({data['build']})**\n\n"
            f"Commit: {data['commit']}\nBranch: {data['branch']}\n"
            + availability + "\n<!-- gatas-release\n" + json.dumps(data, sort_keys=True) + "\n-->\n")


def overview(existing):
    lines = ["\n<!-- gatas-overview -->", "## Current availability", "",
             "| Platform | Channel | Version / build | Commit | Published (UTC) | Link |",
             "| --- | --- | --- | --- | --- | --- |"]
    for platform in ("android", "ios"):
        for channel in ("stable", "test"):
            candidates = [(r, d) for r, d in existing if not r["draft"] and
                          d["platform"] == platform and d["channel"] == channel]
            if not candidates:
                lines.append(f"| {platform} | {channel} | Not published | — | — | — |")
                continue
            release, data = max(candidates, key=lambda pair: pair[1]["build"])
            url = data.get("availability_url") or release["html_url"]
            date = (release.get("published_at") or "")[:10]
            lines.append(f"| {platform} | {channel} | {data['version']} ({data['build']}) | "
                         f"`{data['commit'][:12]}` | {date} | [Open]({url}) |")
    return "\n".join(lines) + "\n<!-- /gatas-overview -->\n"


def refresh_overview(github):
    existing = records(github.releases())
    current = [pair for pair in existing if not pair[0]["draft"]]
    targets = {}
    for platform in ("android", "ios"):
        for channel in ("stable", "test"):
            pairs = [pair for pair in current if pair[1]["platform"] == platform and pair[1]["channel"] == channel]
            if pairs:
                release, _ = max(pairs, key=lambda pair: pair[1]["build"])
                targets[release["id"]] = release
    table = overview(existing)
    for release in targets.values():
        body = OVERVIEW.sub("", release.get("body") or "").rstrip() + "\n" + table
        github.request(f"/releases/{release['id']}", "PATCH", {"body": body})


def publish(github, data, apk=None):
    validate_manifest(data)
    if apk and (Path(apk).name != apk_name(data) or sha256(apk) != data.get("sha256")):
        raise ValueError("APK filename/checksum does not match release metadata")
    existing = records(github.releases())
    check_allocation(data, existing)
    tag = tag_for(data)
    if any(r["tag_name"] == tag for r, _ in existing):
        raise ValueError("Release already exists (including drafts); inspect it rather than overwriting it")
    if data["channel"] == "stable":
        stable = [d for r, d in existing if not r["draft"] and d["platform"] == data["platform"] and d["channel"] == "stable"]
        version = tuple(map(int, data["version"].split(".")))
        if any(tuple(map(int, d["version"].split("."))) >= version for d in stable):
            raise ValueError("Stable product version must be newer than the existing stable version")
    github.ensure_tag_matches(data)
    release = github.request("/releases", "POST", {
        "tag_name": tag, "target_commitish": data["commit"],
        "name": f"{data['platform'].capitalize()} {data['version']} ({data['build']}) — {data['channel']}",
        "body": notes(data), "draft": True, "prerelease": data["channel"] == "test",
        "make_latest": "false"})
    with tempfile.TemporaryDirectory() as directory:
        manifest = Path(directory) / "release.json"
        manifest.write_text(json.dumps(data, indent=2) + "\n")
        files = [str(manifest)]
        if apk:
            checksum = Path(directory) / "SHA256SUMS"
            checksum.write_text(data["sha256"] + "  " + Path(apk).name + "\n")
            files.extend([str(apk), str(checksum)])
        subprocess.run(["gh", "release", "upload", tag, "--repo", REPOSITORY, *files], check=True)
    github.ensure_tag_matches(data)
    github.request(f"/releases/{release['id']}", "PATCH", {
        "draft": False, "make_latest": "true" if data["platform"] == "android" and data["channel"] == "stable" else "false"})
    refresh_overview(github)
    print("Published: " + tag)


def ios_url(url, channel):
    parsed = urlparse(url)
    host = "testflight.apple.com" if channel == "test" else "apps.apple.com"
    if parsed.scheme != "https" or parsed.netloc != host or parsed.query or parsed.fragment:
        raise ValueError("Use a public HTTPS TestFlight/App Store link without query parameters")
    if not re.fullmatch(r"/[A-Za-z0-9/_-]+", parsed.path):
        raise ValueError("Invalid public Apple URL")
    return url


def promote(github, source_tag, tools, fingerprint):
    matches = [(r, d) for r, d in records(github.releases())
               if r["tag_name"] == source_tag and not r["draft"]]
    if len(matches) != 1 or matches[0][1]["platform"] != "android" or matches[0][1]["channel"] != "test":
        raise ValueError("Select a published Android prerelease")
    release, source = matches[0]
    github.ensure_tag_matches(source)
    with tempfile.TemporaryDirectory() as directory:
        apk = github.download_apk(release, source, directory)
        verify_apk(apk, source, tools, fingerprint)
        publish(github, dict(source, channel="stable", promoted_from=source_tag), apk)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=ROOT)
    commands = parser.add_subparsers(dest="command", required=True)
    check = commands.add_parser("check-source")
    check.add_argument("--archive", action="store_true")
    prep = commands.add_parser("prepare")
    prep.add_argument("--highest-used-or-reserved", type=int, required=True)
    prep.add_argument("--version")
    create = commands.add_parser("candidate")
    create.add_argument("--output", type=Path, required=True)
    for name in ("verify-apk", "publish"):
        sub = commands.add_parser(name)
        sub.add_argument("--manifest", type=Path, required=True)
        sub.add_argument("--apk", type=Path, required=True)
        sub.add_argument("--tools", type=Path, required=True)
        sub.add_argument("--fingerprint", required=name == "publish")
        sub.add_argument("--expected-commit", required=name == "publish")
    promote = commands.add_parser("promote")
    promote.add_argument("--source-tag", required=True)
    promote.add_argument("--tools", type=Path, required=True)
    promote.add_argument("--fingerprint", required=True)
    ios = commands.add_parser("record-ios")
    ios.add_argument("--channel", choices=("test", "stable"), required=True)
    ios.add_argument("--url", default="")
    commands.add_parser("overview")
    args = parser.parse_args()
    if args.command == "check-source":
        values = check_source(args.repo, ready=args.archive, clean=args.archive)
        print(f"Shared version: {values[FIELDS[0]]} ({values[FIELDS[1]]})")
    elif args.command == "prepare":
        build = prepare(args.repo, args.highest_used_or_reserved, args.version)
        print(f"Prepared build {build}. Commit the complete candidate before archiving/distribution.")
    elif args.command == "candidate":
        data = candidate(args.repo, "android", records(GitHub().releases()))
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(data, indent=2) + "\n")
    elif args.command in ("verify-apk", "publish"):
        data = validate_manifest(json.loads(args.manifest.read_text()))
        if data["platform"] != "android" or data["channel"] != "test":
            raise ValueError("Expected an Android test candidate")
        if args.expected_commit and data["commit"] != args.expected_commit:
            raise ValueError("Artifact source commit does not match the selected candidate")
        verify_apk(args.apk, data, args.tools, args.fingerprint)
        if args.command == "publish":
            data.update(sha256=sha256(args.apk), certificate_sha256=args.fingerprint.replace(":", "").lower())
            publish(GitHub(), data, args.apk)
    elif args.command == "promote":
        promote(GitHub(), args.source_tag, args.tools, args.fingerprint)
    elif args.command == "record-ios":
        github = GitHub()
        data = candidate(args.repo, "ios", records(github.releases()))
        data["channel"] = args.channel
        if args.url:
            data["availability_url"] = ios_url(args.url, args.channel)
        elif args.channel == "stable":
            raise ValueError("A public App Store URL is required for a stable iOS entry")
        if args.channel == "stable" and not any(not r["draft"] and d["platform"] == "ios" and
                d["channel"] == "test" and d["commit"] == data["commit"] and d["build"] == data["build"]
                for r, d in records(github.releases())):
            raise ValueError("Record the matching TestFlight build before its App Store availability")
        publish(github, data)
    else:
        refresh_overview(GitHub())


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError, HTTPError, URLError) as error:
        sys.exit("Release operation failed: " + str(error))
