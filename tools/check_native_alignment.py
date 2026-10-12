#!/usr/bin/env python3
"""Check ELF load-segment alignment of the APK's 64-bit Android libraries."""
import argparse
import struct
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk")
    args = parser.parse_args()
    failures = []
    checked = 0
    abis = set()
    with zipfile.ZipFile(args.apk) as apk:
        for name in apk.namelist():
            if not name.startswith(("lib/arm64-v8a/", "lib/x86_64/")) or not name.endswith(".so"):
                continue
            data = apk.read(name)
            if data[:6] != b"\x7fELF\x02\x01":
                failures.append(f"{name}: expected little-endian ELF64")
                continue
            phoff = struct.unpack_from("<Q", data, 32)[0]
            phsize, phcount = struct.unpack_from("<HH", data, 54)
            loads = []
            for index in range(phcount):
                header = phoff + index * phsize
                if struct.unpack_from("<I", data, header)[0] == 1:  # PT_LOAD
                    loads.append(struct.unpack_from("<Q", data, header + 48)[0])
            if not loads or any(alignment < 16384 for alignment in loads):
                failures.append(f"{name}: incompatible load alignments {loads}")
            checked += 1
            abis.add(name.split("/")[1])
    if "arm64-v8a" not in abis:
        failures.append("APK has no ARM64 native libraries")
    print(f"Checked {checked} native libraries, ABIs: {', '.join(sorted(abis))}")
    for failure in failures:
        print(failure)
    if failures:
        raise SystemExit(1)
    print("All checked ELF load segments support 16 KB alignment.")
    print("Also run Android zipalign -c -P 16 4 on the APK to check ZIP alignment.")


if __name__ == "__main__":
    main()
