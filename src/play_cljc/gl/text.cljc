(ns play-cljc.gl.text
  (:require [play-cljc.gl.core :as c]
            [play-cljc.gl.entities-2d :as e]
            [play-cljc.transforms :as t]
            [play-cljc.primitives-2d :as primitives]
            [play-cljc.math :as m]
            #?(:clj  [play-cljc.macros-java :refer [gl]]
               :cljs [play-cljc.macros-js :refer-macros [gl]])))

(def ^:private flip-y-matrix
  [1  0  0
   0 -1  0
   0  0  1])

(def ^:private font-vertex-shader
  {:inputs
   '{a_position vec2}
   :uniforms
   '{u_matrix mat3
     u_textureMatrix mat3}
   :outputs
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
   :inputs
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

(defn ->font-entity [game data width height]
  (-> (e/->image-entity game data width height)
      (assoc :vertex font-vertex-shader
             :fragment font-fragment-shader)
      #?(:clj (assoc-in [:uniforms 'u_image :opts]
                        {:mip-level 0
                         :internal-fmt (gl game RED)
                         :width width
                         :height height
                         :border 0
                         :src-fmt (gl game RED)
                         :src-type (gl game UNSIGNED_BYTE)}))))

(defn ->text-entity [game
                     {:keys [baked-chars baseline
                             font-height first-char
                             bitmap-width bitmap-height]
                      :as baked-font}
                     font-entity
                     text]
  (loop [text (seq text)
         total 0
         inner-entities []]
    (if-let [ch (first text)]
      (let [{:keys [x y w h xoff yoff xadv]} (nth baked-chars (- #?(:clj (int ch) :cljs (.charCodeAt ch 0)) first-char))]
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

