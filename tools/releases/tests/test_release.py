"""Release safety tests; no signing secrets, distribution accounts or network."""
import copy
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("mobile_release", Path(__file__).parents[1] / "release.py")
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)


def manifest(platform="android", channel="test", build=42, commit="a" * 40, version="1.1.0"):
    return dict(schema=1, version=version, build=build, commit=commit,
                branch="main", platform=platform, channel=channel)


def entry(data, draft=False, index=1):
    return dict(id=index, tag_name=release.tag_for(data), target_commitish=data["commit"],
                body=release.notes(data), draft=draft, prerelease=data["channel"] == "test",
                html_url="https://github.com/" + release.REPOSITORY + "/releases/tag/" + release.tag_for(data),
                published_at="2026-10-21T12:00:00Z", assets=[])


class FakeGitHub:
    def __init__(self, releases=()):
        self.items = copy.deepcopy(list(releases))
        self.calls = []
        self.apk_bytes = b"exact tested APK bytes"

    def releases(self):
        return copy.deepcopy(self.items)

    def ensure_tag_matches(self, data):
        pass

    def request(self, path, method="GET", data=None):
        self.calls.append((path, method, copy.deepcopy(data)))
        if method == "POST":
            item = dict(data, id=len(self.items) + 1, assets=[],
                        html_url="https://github.com/example/release", published_at="2026-10-21T12:00:00Z")
            self.items.append(item)
            return item
        item = next(r for r in self.items if str(r["id"]) == path.rsplit("/", 1)[1])
        item.update(data)
        return item

    def download_apk(self, item, data, directory):
        path = Path(directory) / release.apk_name(data)
        path.write_bytes(self.apk_bytes)
        if release.sha256(path) != data["sha256"]:
            raise ValueError("Checksum mismatch")
        return path


class SourceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "iosApp/Configuration").mkdir(parents=True)
        (self.root / "iosApp/GatasCompanion.xcodeproj").mkdir()
        (self.root / "Version.xcconfig").write_text(
            "MARKETING_VERSION = 1.0.0\nCURRENT_PROJECT_VERSION = 3\nGATAS_RELEASE_BASELINE = -1\n")
        (self.root / "iosApp/Configuration/Config.xcconfig").write_text('#include "../../Version.xcconfig"\n')
        (self.root / "iosApp/GatasCompanion.xcodeproj/project.pbxproj").write_text("")

    def test_bootstrap_builds_locally_but_cannot_be_distributed(self):
        release.check_source(self.root)
        with self.assertRaisesRegex(ValueError, "Prepare a candidate"):
            release.check_source(self.root, ready=True)

    def test_prepare_uses_highest_service_or_branch_number_and_does_not_reset(self):
        self.assertEqual(release.prepare(self.root, 26101700, "1.1.0"), 26101701)
        self.assertEqual(release.prepare(self.root, 10, "1.2.0"), 26101702)
        release.check_source(self.root, ready=True)

    def test_invalid_preparation_does_not_modify_configuration(self):
        before = (self.root / "Version.xcconfig").read_bytes()
        for highest, version in ((-1, "1.0.0"), (2_100_000_000, "1.0.0"), (4, "1.0-beta"), (4, "0.9.0")):
            with self.subTest(highest=highest, version=version), self.assertRaises(ValueError):
                release.prepare(self.root, highest, version)
            self.assertEqual((self.root / "Version.xcconfig").read_bytes(), before)

    def test_local_signing_file_cannot_override_release_identity(self):
        local = self.root / "iosApp/Configuration/Config.local.xcconfig"
        local.write_text("CURRENT_PROJECT_VERSION[sdk=iphoneos*] = 100\n")
        with self.assertRaisesRegex(ValueError, "Local signing"):
            release.check_source(self.root)

    def test_target_override_is_rejected(self):
        project = self.root / "iosApp/GatasCompanion.xcodeproj/project.pbxproj"
        project.write_text("    MARKETING_VERSION = 9.0.0;\n")
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            release.check_source(self.root)

    def test_conditional_target_override_is_rejected(self):
        project = self.root / "iosApp/GatasCompanion.xcodeproj/project.pbxproj"
        project.write_text('    "CURRENT_PROJECT_VERSION[sdk=watchos*]" = 99;\n')
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            release.check_source(self.root)

    def test_archive_settings_must_match_the_source(self):
        release.prepare(self.root, 4)
        with patch.dict(release.os.environ, {"ACTION": "install", "MARKETING_VERSION": "1.0.0", "CURRENT_PROJECT_VERSION": "99"}):
            with self.assertRaisesRegex(ValueError, "Archive settings"):
                release.check_source(self.root, ready=True)

    def test_uncommitted_candidates_are_rejected(self):
        release.prepare(self.root, 4)
        subprocess.run(["git", "init", "-q", str(self.root)], check=True)
        with self.assertRaisesRegex(ValueError, "fully committed"):
            release.check_source(self.root, ready=True, clean=True)


