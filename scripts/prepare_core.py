"""Reproducible, narrowly scoped embedding patches for the pinned mihomo source.

Never edit the shared Go module cache. The generated module is ignored by Git.
"""
import json
from pathlib import Path
import shutil
import subprocess
import shutil as shell

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "core" / "upstream"
VERSION = "github.com/metacubex/mihomo@v1.19.31"
GO_MOD_REPLACE = 'replace github.com/metacubex/mihomo => ./upstream'


def patch(text, before, after):
    if text.count(before) != 1:
        raise RuntimeError(f"Upstream patch context changed: {before[:100]!r}")
    return text.replace(before, after)


def ensure_import(text, line):
    if text.count(line) == 1:
        return text
    if text.count(line) > 1:
        raise RuntimeError(f"Unexpected duplicate import in upstream: {line!r}")
    start = text.index("import (")
    end = text.index(")\n", start)
    block = text[start:end]
    lines = block.splitlines()
    lines.append(line.rstrip("\n"))
    lines[1:] = sorted(lines[1:])
    head, tail = text[:start], text[end:]
    return head + "\n".join(lines) + tail


def guarded(func):
    def wrapper():
        try:
            func()
        except Exception as error:
            print(f"prepare_core failed before the Go build started: {error}")
            raise SystemExit(1) from error
    return wrapper


@guarded
def main():
    go_mod = ROOT / "core" / "go.mod"
    original = go_mod.read_text(encoding="utf-8")
    replace_line = GO_MOD_REPLACE + "\n"
    if replace_line not in original:
        raise RuntimeError("core/go.mod is missing the upstream replace; refusing to build a stock mihomo")
    if source_override := __import__("os").environ.get("MIHOMO_SOURCE"):
        source = Path(source_override)
    else:
        go_mod.write_text(original.replace(replace_line, ""), encoding="utf-8")
        try:
            resolved = json.loads(subprocess.check_output(
                ["go", "mod", "download", "-json", VERSION], cwd=ROOT / "core"))
        finally:
            go_mod.write_text(original, encoding="utf-8")
        if "Dir" not in resolved:
            raise RuntimeError(resolved)
        source = Path(resolved["Dir"])
    # copy2 would retain the module cache's read-only permission bits.
    shutil.copytree(source, TARGET, dirs_exist_ok=True, copy_function=shutil.copyfile)

    path = TARGET / "component/http/http.go"
    text = path.read_text(encoding="utf-8")
    if "requireHTTPS" not in text:
        text = patch(text, '\t"context"', '\t"context"\n\t"fmt"')
        text = patch(text, '\treq, err := http.NewRequest(method, urlRes.String(), body)',
                     '\tif err := requireHTTPS(urlRes); err != nil { return nil, err }\n\treq, err := http.NewRequest(method, urlRes.String(), body)')
        text = patch(text, '\tclient := http.Client{Transport: transport}',
                     '\tclient := http.Client{Transport: transport, CheckRedirect: secureRedirect}')
        text += '''
// Applies to metadata downloads, not user traffic through the proxy tunnel.
func requireHTTPS(url *URL.URL) error {
    if url == nil || url.Scheme != "https" || url.Hostname() == "" {
        return fmt.Errorf("metadata download requires HTTPS")
    }
    return nil
}

func secureRedirect(req *http.Request, via []*http.Request) error {
    if len(via) >= 10 { return fmt.Errorf("too many metadata redirects") }
    return requireHTTPS(req.URL)
}
'''
        path.write_text(text, encoding="utf-8")

    path = TARGET / "hub/route/server.go"
    text = path.read_text(encoding="utf-8")
    if "serverLifecycle" not in text:
        for needed in ('\t"net"\n', '\t"sync"\n'):
            text = ensure_import(text, needed)
        text = patch(text, 'func ReCreateServer(cfg *Config) {\n\tgo start(cfg)\n\tgo startTLS(cfg)\n\tgo startUnix(cfg)\n\tif inbound.SupportNamedPipe {\n\t\tgo startPipe(cfg)\n\t}\n}', '''var serverLifecycle sync.Mutex
var boundListeners []net.Listener

func ReCreateServer(cfg *Config) {
    serverLifecycle.Lock()
    defer serverLifecycle.Unlock()
    for _, listener := range boundListeners { _ = listener.Close() }
    boundListeners = nil
    start(cfg)
    startTLS(cfg)
    startUnix(cfg)
    if inbound.SupportNamedPipe { startPipe(cfg) }
}

func CloseServers() {
    serverLifecycle.Lock()
    defer serverLifecycle.Unlock()
    for _, server := range []*http.Server{httpServer, tlsServer, unixServer, pipeServer} {
        if server != nil { _ = server.Close() }
    }
    for _, listener := range boundListeners { _ = listener.Close() }
    boundListeners = nil
    httpServer, tlsServer, unixServer, pipeServer = nil, nil, nil, nil
}''')
        for call, label in [('l', ''), ('tls.NewListener(l, tlsConfig)', ' tls'), ('l', ' unix'), ('l', ' pipe')]:
            before = f'\t\tif err = server.Serve({call}); err != nil {{\n\t\t\tlog.Errorln("External controller{label} serve error: %s", err)\n\t\t}}'
            after = f'\t\tgo func() {{\n\t\t\tif err := server.Serve({call}); err != nil {{\n\t\t\t\tlog.Errorln("External controller{label} serve error: %s", err)\n\t\t\t}}\n\t\t}}()'
            text = patch(text, before, after)
        for field in ("httpServer", "tlsServer", "unixServer", "pipeServer"):
            text = patch(text, f"\t\t{field} = server", f"\t\t{field} = server\n\t\tboundListeners = append(boundListeners, l)")
        path.write_text(text, encoding="utf-8")

    if not (TARGET / "hub/route/server.go").read_text(encoding="utf-8").count("func CloseServers"):
        raise RuntimeError("CloseServers missing from the generated upstream; zephyr.go would orphan the controller")

    if shell.which("gofmt"):
        subprocess.run(["gofmt", "-w", str(TARGET / "component/http"), str(TARGET / "hub/route/server.go")], check=True)

    generated = (TARGET / "hub/route/server.go").read_text(encoding="utf-8") + (TARGET / "component/http/http.go").read_text(encoding="utf-8")
    for needle in ("func CloseServers", "func ReCreateServer", "boundListeners", "secureRedirect"):
        assert needle in generated, f"embed patch missing from generated upstream: {needle}"

    print("Prepared mihomo v1.19.31 with HTTPS metadata and synchronous controller lifecycle")


if __name__ == "__main__":
    main()

