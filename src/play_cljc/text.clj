(ns play-cljc.text
  (:require [clojure.java.io :as io]
            [clojure.data.codec.base64 :as base64])
  (:import [org.lwjgl.stb STBTruetype STBTTFontinfo STBTTBakedChar
                          STBImageWrite STBIWriteCallback STBIWriteCallbackI]
           [org.lwjgl BufferUtils]
           [org.lwjgl.system MemoryStack]))

(defn- resource->bytes [path]
  (with-open [out (java.io.ByteArrayOutputStream.)]
    (-> path
        io/resource
        io/input-stream
        (io/copy out))
    (.toByteArray out)))

(defn ->bitmap
  "Returns a map containing a java.nio.ByteBuffer that can store a bitmap of the given dimensions."
  [bitmap-width bitmap-height]
  {:data (BufferUtils/createByteBuffer (* bitmap-width bitmap-height))
   :width bitmap-width
   :height bitmap-height})

(def ^:private default-first-char 32)
(def ^:private default-char-buffer-size 2048)

(defn ->baked-font
  "Returns a map containing all the info needed to crop letters out of a font atlas."
  ([path font-height bitmap]
   (->baked-font path font-height bitmap default-first-char default-char-buffer-size))
  ([path font-height bitmap first-char char-buffer-size]
   (let [^bytes ttf-bytes (cond-> path
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
         {:keys [data width height]} bitmap
         bake-ret (STBTruetype/stbtt_BakeFontBitmap ttf font-height data width height first-char cdata)
         stack (MemoryStack/stackPush)
         *ascent (.callocInt stack 1)
         *descent (.callocInt stack 1)
         *line-gap (.callocInt stack 1)
         _ (STBTruetype/stbtt_GetFontVMetrics info *ascent *descent *line-gap)
         ascent (.get *ascent 0)
         descent (.get *descent 0)
         line-gap (.get *line-gap 0)
         scale (double (STBTruetype/stbtt_ScaleForPixelHeight info font-height))
         baseline (* ascent scale)
         chars (->> cdata .iterator iterator-seq
                    (mapv (fn [^STBTTBakedChar q]
                            {:x (int (.x0 q))
                             :y (int (.y0 q))
                             :w (int (- (.x1 q) (.x0 q)))
                             :h (int (- (.y1 q) (.y0 q)))
                             :xoff (double (.xoff q))
                             :yoff (double (.yoff q))
                             :xadv (double (.xadvance q))})))]
     {:baked-chars chars
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
      :first-char first-char
      :bitmap-width width
      :bitmap-height height})))

(defn bitmap->data-uri
  "Returns a string containing a data URI for the given bitmap."
  [{:keys [data width height] :as bitmap}]
  (let [image (promise)]
    (STBImageWrite/stbi_write_png_to_func (reify STBIWriteCallbackI
                                            (invoke [this context data size]
                                              (let [buf (STBIWriteCallback/getData data size)
                                                    arr (byte-array (.remaining buf))]
                                                (.get buf arr)
                                                (deliver image arr))))
                                          0 width height 1 data 0)
    (let [^bytes barray (base64/encode @image)]
      (str "data:image/png;base64," (String. barray "UTF-8")))))