class AllocationTests(unittest.TestCase):
    def test_platforms_can_share_a_build_and_skip_numbers(self):
        previous = release.records([entry(manifest("ios")), entry(manifest("ios", build=43, commit="b" * 40), index=2)])
        release.check_allocation(manifest("android"), previous)
        release.check_allocation(manifest("android", build=44, commit="c" * 40), previous)

    def test_build_cannot_be_reused_for_other_code_or_version(self):
        previous = release.records([entry(manifest())])
        for other in (manifest(commit="b" * 40), manifest(version="1.2.0")):
            with self.assertRaisesRegex(ValueError, "another source"):
                release.check_allocation(other, previous)

    def test_unknown_older_candidate_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "older"):
            release.check_allocation(manifest(build=40), release.records([entry(manifest())]))

    def test_draft_reserves_number_but_does_not_appear_available(self):
        items = release.records([entry(manifest(), draft=True)])
        with self.assertRaisesRegex(ValueError, "another source"):
            release.check_allocation(manifest(commit="b" * 40), items)
        self.assertEqual(release.overview(items).count("Not published"), 4)

    def test_corrupt_or_mislabelled_release_fails_closed(self):
        item = entry(manifest())
        item["prerelease"] = False
        with self.assertRaisesRegex(ValueError, "channel flag"):
            release.records([item])
        item["body"] = "missing metadata"
        with self.assertRaisesRegex(ValueError, "missing metadata"):
            release.records([item])

    def test_overview_keeps_platform_and_channel_availability_separate(self):
        items = release.records([
            entry(manifest(channel="stable", build=30, version="1.0.0")),
            entry(manifest(), index=2),
            entry(manifest("ios", build=43, commit="b" * 40), index=3)])
        text = release.overview(items)
        self.assertIn("android | stable | 1.0.0 (30)", text)
        self.assertIn("android | test | 1.1.0 (42)", text)
        self.assertIn("ios | test | 1.1.0 (43)", text)
        self.assertIn("ios | stable | Not published", text)


