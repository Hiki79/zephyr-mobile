//go:build tools

// gomobile generates binding code that imports golang.org/x/mobile/bind, so the
// dependency has to stay in go.mod. Nothing in this file is compiled into the
// app; it exists only to keep `go mod tidy` from dropping the requirement.
package zephyrcore

import _ "golang.org/x/mobile/bind"
