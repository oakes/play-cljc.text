(ns play-cljc.text
  (:require [clojure.java.io :as io])
  (:import [org.lwjgl.stb STBTruetype STBTTFontinfo STBTTBakedChar]
           [org.lwjgl BufferUtils]
           [org.lwjgl.system MemoryStack]))

(defrecord BakedChar [x y w h xoff yoff xadv])
(defrecord BakedFont [bitmap baked-chars
                      ascent descent line-gap scale baseline
                      first-unused-row chars-that-fit
                      font-height bitmap-width bitmap-height first-char])

(defn- resource->bytes [path]
  (with-open [out (java.io.ByteArrayOutputStream.)]
    (-> path
        io/resource
        io/input-stream
        (io/copy out))
    (.toByteArray out)))

(def default-first-char 32)
(def default-char-buffer-size 2048)

(defn ->baked-font
  ([path font-height bitmap-width bitmap-height]
   (->baked-font path font-height bitmap-width bitmap-height default-first-char default-char-buffer-size))
  ([path font-height bitmap-width bitmap-height first-char char-buffer-size]
   (let [ttf-bytes (cond-> path
                           (string? path)
                           resource->bytes)
         ttf (doto (java.nio.ByteBuffer/allocateDirect (alength ttf-bytes))
               (.order (java.nio.ByteOrder/nativeOrder))
               (.put ttf-bytes)
               .flip)
         info (STBTTFontinfo/create)
         _ (or (STBTruetype/stbtt_InitFont info ttf)
               (throw (IllegalStateException. "Failed to initialize font information.")))
         cdata (STBTTBakedChar/malloc char-buffer-size)
         bitmap (BufferUtils/createByteBuffer (* bitmap-width bitmap-height))
         bake-ret (STBTruetype/stbtt_BakeFontBitmap ttf font-height bitmap bitmap-width bitmap-height first-char cdata)
         stack (MemoryStack/stackPush)
         *ascent (.callocInt stack 1)
         *descent (.callocInt stack 1)
         *line-gap (.callocInt stack 1)
         _ (STBTruetype/stbtt_GetFontVMetrics info *ascent *descent *line-gap)
         ascent (.get *ascent 0)
         descent (.get *descent 0)
         line-gap (.get *line-gap 0)
         scale (STBTruetype/stbtt_ScaleForPixelHeight info font-height)
         baseline (* ascent scale)
         chars (->> cdata .iterator iterator-seq
                    (mapv (fn [q]
                            (map->BakedChar
                              {:x (.x0 q)
                               :y (.y0 q)
                               :w (- (.x1 q) (.x0 q))
                               :h (- (.y1 q) (.y0 q))
                               :xoff (.xoff q)
                               :yoff (.yoff q)
                               :xadv (.xadvance q)}))))]
     (map->BakedFont {:bitmap bitmap
                      :baked-chars chars
                      :ascent ascent
                      :descent descent
                      :line-gap line-gap
                      :scale scale
                      :baseline baseline
                      :first-unused-row (when (pos? bake-ret)
                                          bake-ret)
                      :chars-that-fit (when (neg? bake-ret)
                                        (* -1 bake-ret))
                      :font-height font-height
                      :bitmap-width bitmap-width
                      :bitmap-height bitmap-height
                      :first-char first-char}))))

