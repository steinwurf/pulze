"""Pulze server sending multicast packets in a configurable interval."""
import argparse
import array
import socket
import struct
import time
import sys

# Constants
PORT = 51423
DEFAULT_SEND_INTERVAL = 1000
DEFAULT_KEEP_ALIVE_INTERVAL = 100
DEFAULT_PAYLOAD_SIZE = 0
# Packet format: packet number, send interval [ms], keep alive interval [ms], WIFI sleep policy, WIFI lock type, payload
PACKET_FORMAT = ">IIIII{payload_size:d}s"

# Android wifi policy:
# https://developer.android.com/reference/android/provider/Settings.Global.html#WIFI_SLEEP_POLICY
WIFI_SLEEP_POLICY_DEFAULT = 0 # Default after turning off
WIFI_SLEEP_POLICY_NEVER = 2 # Never sleep
WIFI_SLEEP_POLICY_NEVER_WHILE_PLUGGED = 1 # Never sleep while plugged in

# Android wifi lock type:
# https://developer.android.com/reference/android/net/wifi/WifiManager.html#WIFI_MODE_FULL
WIFI_MODE_NONE = 0 # No lock
WIFI_MODE_FULL = 1
WIFI_MODE_FULL_HIGH_PERF = 3


def all_interfaces():
    """Return a list of all network interfaces."""
    import fcntl
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
             payload_size, use_broadcast, wifi_sleep_policy, wifi_lock_type):
    """Transmit data."""
    print("Transmitting data every {send_interval}ms, ".format(
        send_interval=send_interval))

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind((interface_ip, 0))
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)

    broadcast_ip = '.'.join(interface_ip.split('.')[:-1] + ['255'])
    address = (broadcast_ip, port)

    if not use_broadcast:
        mcaddress = "224.0.0.251"
        address = (mcaddress, port)
    print "Sending to endpoint", address

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
            datafmt = PACKET_FORMAT.format(payload_size=payload_size)
            data = struct.pack(datafmt, packet, int(send_interval),
                               client_keep_alive_interval, wifi_sleep_policy,
                               wifi_lock_type, payload)

            bytes_sent += len(data)
            send_rate = (bytes_sent * 8 / (time.time() - time_start)) / 10**6

            sock.sendto(data, address)

            # For high send rate (low send intervals), only print sometimes
            if send_interval < 100.0 and packet % int(100.0/send_interval):
                continue

            sys.stdout.write("\rSent packet number {} with length {}. "
                  "Data rate is {} Mbps".format(packet, len(data), send_rate))
            sys.stdout.flush()

    except KeyboardInterrupt:
        print("\nStopping")
    finally:
        sock.close()


def main():
    """Main function."""
    parser = argparse.ArgumentParser()

    if sys.platform != 'win32':

        interfaces = all_interfaces()

        parser.add_argument(
            '--interface',
            help='The network interface to use for the transmission.',
            type=str,
            choices=interfaces.keys(),
            default='eth2')

    else:

        parser.add_argument(
            '--interface',
            help='The IP of the network interface to use for transmission.',
            type=str,
            default='0.0.0.0')

    parser.add_argument(
        '--port',
        help='The port to use for the transmission.',
        type=int,
        default=PORT)

    parser.add_argument(
        '--send-interval',
        help='The transmit interval (ms).',
        type=float,
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

    parser.add_argument(
        '--use-broadcast',
        help='Use a broadcast ip instead of multicast',
        action='store_true'
    )

    wifi_sleep_policy = parser.add_mutually_exclusive_group()
    wifi_sleep_policy.add_argument('--wifi-sleep-policy-never',
        dest='wifi_sleep_policy',
        action='store_const', const=WIFI_SLEEP_POLICY_NEVER,
        default=WIFI_SLEEP_POLICY_DEFAULT,
        help='Set Android WiFi sleep policy to WIFI_SLEEP_POLICY_NEVER')
    wifi_sleep_policy.add_argument('--wifi-sleep-policy-never-while-plugged',
        dest='wifi_sleep_policy',
        action='store_const', const=WIFI_SLEEP_POLICY_NEVER_WHILE_PLUGGED,
        default=WIFI_SLEEP_POLICY_DEFAULT,
        help="Set Android WiFi sleep policy "
             "to WIFI_SLEEP_POLICY_NEVER_WHILE_PLUGGED")

    wifi_lock_type = parser.add_mutually_exclusive_group()
    wifi_lock_type.add_argument('--wifi-mode-full',
        dest='wifi_lock_type',
        action='store_const', const=WIFI_MODE_FULL,
        default=WIFI_MODE_NONE,
        help="Set Android WiFi lock type to WIFI_MODE_FULL")
    wifi_lock_type.add_argument('--wifi-mode-full-high-perf',
        dest='wifi_lock_type',
        action='store_const', const=WIFI_MODE_FULL_HIGH_PERF,
        default=WIFI_MODE_NONE,
        help="Set Android WiFi lock type to WIFI_MODE_FULL_HIGH_PERF")

    args = parser.parse_args()

    if sys.platform != 'win32':
        interface = interfaces[args.interface]
    else:
        interface = args.interface

    transmit(
        interface,
        args.port,
        args.send_interval,
        args.keep_alive_interval,
        args.payload_size,
        args.use_broadcast,
        args.wifi_sleep_policy,
        args.wifi_lock_type)

if __name__ == '__main__':
    main()
