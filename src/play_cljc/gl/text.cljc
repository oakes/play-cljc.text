(ns play-cljc.gl.text
  (:require [play-cljc.gl.core :as c]
            [play-cljc.gl.entities-2d :as e]
            [play-cljc.transforms :as t]
            [play-cljc.primitives-2d :as primitives]
            [play-cljc.math :as m]
            #?(:clj  [play-cljc.macros-java :refer [gl]]
               :cljs [play-cljc.macros-js :refer-macros [gl]]))
  (:import [org.lwjgl.stb STBTruetype STBTTFontinfo STBTTBakedChar]
           [org.lwjgl BufferUtils]
           [org.lwjgl.system MemoryStack]))

(def ^:private flip-y-matrix
  [1  0  0
   0 -1  0
   0  0  1])

(def ^:private font-vertex-shader
  {:attributes
   '{a_position vec2}
   :uniforms
   '{u_matrix mat3
     u_textureMatrix mat3}
   :varyings
   '{v_texCoord vec2}
   :signatures
   '{main ([] void)}
   :functions
   '{main ([]
           (= gl_Position
              (vec4
                (.xy (* u_matrix (vec3 a_position 1)))
                0 1))
           (= v_texCoord (.xy (* u_textureMatrix (vec3 a_position 1)))))}})

(def ^:private font-fragment-shader
  {:precision "mediump float"
   :uniforms
   '{u_image sampler2D}
   :varyings
   '{v_texCoord vec2}
   :outputs
   '{outColor vec4}
   :signatures
   '{main ([] void)}
   :functions
   '{main ([]
           (= outColor (texture u_image v_texCoord))
           ("if" (== (.rgb outColor) (vec3 "0.0" "0.0" "0.0"))
             "discard")
           ("else"
             (= outColor (vec4 "0.0" "0.0" "0.0" "1.0"))))}})

(defn ->font-entity [game {:keys [bitmap bitmap-width bitmap-height] :as baked-font}]
  (->> {:vertex font-vertex-shader
        :fragment font-fragment-shader
        :attributes {'a_position {:data primitives/rect
                                  :type (gl game FLOAT)
                                  :size 2}}
        :uniforms {'u_image {:data bitmap
                             :opts {:mip-level 0
                                    :internal-fmt (gl game RED)
                                    :width bitmap-width
                                    :height bitmap-height
                                    :border 0
                                    :src-fmt (gl game RED)
                                    :src-type (gl game UNSIGNED_BYTE)}
                             :params {(gl game TEXTURE_MAG_FILTER)
                                      (gl game LINEAR)
                                      (gl game TEXTURE_MIN_FILTER)
                                      (gl game LINEAR)}}
                   'u_textureMatrix (m/identity-matrix 3)}
        :width bitmap-width
        :height bitmap-height
        :baked-font baked-font}
       e/map->TwoDEntity))

(defn ->text-entity [game
                     {{:keys [baked-chars baseline
                              font-height bitmap-width
                              bitmap-height first-char]} :baked-font
                      :as font-entity}
                     text]
  (loop [text (seq text)
         total 0
         inner-entities []]
    (if-let [ch (first text)]
      (let [{:keys [x y w h xoff yoff xadv]} (nth baked-chars (- (int ch) first-char))]
        (recur (rest text)
               (+ total xadv)
               (conj inner-entities
                     (-> font-entity
                         (t/project bitmap-width bitmap-height)
                         (t/crop x y w h)
                         (t/translate (+ total xoff) (- font-height baseline yoff))
                         (t/scale w h)
                         (update-in [:uniforms 'u_matrix]
                                    #(m/multiply-matrices 3 flip-y-matrix %))))))
      (-> (e/->image-entity game nil total font-height)
          (assoc
            :width total
            :height font-height
            :render-to-texture {'u_image (mapv #(assoc % :viewport {:x 0
                                                                    :y (- font-height bitmap-height)
                                                                    :width bitmap-width
                                                                    :height bitmap-height})
                                               inner-entities)})))))

