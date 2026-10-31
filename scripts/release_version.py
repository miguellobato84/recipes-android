"""Allocate a positive integer Android release version from Git tags."""

import os
import re
import subprocess

MAX_VERSION = 2_100_000_000
VERSION_TAG = re.compile(r"v([1-9][0-9]*)\Z")


def select_version(tags: list[str], commit_tags: list[str]) -> int:
    def numbers(values: list[str]) -> list[int]:
        return [int(match[1]) for tag in values if (match := VERSION_TAG.fullmatch(tag))]

    existing = numbers(commit_tags)
    version = max(existing) if existing else max(numbers(tags), default=0) + 1
    if version > MAX_VERSION:
        raise ValueError("Android versionCode exceeds 2100000000")
    return version


def git(*args: str) -> list[str]:
    return subprocess.check_output(["git", *args], text=True).splitlines()


if __name__ == "__main__":
    version = select_version(git("tag", "--list"), git("tag", "--points-at", "HEAD"))
    tag = f"v{version}"
    if tag not in git("tag", "--points-at", "HEAD"):
        subprocess.run(["git", "tag", tag, "HEAD"], check=True)
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
        output.write(f"number={version}\ntag={tag}\n")
    print(f"Release version: {tag}")
