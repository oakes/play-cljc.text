[![Clojars Project](https://img.shields.io/clojars/v/play-cljc.text.svg)](https://clojars.org/play-cljc.text)

## Introduction

A library for rendering text in OpenGL or WebGL with [play-cljc](https://github.com/oakes/play-cljc).

## Usage

You can include this library in your project dependencies using the version number in the badge above. See [the dynadocs](https://oakes.github.io/play-cljc.text/) to learn how to use it.

## Development

* Install [the Clojure CLI tool](https://clojure.org/guides/getting_started#_clojure_installer_and_cli_tools)
* To develop the web version with figwheel: `clj -A:dev dev.clj`
* To develop the native version: `clj -A:dev dev.clj native`
  * On Mac OS, you will need to run `clj -A:dev -J-XstartOnFirstThread dev.clj native`
* To install the release version: `clj -A:prod prod.clj install`

## Licensing

All files that originate from this project are dedicated to the public domain. I would love pull requests, and will assume that they are also dedicated to the public domain.
