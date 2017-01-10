"""Pulze server sending multicast packets in a configurable interval."""
import argparse
import array
import fcntl
import socket
import struct
import time
import sys

# Constants
PORT = 51423
DEFAULT_SEND_INTERVAL = 1000
DEFAULT_KEEP_ALIVE_INTERVAL = 100
DEFAULT_PAYLOAD_SIZE = 0
PACKET_FORMAT = "{packet:010d},{send_interval:05d},{keep_alive:05d},{payload}"


def all_interfaces():
    """Return a list of all network interfaces."""
    max_possible = 128  # arbitrary. raise if needed.
    number_of_bytes = max_possible * 32
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    names = array.array('B', '\0' * number_of_bytes)
    outbytes = struct.unpack('iL', fcntl.ioctl(
        s.fileno(),
        0x8912,  # SIOCGIFCONF
        struct.pack('iL', number_of_bytes, names.buffer_info()[0])
    ))[0]
    namestr = names.tostring()
    interfaces = {}

    for i in range(0, outbytes, 40):
        name = namestr[i:i+16].split('\0', 1)[0]
        ip = namestr[i+20:i+24]
        interfaces[name] = format_ip(ip)
    return interfaces


def format_ip(addr):
    """Format given ip address nicely."""
    return \
        str(ord(addr[0])) + '.' + \
        str(ord(addr[1])) + '.' + \
        str(ord(addr[2])) + '.' + \
        str(ord(addr[3]))


def transmit(interface_ip, port, send_interval, client_keep_alive_interval,
             payload_size):
    """Transmit data."""
    print("Transmitting data every {send_interval}ms, "
          "to port {port}".format(
            port=port,
            send_interval=send_interval))

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind((interface_ip, 0))
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    broadcast_ip = '.'.join(interface_ip.split('.')[:-1] + ['255'])
    print broadcast_ip
    address = (broadcast_ip, port)
    payload = "X" * payload_size

    bytes_sent = 0
    time_start = time.time()
    time_last = time_start

    try:
        packet = 0
        while True:
            if send_interval:
                time_next = time_last + (send_interval / 1000.0)
                sleeptime = time_next - time.time()
                if(sleeptime > 0):
                    time.sleep(sleeptime)
                time_last = time_next
            packet += 1
            data = PACKET_FORMAT.format(
                packet=packet,
                send_interval=send_interval,
                keep_alive=client_keep_alive_interval,
                payload=payload)

            bytes_sent += len(data)
            send_rate = (bytes_sent * 8 / (time.time() - time_start)) / 10**6

            sock.sendto(data, address)

            # For high send rate (low send intervals), only print sometimes
            if send_interval <= 100 and packet % (100 / send_interval):
                continue

            sys.stdout.write("\rSent {}... with length {}. "
                  "Data rate is {} Mbps".format(data[:26],
                                                len(data),
                                                send_rate))
            sys.stdout.flush()

    except KeyboardInterrupt:
        print("\nStopping")
    finally:
        sock.close()


def main():
    """Main function."""
    parser = argparse.ArgumentParser()

    interfaces = all_interfaces()

    parser.add_argument(
        '--interface',
        help='The network interface to use for the transmission.',
        type=str,
        choices=interfaces.keys(),
        default='eth2')

    parser.add_argument(
        '--port',
        help='The port to use for the transmission.',
        type=int,
        default=PORT)

    parser.add_argument(
        '--send-interval',
        help='The transmit interval (ms).',
        type=int,
        default=DEFAULT_SEND_INTERVAL)

    parser.add_argument(
        '--keep-alive-interval',
        help='The interval in which the clients sends keep alive data (ms).',
        type=int,
        default=DEFAULT_KEEP_ALIVE_INTERVAL)

    parser.add_argument(
        '--payload-size',
        help='The size of the payload appended to the packets (bytes).',
        type=int,
        default=DEFAULT_PAYLOAD_SIZE)


    args = parser.parse_args()
    transmit(
        interfaces[args.interface],
        args.port,
        args.send_interval,
        args.keep_alive_interval,
        args.payload_size)

if __name__ == '__main__':
    main()
