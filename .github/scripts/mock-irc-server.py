#!/usr/bin/env python3
"""Small IRC subset used by MineTogether's offline runtime chat test."""

import argparse
import signal
import socketserver
import threading


class State:
    def __init__(self, channel):
        self.channel = channel
        self.clients = set()
        self.lock = threading.Lock()

    def broadcast(self, line, exclude=None):
        with self.lock:
            clients = list(self.clients)
        for client in clients:
            if client is not exclude:
                client.send(line)


class IrcHandler(socketserver.StreamRequestHandler):
    nick = None
    realname = "MineTogether CI"
    registered = False
    user_seen = False

    def setup(self):
        super().setup()
        with self.server.state.lock:
            self.server.state.clients.add(self)

    def finish(self):
        with self.server.state.lock:
            self.server.state.clients.discard(self)
        if self.nick:
            self.server.state.broadcast(f":{self.mask} QUIT :Client closed", exclude=self)
        super().finish()

    @property
    def mask(self):
        return f"{self.nick}!MineTogether@127.0.0.1"

    def send(self, line):
        try:
            self.wfile.write((line + "\r\n").encode("utf-8"))
            self.wfile.flush()
        except (BrokenPipeError, ConnectionResetError, ValueError):
            pass

    def maybe_register(self):
        if self.registered or not self.nick or not self.user_seen:
            return
        self.registered = True
        self.send(f":minetogether-ci 001 {self.nick} :Welcome to MineTogether CI IRC")
        self.send(f":minetogether-ci 002 {self.nick} :Your host is minetogether-ci")
        self.send(f":minetogether-ci 003 {self.nick} :This server was created for CI")
        self.send(f":minetogether-ci 004 {self.nick} minetogether-ci 0.1 o o")
        self.send(f":minetogether-ci 005 {self.nick} CHANTYPES=# PREFIX=(ov)@+ :are supported")
        self.send(f":minetogether-ci 375 {self.nick} :- minetogether-ci Message of the Day -")
        self.send(f":minetogether-ci 372 {self.nick} :- MineTogether CI")
        self.send(f":minetogether-ci 376 {self.nick} :End of MOTD")

    def handle_join(self, channel):
        channel = channel.lstrip(":")
        self.server.state.broadcast(f":{self.mask} JOIN :{channel}")
        with self.server.state.lock:
            names = [client.nick for client in self.server.state.clients if client.nick]
        self.send(f":minetogether-ci 353 {self.nick} = {channel} :" + " ".join(f"+{name}" for name in names))
        self.send(f":minetogether-ci 366 {self.nick} {channel} :End of NAMES list")
        self.server.state.broadcast(f":minetogether-ci MODE {channel} +v {self.nick}")

    def handle_who(self, channel):
        channel = channel.lstrip(":")
        with self.server.state.lock:
            clients = [client for client in self.server.state.clients if client.nick]
        for client in clients:
            self.send(
                f":minetogether-ci 352 {self.nick} {channel} MineTogether 127.0.0.1 "
                f"minetogether-ci {client.nick} H+ :0 {client.realname}"
            )
        self.send(f":minetogether-ci 315 {self.nick} {channel} :End of WHO list")

    def handle(self):
        for raw in self.rfile:
            line = raw.decode("utf-8", errors="replace").rstrip("\r\n")
            if not line:
                continue
            print(f"RECV {self.nick or '-'} {line}", flush=True)
            command, _, rest = line.partition(" ")
            command = command.upper()
            if command == "CAP":
                subcommand = rest.split()[0].upper() if rest else ""
                if subcommand == "LS":
                    self.send(f":minetogether-ci CAP {self.nick or '*'} LS :")
            elif command == "NICK":
                self.nick = rest.lstrip(":").split()[0]
                self.maybe_register()
            elif command == "USER":
                self.user_seen = True
                if " :" in rest:
                    self.realname = rest.split(" :", 1)[1]
                self.maybe_register()
            elif command == "JOIN":
                self.handle_join(rest.split()[0])
            elif command == "WHO":
                self.handle_who(rest.split()[0])
            elif command == "PRIVMSG":
                target, _, message = rest.partition(" :")
                self.server.state.broadcast(f":{self.mask} PRIVMSG {target} :{message}", exclude=self)
            elif command == "WHOIS":
                target = rest.split()[-1]
                with self.server.state.lock:
                    match = next((client for client in self.server.state.clients if client.nick == target), None)
                realname = match.realname if match else "MineTogether CI"
                self.send(f":minetogether-ci 311 {self.nick} {target} MineTogether 127.0.0.1 * :{realname}")
                self.send(f":minetogether-ci 318 {self.nick} {target} :End of WHOIS list")
            elif command == "MODE" and rest.startswith("#"):
                channel = rest.split()[0]
                self.send(f":minetogether-ci 324 {self.nick} {channel} +nt")
            elif command == "PING":
                self.send("PONG " + rest)
            elif command == "QUIT":
                break


class ThreadedIrcServer(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--channel", default="#minetogether-ci")
    args = parser.parse_args()
    server = ThreadedIrcServer((args.host, args.port), IrcHandler)
    server.state = State(args.channel)
    signal.signal(signal.SIGTERM, lambda *_: threading.Thread(target=server.shutdown, daemon=True).start())
    print(f"READY {args.host}:{args.port} {args.channel}", flush=True)
    server.serve_forever(poll_interval=0.2)
    server.server_close()


if __name__ == "__main__":
    main()
