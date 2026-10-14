#!/usr/bin/env python3
"""Single-call SIP/RTP test peer bound only to host loopback, for the Android emulator.

No registration, accounts, credentials, external calls or real audio.
Responds to the local test INVITE with G.711 PCMU silence (or --tone) and receives RTP.
"""
import math
import json
import argparse
import re
import select
import socket
import struct
import time


def pcmu(sample):
    sign = 0x80 if sample < 0 else 0
    value = min(32635, abs(sample)) + 0x84
    exponent = max(0, value.bit_length() - 8)
    mantissa = (value >> (exponent + 3)) & 15
    return (~(sign | (exponent << 4) | mantissa)) & 255


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--incoming", action="store_true", help="also wait for the incoming-call test trigger")
    parser.add_argument("--tone", action="store_true", help="synthetic 1 kHz low-level tone for gain tests")
    args = parser.parse_args()
    samples = bytes(pcmu(round(1000 * math.sin(2 * math.pi * n / 8))) for n in range(160)) if args.tone else b"\xff" * 160
    sip = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    rtp = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sip.bind(("127.0.0.1", 15060))
    rtp.bind(("127.0.0.1", 15062))
    print("Local SIP peer ready: 127.0.0.1:15060", flush=True)
    deadline = time.monotonic() + 300
    remote_rtp = None
    received = sent = 0
    sequence = timestamp = 0
    next_packet = 0
    sip_target = contact = None
    incoming_active = False
    sdp = ("v=0\r\no=peer 1 1 IN IP4 10.0.2.2\r\ns=Local test\r\n"
           "c=IN IP4 10.0.2.2\r\nt=0 0\r\nm=audio 15062 RTP/AVP 0\r\n"
           "a=rtpmap:0 PCMU/8000\r\na=sendrecv\r\n")
    while time.monotonic() < deadline:
        ready, _, _ = select.select([sip, rtp], [], [], 0.02)
        for sock in ready:
            packet, source = sock.recvfrom(65535)
            if sock is rtp:
                if len(packet) >= 12 and packet[0] >> 6 == 2:
                    received += 1
                    remote_rtp = source  # Symmetric RTP through the emulator NAT.
                continue
            message = packet.decode("utf-8", errors="replace")
            if message == "START_INCOMING" and args.incoming and sip_target:
                incoming_active = True
                remote_rtp = None
                received = sent = 0
                invite = (f"INVITE {contact} SIP/2.0\r\n"
                          "Via: SIP/2.0/UDP 10.0.2.2:15060;branch=z9hG4bK-local-incoming;rport\r\n"
                          "Max-Forwards: 70\r\n"
                          "From: <sip:loopback@10.0.2.2>;tag=incoming-peer\r\n"
                          f"To: <{contact}>\r\nCall-ID: incoming-local-test\r\nCSeq: 1 INVITE\r\n"
                          "Contact: <sip:loopback@10.0.2.2:15060>\r\n"
                          f"Content-Type: application/sdp\r\nContent-Length: {len(sdp)}\r\n\r\n{sdp}")
                sip.sendto(invite.encode(), sip_target)
                print("Incoming INVITE sent", flush=True)
                continue
            first_line = message.split("\r\n", 1)[0]
            if first_line.startswith("ACK "):
                continue
            headers = {}
            for line in message.split("\r\n")[1:]:
                if not line:
                    break
                key, value = line.split(":", 1)
                headers.setdefault(key.lower(), []).append(value.strip())
            if first_line.startswith("SIP/2.0 "):
                if first_line.startswith("SIP/2.0 200") and "INVITE" in headers.get("cseq", [""])[0]:
                    ack = (f"ACK {contact} SIP/2.0\r\n"
                           "Via: SIP/2.0/UDP 10.0.2.2:15060;branch=z9hG4bK-local-ack;rport\r\n"
                           f"From: {headers['from'][0]}\r\nTo: {headers['to'][0]}\r\n"
                           "Call-ID: incoming-local-test\r\nCSeq: 1 ACK\r\nMax-Forwards: 70\r\nContent-Length: 0\r\n\r\n")
                    sip.sendto(ack.encode(), source)
                continue
            response_headers = ["Via: " + v for v in headers.get("via", [])]
            for name in ["From", "To", "Call-ID", "CSeq"]:
                value = headers[name.lower()][0]
                if name == "To" and ";tag=" not in value:
                    value += ";tag=local-test-peer"
                response_headers.append(name + ": " + value)
            body = ""
            if first_line.startswith("REGISTER "):
                response_headers += ["Contact: " + value for value in headers.get("contact", [])]
                response_headers.append("Expires: " + headers.get("expires", ["300"])[0])
            if first_line.startswith("INVITE "):
                sip_target = source
                contact = re.search(r"<([^>]+)>", headers["contact"][0])[1]
                media = re.search(r"^m=audio (\d+) RTP/AVP ([0-9 ]+)", message, re.M)
                if not media or "0" not in media[2].split():
                    raise RuntimeError("Test requires unencrypted PCMU, no compatible SDP received")
                body = sdp
                response_headers += ["Contact: <sip:loopback@10.0.2.2:15060>", "Content-Type: application/sdp"]
            response = "\r\n".join(["SIP/2.0 200 OK"] + response_headers + [f"Content-Length: {len(body)}", "", body])
            sip.sendto(response.encode(), source)
            print(first_line, flush=True)
            if first_line.startswith("BYE "):
                print(json.dumps({"direction": "incoming" if incoming_active else "outgoing",
                                  "rtp_received": received, "rtp_sent": sent, "hangup": True}), flush=True)
                remote_rtp = None
                if not args.incoming or incoming_active:
                    return
        if remote_rtp and time.monotonic() >= next_packet:
            payload = struct.pack("!BBHII", 0x80, 0, sequence, timestamp, 12345) + samples
            rtp.sendto(payload, remote_rtp)
            sequence = (sequence + 1) & 0xffff
            timestamp = (timestamp + 160) & 0xffffffff
            sent += 1
            next_packet = time.monotonic() + 0.02
    raise TimeoutError("No completed local test call within 300 seconds")


if __name__ == "__main__":
    main()
