## Introduction

A library for rendering text in OpenGL or WebGL with [play-cljc](https://github.com/oakes/play-cljc).

### [Try the interactive docs!](https://oakes.github.io/play-cljc/cljs/play-cljc.gl.text.html)

There is also an [example project](https://github.com/oakes/play-cljc-examples/tree/master/ui-gallery) showing how to use it in more detail.

## Usage

If you're using the latest version of play-cljc, it brings this library in automatically.

## Development

* Install [the Clojure CLI tool](https://clojure.org/guides/getting_started#_clojure_installer_and_cli_tools)
* To develop the native version on each OS:
  * `clj -A:dev:linux native`
  * `clj -A:dev:macos native`
  * `clj -A:dev:windows native`
* To install the release version: `clj -A:prod install`

## Licensing

All files that originate from this project are dedicated to the public domain. I would love pull requests, and will assume that they are also dedicated to the public domain.
