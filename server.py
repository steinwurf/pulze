"""Pulze server sending multicast packets in a configurable interval."""
import argparse
import socket
import time

# Constants
PORT = 51423
DEFAULT_INTERVAL = 1000


def transmit(broadcast_ip, port, interval):
    """Transmit data."""
    print("Transmitting data every {interval}ms, "
          "to {port}".format(
            port=port,
            interval=interval))

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    # sock.bind(('', 0))
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    address = (broadcast_ip, port)
    try:
        packet = 0
        while True:
            time.sleep(interval / 1000.0)
            packet += 1
            data = "{0:05d},{1:010d}".format(interval, packet)
            # Don't print if the interval is too low.
            if not interval <= 10:
                print("sending {}".format(data))
            sock.sendto(data, address)
    except:
        print("Stopping")
        sock.close()


def main():
    """Main function."""
    parser = argparse.ArgumentParser()

    parser.add_argument(
        '--ip',
        help='The broadcast ip to use for the transmission.',
        type=str,
        default='10.0.0.255')

    parser.add_argument(
        '--port',
        help='The port to use for the transmission.',
        type=int,
        default=PORT)

    parser.add_argument(
        '--interval',
        help='The transmit interval (ms).',
        type=int,
        default=DEFAULT_INTERVAL)
    args = parser.parse_args()
    transmit(args.ip, args.port, args.interval)


if __name__ == '__main__':
    main()