class PublicationTests(unittest.TestCase):
    def test_missing_tag_uses_reference_lookup_instead_of_commit_lookup(self):
        github = object.__new__(release.GitHub)
        def request(path):
            status = 404 if path.startswith("/git/ref/tags/") else 422
            raise release.HTTPError("https://api.github.com" + path, status, "Not found", {}, None)
        with patch.object(github, "request", side_effect=request) as lookup:
            github.ensure_tag_matches(manifest())
        lookup.assert_called_once()

    def test_existing_reference_resolution_errors_are_not_treated_as_missing_tags(self):
        github = object.__new__(release.GitHub)
        reference = {"object": {"type": "tag", "sha": "b" * 40}}
        error = release.HTTPError("https://api.github.com/commits/ref", 404, "Not found", {}, None)
        with patch.object(github, "request", side_effect=[reference, error]):
            with self.assertRaises(release.HTTPError):
                github.ensure_tag_matches(manifest())

    def test_existing_tag_cannot_point_to_different_code(self):
        github = object.__new__(release.GitHub)
        with patch.object(github, "request", return_value={"sha": "b" * 40}):
            with self.assertRaisesRegex(ValueError, "Git tag"):
                github.ensure_tag_matches(manifest())
        with patch.object(github, "request", return_value={"sha": "a" * 40}):
            github.ensure_tag_matches(manifest())

    def test_duplicate_stable_or_draft_cannot_be_overwritten(self):
        for draft in (False, True):
            data = manifest(channel="stable")
            github = FakeGitHub([entry(data, draft=draft)])
            with self.assertRaisesRegex(ValueError, "already exists"):
                release.publish(github, data)
            self.assertFalse(github.calls)

    def test_invalid_apk_fails_before_any_remote_mutation(self):
        github = FakeGitHub()
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "wrong.apk"
            apk.write_bytes(b"unverified")
            with self.assertRaisesRegex(ValueError, "filename/checksum"):
                release.publish(github, manifest(), apk)
        self.assertFalse(github.calls)

    def test_test_release_and_ios_stable_never_receive_latest(self):
        for data in (manifest(), manifest("ios", "stable")):
            github = FakeGitHub()
            with patch.object(release.subprocess, "run"):
                release.publish(github, data)
            self.assertTrue(all(call[2].get("make_latest") != "true" for call in github.calls))

    def test_promotion_preserves_exact_apk_and_identity_without_building_or_signing(self):
        data = manifest()
        github = FakeGitHub()
        data["sha256"] = release.hashlib.sha256(github.apk_bytes).hexdigest()
        github.items = [entry(data)]
        uploaded = []
        def capture(args, **kwargs):
            apk = next(Path(arg) for arg in args if str(arg).endswith(".apk"))
            uploaded.append(apk.read_bytes())
            self.assertEqual(args[:3], ["gh", "release", "upload"])
        with patch.object(release, "verify_apk") as verify, patch.object(release.subprocess, "run", side_effect=capture):
            release.promote(github, release.tag_for(data), "/tools", "a" * 64)
        verify.assert_called_once()
        self.assertEqual(uploaded, [github.apk_bytes])
        final = release.records(github.items)[1][1]
        self.assertEqual((final["version"], final["build"], final["commit"], final["sha256"]),
                         (data["version"], data["build"], data["commit"], data["sha256"]))
        self.assertEqual(final["channel"], "stable")
        self.assertTrue(any(call[2].get("make_latest") == "true" for call in github.calls))

    def test_corrupted_promoted_apk_is_rejected(self):
        data = dict(manifest(), sha256="0" * 64)
        github = FakeGitHub([entry(data)])
        with self.assertRaisesRegex(ValueError, "Checksum"):
            release.promote(github, release.tag_for(data), "/tools", "a" * 64)
        self.assertFalse(github.calls)

    def test_apk_package_version_and_project_certificate_are_verified(self):
        valid = "package: name='nl.rvt.gatas' versionCode='42' versionName='1.1.0'"
        for output in (valid.replace("42", "43"), valid.replace("nl.rvt.gatas", "other.app")):
            with patch.object(release, "run", return_value=output), self.assertRaises(ValueError):
                release.verify_apk("app.apk", manifest(), "/tools")
        with patch.object(release, "run", side_effect=[valid, "Signer #1 certificate SHA-256 digest: " + "a" * 64]):
            with self.assertRaisesRegex(ValueError, "expected project"):
                release.verify_apk("app.apk", manifest(), "/tools", "b" * 64)

    def test_apple_link_validation_rejects_private_or_injected_links(self):
        for url in ("https://appstoreconnect.apple.com/apps/123", "https://testflight.apple.com@evil.example/join/test",
                    "https://testflight.apple.com/join/test?token=private", "https://testflight.apple.com/join/test)bad"):
            with self.assertRaises(ValueError):
                release.ios_url(url, "test")
        self.assertEqual(release.ios_url("https://testflight.apple.com/join/Example", "test"),
                         "https://testflight.apple.com/join/Example")


if __name__ == "__main__":
    unittest.main()
