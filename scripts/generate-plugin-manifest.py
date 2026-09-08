#!/usr/bin/env python3
"""Generate the public SageTV V9 plugin manifest for an exact package."""

import argparse
import hashlib
import pathlib
import re
import xml.etree.ElementTree as ET
import zipfile


def md5(path):
    digest = hashlib.md5(usedforsecurity=False)
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest().upper()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--version", required=True)
    parser.add_argument("--package", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    if not re.fullmatch(r"[0-9]+(?:\.[0-9]+){1,3}", args.version):
        raise SystemExit("SageTV plugin version must be dotted numeric")
    package = pathlib.Path(args.package)
    if not package.is_file() or package.stat().st_size == 0:
        raise SystemExit(f"plugin package does not exist or is empty: {package}")

    with zipfile.ZipFile(package) as archive:
        members = [name.replace("\\", "/") for name in archive.namelist()]
    if not members or any(
        name.startswith("/") or name.startswith("../") or "/../" in name
        for name in members
    ):
        raise SystemExit("plugin package has an empty or unsafe root-relative layout")
    required_roots = {"JARs/", "plugins/", "docs/"}
    present_roots = {
        root for root in required_roots if any(name.startswith(root) for name in members)
    }
    if present_roots != required_roots:
        missing = ", ".join(sorted(required_roots - present_roots))
        raise SystemExit(f"plugin package lacks required root paths: {missing}")

    location = (
        "https://github.com/opensagetv-vibe/opensagetv-vibe-tmdb/"
        f"releases/download/v{args.version}/{package.name}"
    )
    root = ET.Element("SageTVPlugin")
    fields = (
        ("Name", "OpenSageTV Vibe TMDB Metadata Service"),
        ("Identifier", "opensagetv-vibe-tmdb"),
        ("Description", "Shared, cache-backed TMDB metadata service for SageTV plugins."),
        ("Author", "OpenSageTV Vibe"),
        ("CreationDate", "2026.09.08"),
        ("ModificationDate", "2026.09.08"),
        ("Version", args.version),
        ("ResourcePath", "OpenSageTVVibeTMDB"),
        ("Webpage", "https://github.com/opensagetv-vibe/opensagetv-vibe-tmdb"),
        ("PluginType", "Standard"),
        ("ImplementationClass", "org.opensagetv.vibe.tmdb.plugin.OpenSageTVVibeTmdbPlugin"),
    )
    for name, value in fields:
        ET.SubElement(root, name).text = value
    for kind, minimum in (("JVM", "1.8.0"), ("Core", "9.0.0")):
        dependency = ET.SubElement(root, "Dependency")
        ET.SubElement(dependency, kind)
        ET.SubElement(dependency, "MinVersion").text = minimum
    package_element = ET.SubElement(root, "Package")
    # This is one root-relative archive containing JARs/, plugins/, and docs/.
    # Stock SageTV extracts JAR packages under JARs/, which would incorrectly
    # produce JARs/JARs/... for this layout. System packages extract relative
    # to the SageTV server root and still trigger the JAR-loader refresh.
    ET.SubElement(package_element, "PackageType").text = "System"
    ET.SubElement(package_element, "Location").text = location
    ET.SubElement(package_element, "MD5").text = md5(package)
    ET.SubElement(root, "ReleaseNotes").text = (
        "Initial shared TMDB service with SQLite caching and fail-safe SageMC/XMLTV adapters."
    )

    ET.indent(root, space="    ")
    output = pathlib.Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    ET.ElementTree(root).write(output, encoding="UTF-8", xml_declaration=True)
    parsed = ET.parse(output).getroot()
    if (
        parsed.findtext("Version") != args.version
        or parsed.findtext("Package/PackageType") != "System"
        or parsed.findtext("Package/MD5") != md5(package)
    ):
        raise SystemExit("generated SageTV plugin manifest failed verification")


if __name__ == "__main__":
    main()
