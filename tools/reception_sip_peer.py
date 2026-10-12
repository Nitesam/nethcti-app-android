#!/usr/bin/env python3
"""Loopback-only SIP registrar/call peer for ReceptionInstrumentation (no credentials)."""
import re
import socket
import time


def main():
    sip = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sip.bind(('127.0.0.1', 15060))
    sip.settimeout(1)
    target = contact = None
    calls = 0
    end = time.monotonic() + 900
    sdp = ('v=0\r\no=peer 1 1 IN IP4 10.0.2.2\r\ns=Local test\r\n'
           'c=IN IP4 10.0.2.2\r\nt=0 0\r\nm=audio 15062 RTP/AVP 0\r\n'
           'a=rtpmap:0 PCMU/8000\r\na=sendrecv\r\n')
    print('Reception peer ready (loopback only)', flush=True)
    while time.monotonic() < end:
        try:
            packet, source = sip.recvfrom(65535)
        except socket.timeout:
            continue
        message = packet.decode(errors='replace')
        if message == 'INCOMING' and target:
            calls += 1
            request = (f'INVITE {contact} SIP/2.0\r\n'
                       f'Via: SIP/2.0/UDP 10.0.2.2:15060;branch=z9hG4bK-test-{calls};rport\r\n'
                       'Max-Forwards: 70\r\nFrom: <sip:peer@10.0.2.2>;tag=peer\r\n'
                       f'To: <{contact}>\r\nCall-ID: reception-{calls}\r\nCSeq: 1 INVITE\r\n'
                       'Contact: <sip:peer@10.0.2.2:15060>\r\nContent-Type: application/sdp\r\n'
                       f'Content-Length: {len(sdp)}\r\n\r\n{sdp}')
            sip.sendto(request.encode(), target)
            print(f'INVITE test={calls}', flush=True)
            continue
        lines = message.split('\r\n')
        first = lines[0]
        headers = {}
        for line in lines[1:]:
            if not line:
                break
            key, _, value = line.partition(':')
            headers.setdefault(key.lower(), []).append(value.strip())
        if first.startswith('SIP/2.0'):
            print(first, headers.get('call-id', [''])[0], flush=True)
            if 'INVITE' in headers.get('cseq', [''])[0] and int(first.split()[1]) >= 200:
                ack = (f"ACK {contact} SIP/2.0\r\nVia: {headers['via'][0]}\r\n"
                       f"From: {headers['from'][0]}\r\nTo: {headers['to'][0]}\r\nCall-ID: {headers['call-id'][0]}\r\n"
                       'CSeq: 1 ACK\r\nMax-Forwards: 70\r\nContent-Length: 0\r\n\r\n')
                sip.sendto(ack.encode(), source)
            continue
        if first.startswith('ACK'):
            continue
        if not all(k in headers for k in ('from', 'to', 'call-id', 'cseq', 'via')):
            continue
        out = ['Via: ' + v for v in headers['via']]
        for name in ('From', 'To', 'Call-ID', 'CSeq'):
            value = headers[name.lower()][0]
            if name == 'To' and ';tag=' not in value:
                value += ';tag=local'
            out.append(name + ': ' + value)
        body = ''
        if first.startswith('REGISTER'):
            if 'contact' in headers:
                target = source
                contact = re.search(r'<([^>]+)>', headers['contact'][0])[1]
                out.append('Contact: ' + headers['contact'][0])
            out.append('Expires: ' + headers.get('expires', ['300'])[0])
            print('REGISTER expires=' + headers.get('expires', ['contact'])[0], flush=True)
        elif first.startswith('INVITE'):
            body = sdp
            out += ['Contact: <sip:peer@10.0.2.2:15060>', 'Content-Type: application/sdp']
            print('OUTGOING INVITE', flush=True)
        response = '\r\n'.join(['SIP/2.0 200 OK'] + out + [f'Content-Length: {len(body)}', '', body])
        sip.sendto(response.encode(), source)


if __name__ == '__main__':
    main()
