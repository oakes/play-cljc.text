(ns play-cljc.text-js
  (:require [play-cljc.text :as text]))

(defmacro ->baked-font
  "Runs play-cljc.text/->baked-font and then converts the :bitmap field into a data URI,
  so it can be used by browsers."
  ([path font-height bitmap-width bitmap-height]
   (->baked-font path font-height bitmap-width bitmap-height text/default-first-char text/default-char-buffer-size))
  ([path font-height bitmap-width bitmap-height first-char char-buffer-size]
   (text/->baked-font path font-height bitmap-width bitmap-height first-char char-buffer-size)))

