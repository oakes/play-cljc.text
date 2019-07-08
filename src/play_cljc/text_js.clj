(ns play-cljc.text-js
  (:require [play-cljc.text :as text]))

(defmacro ttf->bitmap
  "Runs play-cljc.text/ttf->bitmap and then converts the :bitmap field into a data URI,
  so it can be used by browsers."
  ([ttf-path font-height bitmap-width bitmap-height]
   (ttf->bitmap ttf-path font-height bitmap-width bitmap-height text/default-first-char text/default-char-buffer-size))
  ([ttf-path font-height bitmap-width bitmap-height first-char char-buffer-size]
   (text/ttf->bitmap ttf-path font-height bitmap-width bitmap-height first-char char-buffer-size)))

