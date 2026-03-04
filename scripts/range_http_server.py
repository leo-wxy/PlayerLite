#!/usr/bin/env python3
"""Minimal local HTTP file server with Range(206) support for media seek testing."""

import argparse
import contextlib
import os
import re
from http import HTTPStatus
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

_RANGE_RE = re.compile(r"bytes=(\d*)-(\d*)$")


class RangeRequestHandler(SimpleHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def send_head(self):
        path = self.translate_path(self.path)
        if os.path.isdir(path):
            return super().send_head()

        ctype = self.guess_type(path)
        try:
            file_obj = open(path, "rb")
        except OSError:
            self.send_error(HTTPStatus.NOT_FOUND, "File not found")
            return None

        with contextlib.ExitStack() as stack:
            stack.callback(file_obj.close)
            stat = os.fstat(file_obj.fileno())
            file_size = stat.st_size
            range_header = self.headers.get("Range")

            if not range_header:
                self.send_response(HTTPStatus.OK)
                self.send_header("Content-Type", ctype)
                self.send_header("Content-Length", str(file_size))
                self.send_header("Accept-Ranges", "bytes")
                self.send_header("Last-Modified", self.date_time_string(stat.st_mtime))
                self.end_headers()
                self._range = None
                stack.pop_all()
                return file_obj

            range_match = _RANGE_RE.fullmatch(range_header.strip())
            if not range_match:
                self.send_error(HTTPStatus.REQUESTED_RANGE_NOT_SATISFIABLE, "Invalid Range header")
                return None

            start_text, end_text = range_match.groups()
            if start_text == "" and end_text == "":
                self.send_error(HTTPStatus.REQUESTED_RANGE_NOT_SATISFIABLE, "Invalid Range values")
                return None

            if start_text == "":
                suffix_len = int(end_text)
                if suffix_len <= 0:
                    self.send_error(HTTPStatus.REQUESTED_RANGE_NOT_SATISFIABLE, "Invalid Range suffix")
                    return None
                start = max(file_size - suffix_len, 0)
                end = file_size - 1
            else:
                start = int(start_text)
                end = int(end_text) if end_text else file_size - 1

            if start < 0 or start >= file_size:
                self.send_response(HTTPStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                self.send_header("Content-Range", f"bytes */{file_size}")
                self.end_headers()
                return None

            end = min(end, file_size - 1)
            if end < start:
                self.send_response(HTTPStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                self.send_header("Content-Range", f"bytes */{file_size}")
                self.end_headers()
                return None

            content_length = end - start + 1
            self.send_response(HTTPStatus.PARTIAL_CONTENT)
            self.send_header("Content-Type", ctype)
            self.send_header("Content-Length", str(content_length))
            self.send_header("Content-Range", f"bytes {start}-{end}/{file_size}")
            self.send_header("Accept-Ranges", "bytes")
            self.send_header("Last-Modified", self.date_time_string(stat.st_mtime))
            self.end_headers()

            file_obj.seek(start)
            self._range = (start, end)
            stack.pop_all()
            return file_obj

    def copyfile(self, source, outputfile):
        range_info = getattr(self, "_range", None)
        if range_info is None:
            return super().copyfile(source, outputfile)

        start, end = range_info
        remaining = end - start + 1
        buffer_size = 64 * 1024
        while remaining > 0:
            chunk = source.read(min(buffer_size, remaining))
            if not chunk:
                break
            outputfile.write(chunk)
            remaining -= len(chunk)


def main():
    parser = argparse.ArgumentParser(description="Range-enabled local file server")
    parser.add_argument("--port", type=int, default=18080)
    parser.add_argument("--bind", default="0.0.0.0")
    parser.add_argument("--directory", default=os.getcwd())
    args = parser.parse_args()

    os.chdir(args.directory)
    server = ThreadingHTTPServer((args.bind, args.port), RangeRequestHandler)
    print(f"Serving {args.directory} on {args.bind}:{args.port} (Range enabled)")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
